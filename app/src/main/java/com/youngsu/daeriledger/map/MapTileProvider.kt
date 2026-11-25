package com.youngsu.daeriledger.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 실제 도로·지명이 그려진 OpenStreetMap 타일(z/x/y.png)을 받아 캐시하는 엔진.
 *
 * · API 키가 필요 없다 — OSM 표준 타일 서버만 쓴다(지도 SDK 추가 없음).
 * · 네트워크: HttpURLConnection (외부 라이브러리 없음)
 * · 메모리: LruCache / 저장소: cacheDir/spot-tiles/v1/z/x_y.png (앱을 껐다 켜도 다시 받지 않음)
 * · 서버가 막히면 다음 미러로 넘어가고, 모두 실패하면 부른 화면이 격자 바탕만 그린다.
 *
 * OSM 타일 이용 정책상 앱을 밝히는 User-Agent 를 반드시 붙인다.
 */
class MapTileProvider private constructor(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "spot-map-tile").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    private val mem = object : LruCache<String, Bitmap>(memoryBudget()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val lock = Any()
    private val pending = HashSet<String>()
    private val failedAt = HashMap<String, Long>()
    private val diskDir = File(context.cacheDir, "spot-tiles/v1")

    private var downloads = 0

    /** 메모리 캐시에 있으면 즉시 돌려주고, 없으면 내려받기를 예약한다(끝나면 notify 호출). */
    fun tile(z: Int, x: Int, y: Int, notify: () -> Unit): Bitmap? {
        val key = key(z, x, y)
        mem.get(key)?.let { return it }

        synchronized(lock) {
            if (key in pending) return null
            val failed = failedAt[key]
            if (failed != null && System.currentTimeMillis() - failed < RETRY_MS) return null
            pending.add(key)
        }

        pool.execute {
            val bmp = readDisk(key) ?: download(z, x, y)
            synchronized(lock) {
                pending.remove(key)
                if (bmp == null) failedAt[key] = System.currentTimeMillis() else failedAt.remove(key)
            }
            if (bmp != null) {
                mem.put(key, bmp)
                main.post { notify() }
                if (++downloads % 60 == 0) pruneDiskAsync()
            }
        }
        return null
    }

    // ------------------------------------------------------------------ files

    private fun key(z: Int, x: Int, y: Int) = "$z/$x/$y"

    private fun fileOf(key: String): File {
        val parts = key.split('/')
        return File(File(diskDir, parts[0]), "${parts[1]}_${parts[2]}.png")
    }

    private fun readDisk(key: String): Bitmap? {
        val f = fileOf(key)
        if (!f.isFile || f.length() < 64L) return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    private fun saveDisk(key: String, bytes: ByteArray) {
        runCatching {
            val f = fileOf(key)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
            f.setLastModified(System.currentTimeMillis())
        }
    }

    // --------------------------------------------------------------- network

    private fun download(z: Int, x: Int, y: Int): Bitmap? {
        for (base in PROVIDERS) {
            val bytes = http(base, z, x, y) ?: continue
            val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            if (bmp != null && bmp.width >= 64) {
                saveDisk(key(z, x, y), bytes)
                return bmp
            }
        }
        return null
    }

    private fun http(base: String, z: Int, x: Int, y: Int): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$base/$z/$x/$y.png").openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/png,image/*;q=0.8")
                setRequestProperty("Accept-Encoding", "identity")
            }
            if (conn.responseCode != 200) return null
            val type = conn.contentType ?: ""
            if (!type.contains("image", ignoreCase = true)) return null
            conn.inputStream.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    // ------------------------------------------------------------ housekeeping

    private fun pruneDiskAsync() {
        pool.execute {
            runCatching {
                val files = diskDir.walkTopDown().filter { it.isFile }.toMutableList()
                var total = files.sumOf { it.length() }
                if (total <= DISK_BUDGET) return@runCatching
                files.sortBy { it.lastModified() }
                for (f in files) {
                    if (total <= DISK_BUDGET * 3 / 4) break
                    total -= f.length()
                    f.delete()
                }
            }
        }
    }

    private fun memoryBudget(): Int {
        val max = Runtime.getRuntime().maxMemory() / 8
        return max.coerceIn(8L * 1024 * 1024, 32L * 1024 * 1024).toInt()
    }

    companion object {
        /** OSM 타일 정책이 요구하는 앱 식별자 */
        private const val USER_AGENT = "DaeriLedger/1.0 (Android; com.youngsu.daeriledger)"

        private const val RETRY_MS = 60_000L
        private const val DISK_BUDGET = 32L * 1024 * 1024

        /** OSM 표준 타일 미러 — 앞에서부터 시도하고, 실패하면 다음으로 */
        private val PROVIDERS = listOf(
            "https://tile.openstreetmap.org",
            "https://tile.openstreetmap.de",
            "https://a.tile.openstreetmap.fr/osmfr"
        )

        @Volatile private var instance: MapTileProvider? = null

        fun get(context: Context): MapTileProvider {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val made = MapTileProvider(context.applicationContext)
                instance = made
                return made
            }
        }
    }
}
