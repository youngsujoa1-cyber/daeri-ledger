package com.youngsu.daeriledger.location

import com.youngsu.daeriledger.BuildConfig
import com.youngsu.daeriledger.data.KakaoReverse
import java.net.HttpURLConnection
import java.net.URL

/**
 * 좌표 → 동·리 이름을 카카오에 물어본다 (v1.0.28).
 *
 * 한국 Android `Geocoder` 는 도로명·시 단위까지만 주는 일이 많아 출발지가 `성남시` 로 떨어졌다.
 * 카카오 `coord2address` 는 법정동·리(`정자동`, `백암면 박곡리`, `종로1가`)를 돌려주므로 먼저 쓴다.
 *
 *  - `local.properties` 의 `KAKAO_REST_KEY` 가 있으면 그 키로(정식 REST 인증),
 *  - 없으면 앱이 이미 쓰는 `KAKAO_JS_KEY` + 등록 도메인 `KA` 헤더로 부른다.
 *  - 둘 다 없거나 네트워크·인증 실패면 null — 부른 쪽이 Android `Geocoder` 로 폴백한다.
 *
 * **메인 스레드에서 부르면 안 된다** (네트워크). `Thread` 안에서 호출한다.
 */
object KakaoGeocoder {

    /** 카카오가 답하지 않을 때 기다리는 시간 — 현위치 버튼이 오래 잠기지 않게 짧게 */
    private const val TIMEOUT_MS = 4_000

    fun lookup(lat: Double, lng: Double, timeoutMs: Int = TIMEOUT_MS): String? {
        val restKey = BuildConfig.KAKAO_REST_KEY.trim()
        val jsKey = BuildConfig.KAKAO_JS_KEY.trim()
        if (restKey.isEmpty() && jsKey.isEmpty()) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(KakaoReverse.url(lat, lng)).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                // gzip 본문은 기본 자바가 풀지 않는다 — 평문으로 받는다
                setRequestProperty("Accept-Encoding", "identity")
                if (restKey.isNotEmpty()) {
                    setRequestProperty("Authorization", "KakaoAK $restKey")
                } else {
                    setRequestProperty("Authorization", "KakaoAK $jsKey")
                    setRequestProperty("KA", KakaoReverse.kaHeader())
                    setRequestProperty("Referer", KakaoReverse.REFERER)
                }
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                KakaoReverse.nameFromJson(body)
            }
        } catch (_: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
