package com.youngsu.daeriledger.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 기사님들이 같이 만드는 공용 콜밭 (Firebase Firestore REST).
 *
 * - 외부 SDK 없이 `HttpURLConnection` 으로 Firestore REST API 만 부른다(오프라인 Gradle 환경 대응).
 * - 제보 1건 = `spot_reports` 문서 1건. 서버는 읽기만 열려 있고 수정·삭제는 규칙으로 막혀 있다.
 * - **5명 기준**: 같은 자리(반경 150m)에 서로 다른 기기 5명 이상이 `콜밭` 제보하면 공용 콜밭으로 노출하고,
 *   같은 자리에 5명 이상이 `삭제`(없어진 곳) 신고하면 모든 기사님 화면에서 숨긴다.
 *   Spark 무료 플랜에는 Cloud Functions 가 없어 집계는 앱에서 한다([SpotAggregator]).
 *
 * 서버 프로젝트·키는 Firebase 콘솔 > 프로젝트 설정에서 확인할 수 있다.
 */
object SpotCloud {

    // ---------------------------------------------------------------- 서버 설정
    private const val PROJECT_ID = "daeri-ledger-spots"
    private const val API_KEY = "AIzaSyCuWTIVmoMKtOGEoZMx7oPRocrMom6DKiA"
    private const val COLLECTION = "spot_reports"
    private const val BASE =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents"

    // ---------------------------------------------------------------- 정책 값
    /** 한 번에 받아 오는 최근 제보 수 (무료 한도: 읽기 5만/일 · 등록·삭제 제보가 섞여 온다) */
    private const val FETCH_LIMIT = 500
    /** 이 기간 안의 제보만 집계에 쓴다 (일) */
    private const val RECENT_DAYS = 30
    private const val TIMEOUT_MS = 8_000
    /** 같은 결과를 다시 받지 않는 시간 (ms) */
    private const val CACHE_MS = 5 * 60 * 1000L

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var cache: SpotPublicState = SpotPublicState()
    @Volatile private var cacheAt = 0L
    @Volatile private var lastError: String? = null

    /** 마지막 실패 사유 (화면에 안내 문구로 쓸 수 있다). 성공하면 null. */
    val errorMessage: String? get() = lastError

    // ---------------------------------------------------------------- 제보 보내기

    /**
     * 제보를 서버에 올린다. 네트워크·JSON 작업이라 워커 스레드에서 돈다.
     * 결과 콜백은 메인 스레드에서 온다.
     *
     * @param kind [SpotAggregator.KIND_SPOT] `콜밭`(등록) 또는 [SpotAggregator.KIND_REMOVED] `삭제`(없어진 곳)
     */
    fun submit(
        context: Context,
        name: String,
        lat: Double,
        lng: Double,
        address: String = "",
        note: String = "",
        kind: String = SpotAggregator.KIND_SPOT,
        onDone: (Boolean, String) -> Unit
    ) {
        val app = context.applicationContext
        Thread {
            var ok = false
            var msg: String
            try {
                val body = JSONObject().apply {
                    put("fields", JSONObject().apply {
                        put("name", str(trimTo(name, 60)))
                        put("kind", str(trimTo(kind, 20)))
                        put("lat", num(lat))
                        put("lng", num(lng))
                        put("address", str(trimTo(address, 200)))
                        put("note", str(trimTo(note, 300)))
                        put("deviceHash", str(deviceHash(app)))
                        put("createdAt", ts(System.currentTimeMillis()))
                    })
                }
                val code = request("$BASE/$COLLECTION?key=$API_KEY", "POST", body.toString())
                when {
                    code in 200..299 -> {
                        ok = true
                        msg = if (kind == SpotAggregator.KIND_REMOVED) {
                            "없어진 곳으로 신고했어요. 5명 이상 모이면 모든 기사님 화면에서 숨겨져요."
                        } else {
                            "제보를 올렸어요. 같은 자리에서 5명 이상 모이면 모든 기사님께 공용 콜밭으로 보여요."
                        }
                        cacheAt = 0L                     // 다음 조회 때 새로 받는다
                    }
                    code == 403 -> msg = "제보 형식이 서버 규칙에 맞지 않아 거절됐어요."
                    else -> msg = "제보를 못 올렸어요 (오류 $code). 잠시 뒤 다시 시도해 주세요."
                }
            } catch (e: Exception) {
                msg = "인터넷 연결을 확인해 주세요. (${e.javaClass.simpleName})"
            }
            main.post { onDone(ok, msg) }
        }.start()
    }

    /** 없어진 곳 신고 — 같은 스키마에 `kind = "삭제"` 로 보낸다 (보안 규칙 수정 불필요) */
    fun reportRemoved(
        context: Context,
        name: String,
        lat: Double,
        lng: Double,
        address: String = "",
        onDone: (Boolean, String) -> Unit
    ) = submit(
        context = context,
        name = name,
        lat = lat,
        lng = lng,
        address = address,
        kind = SpotAggregator.KIND_REMOVED,
        onDone = onDone
    )

    // ---------------------------------------------------------------- 공용 콜밭 받기

    /**
     * 최근 제보를 모아 "서로 다른 기기 5명 이상이 제보한 자리"만 공용 콜밭으로,
     * "5명 이상이 없어졌다고 신고한 자리"는 숨길 자리로 돌려준다.
     * 5분 캐시를 쓰므로 화면을 자주 열어도 서버를 계속 부르지 않는다.
     * 콜백은 메인 스레드에서 온다.
     */
    fun publicState(context: Context, force: Boolean = false, onDone: (SpotPublicState) -> Unit) {
        if (!force && cacheAt != 0L && System.currentTimeMillis() - cacheAt < CACHE_MS) {
            val cached = cache
            main.post { onDone(cached) }
            return
        }
        Thread {
            var result: SpotPublicState = cache
            try {
                result = SpotAggregator.aggregate(fetchRecent())
                cache = result
                cacheAt = System.currentTimeMillis()
                lastError = null
            } catch (e: Exception) {
                lastError = "공용 콜밭을 못 불러왔어요 (${e.javaClass.simpleName})"
            }
            main.post { onDone(result) }
        }.start()
    }

    /** 캐시만 비운다 (제보 직후·수동 새로고침) */
    fun invalidate() {
        cacheAt = 0L
    }

    private fun fetchRecent(): List<SpotReport> {
        val since = iso(System.currentTimeMillis() - RECENT_DAYS * 24L * 60L * 60L * 1000L)
        val query = JSONObject().apply {
            put("structuredQuery", JSONObject().apply {
                put("from", JSONArray().put(JSONObject().put("collectionId", COLLECTION)))
                put("where", JSONObject().put("fieldFilter", JSONObject().apply {
                    put("field", JSONObject().put("fieldPath", "createdAt"))
                    put("op", "GREATER_THAN")
                    put("value", JSONObject().put("timestampValue", since))
                }))
                put("orderBy", JSONArray().put(JSONObject().apply {
                    put("field", JSONObject().put("fieldPath", "createdAt"))
                    put("direction", "DESCENDING")
                }))
                put("limit", FETCH_LIMIT)
            })
        }
        val text = requestForText("$BASE:runQuery?key=$API_KEY", "POST", query.toString())
        val out = ArrayList<SpotReport>()
        val rows = JSONArray(text)
        for (i in 0 until rows.length()) {
            val doc = rows.optJSONObject(i)?.optJSONObject("document") ?: continue
            val f = doc.optJSONObject("fields") ?: continue
            val lat = f.optJSONObject("lat")?.optDouble("doubleValue", Double.NaN) ?: Double.NaN
            val lng = f.optJSONObject("lng")?.optDouble("doubleValue", Double.NaN) ?: Double.NaN
            if (lat.isNaN() || lng.isNaN()) continue
            out.add(
                SpotReport(
                    name = f.optJSONObject("name")?.optString("stringValue").orEmpty(),
                    lat = lat,
                    lng = lng,
                    address = f.optJSONObject("address")?.optString("stringValue").orEmpty(),
                    note = f.optJSONObject("note")?.optString("stringValue").orEmpty(),
                    deviceHash = f.optJSONObject("deviceHash")?.optString("stringValue").orEmpty(),
                    createdAt = parseIso(f.optJSONObject("createdAt")?.optString("timestampValue")),
                    kind = f.optJSONObject("kind")?.optString("stringValue").orEmpty()
                        .ifBlank { SpotAggregator.KIND_SPOT }
                )
            )
        }
        return out
    }

    // ---------------------------------------------------------------- 기기 식별

    /**
     * 같은 사람이 여러 번 눌러도 1명으로 세기 위한 기기 해시.
     * ANDROID_ID 를 SHA-256 으로 돌린 값이라 개인정보가 아니고, 앱을 지우면 값이 바뀐다.
     * 서버 규칙이 deviceHash 를 8~64자 문자열로만 받으므로 32자로 잘라 쓴다.
     */
    fun deviceHash(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty().ifBlank { "no-android-id" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("daeri-ledger|$androidId").toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder(32)
        for (b in digest) sb.append(String.format("%02x", b))
        return sb.substring(0, 32)
    }

    // ---------------------------------------------------------------- HTTP / JSON 도우미

    private fun request(url: String, method: String, body: String): Int {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { os: OutputStream -> os.write(bytes) }
            val code = conn.responseCode
            runCatching {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.use { it.readBytes() }
            }
            return code
        } finally {
            conn.disconnect()
        }
    }

    private fun requestForText(url: String, method: String, body: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { os -> os.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { r -> r.readText() }
            }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun str(value: String) = JSONObject().put("stringValue", value)
    private fun num(value: Double) = JSONObject().put("doubleValue", value)
    private fun ts(millis: Long) = JSONObject().put("timestampValue", iso(millis))

    private fun trimTo(value: String, max: Int): String {
        val v = value.trim()
        return if (v.length <= max) v else v.substring(0, max)
    }

    private fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))

    private fun parseIso(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        // Firestore 는 소수점 이하 초를 붙여 준다: 2026-11-25T04:01:10.260314Z
        val core = text.substringBefore('.').removeSuffix("Z")
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(core)?.time ?: 0L
        }.getOrDefault(0L)
    }
}
