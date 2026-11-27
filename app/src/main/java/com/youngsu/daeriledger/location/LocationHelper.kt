package com.youngsu.daeriledger.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * 위치 한 번/계속 받기 — **융합·GPS·네트워크를 함께** 쓴다 (v1.0.31).
 *
 * v1.0.30 까지는 ① `request()` 가 기본값(-1)일 때 `lastKnown()` 의 **오래된 좌표(예: 정자동)를 나이 제한 없이**
 * 그대로 돌려주고 ② `NETWORK` 또는 `GPS` **한 제공자에만** 요청해 실내·이동 중에는 갱신이 끊겼다.
 * 그래서 사용자가 동탄에 있는데도 콜밭 화면이 정자동을 보여 주고, `새로고침`(📍)을 눌러야만 바뀌었다.
 *
 * 지금은
 *  - 캐시는 [LocationPolicy.DEFAULT_MAX_CACHE_AGE_MS](20초) 안일 때만 바로 쓰고, 아니면 **실제 측정**을 한다.
 *  - 측정은 켜져 있는 제공자(GPS·네트워크·융합)에 **모두** 건다 — 하나가 안 오면 다른 하나가 온다.
 *  - [Watcher] 는 화면이 켜져 있는 동안 연속 갱신을 받고, [LocationPolicy.WATCHDOG_SILENCE_MS](20초) 넘게
 *    새 측정이 없으면 [LocationPolicy.WATCHDOG_INTERVAL_MS](15초)마다 `getCurrentLocation` 으로 강제로 다시 잰다.
 *
 * **백그라운드 위치 권한·포그라운드 서비스는 쓰지 않는다** (`ACCESS_BACKGROUND_LOCATION`,
 * `FOREGROUND_SERVICE_LOCATION` 없음 — Play 스토어 배경 위치 심사 대상이 아니다). 화면이 꺼지면 갱신을 멈추고,
 * 화면이 다시 켜지거나 앱으로 돌아오는 순간([Watcher] 재시작) 새 좌표를 바로 잡는다.
 */
object LocationHelper {

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /** 권한이 없거나 위치를 못 얻으면 null — 콜밭 화면은 지역 필터/기본 순서로 동작한다. */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        providers.forEach { provider ->
            val loc = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (loc != null && (best == null || loc.time > best!!.time)) best = loc
        }
        return best
    }

    /**
     * 위치를 **한 번** 받는다. `onResult` 는 메인 스레드에서 정확히 한 번 불린다.
     *
     * @param maxCacheAgeMs 캐시 좌표를 믿어도 되는 나이.
     *   기본 [LocationPolicy.DEFAULT_MAX_CACHE_AGE_MS] 20초 — 예전처럼 오래된 좌표(다른 동네)를 쓰지 않는다.
     *   `0` 이면 캐시를 아예 믿지 않고 **지금 자리를 새로 잰다**(`새로고침`).
     * @param allowStaleFallback 새 측정이 [LocationPolicy.FIX_TIMEOUT_MS] 안에 안 오면 오래된 캐시라도 돌려줄지.
     *   기본 false — `콜밭 추가`·운행 저장처럼 좌표가 서버로 가는 곳은 다른 동네 좌표를 쓰면 안 된다.
     */
    @SuppressLint("MissingPermission")
    fun request(
        context: Context,
        maxCacheAgeMs: Long = LocationPolicy.DEFAULT_MAX_CACHE_AGE_MS,
        allowStaleFallback: Boolean = false,
        onResult: (Location?) -> Unit
    ) {
        if (!hasPermission(context)) {
            onResult(null)
            return
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            onResult(null)
            return
        }
        val now = System.currentTimeMillis()
        val cached = lastKnown(context)
        if (cached != null && LocationPolicy.isFresh(cached.time, now, maxCacheAgeMs)) {
            onResult(cached)
            return
        }
        SingleShot(manager, cached, allowStaleFallback, maxCacheAgeMs <= 0L, onResult).start()
    }

    /** 캐시를 무시하고 **지금 자리**를 강제로 잰다 — `새로고침`·`내 위치` 버튼 */
    fun requestFresh(
        context: Context,
        onResult: (Location?) -> Unit
    ) = request(context, maxCacheAgeMs = 0L, allowStaleFallback = false, onResult = onResult)

    /**
     * 화면이 보이는 동안 계속 위치를 따라가는 감시자.
     * 권한이 없거나 위치 서비스가 꺼져 있으면 null — 부른 쪽은 그대로 지역별 보기를 쓴다.
     * 화면이 가려지면([Watcher.stop]) 반드시 멈춘다 — 배터리와 Play 정책(포그라운드에서만) 때문이다.
     */
    fun startWatch(context: Context, onFix: (Location) -> Unit): Watcher? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return Watcher(manager, onFix).also { it.start() }
    }

    /** 켜져 있는 제공자 — GPS·네트워크·융합(12+)을 **모두** 돌려준다 */
    internal fun enabledProviders(manager: LocationManager): List<String> {
        fun on(name: String): Boolean = runCatching { manager.isProviderEnabled(name) }.getOrDefault(false)
        val fusedAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            on(LocationManager.FUSED_PROVIDER)
        return LocationPolicy.providers(
            gps = on(LocationManager.GPS_PROVIDER),
            network = on(LocationManager.NETWORK_PROVIDER),
            fused = fusedAvailable
        ).map { policyName ->
            when (policyName) {
                LocationPolicy.FUSED -> LocationManager.FUSED_PROVIDER
                LocationPolicy.GPS -> LocationManager.GPS_PROVIDER
                else -> LocationManager.NETWORK_PROVIDER
            }
        }
    }

    /** 안드로이드 10+ 에서 지금 자리를 즉시 한 번 재게 한다 (실내에서 제공자 등록만으로 안 올 때) */
    @SuppressLint("MissingPermission")
    internal fun probe(
        manager: LocationManager,
        providers: List<String>,
        executor: Executor,
        onLocation: (Location) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        providers.forEach { provider ->
            runCatching {
                manager.getCurrentLocation(provider, null, executor) { location ->
                    if (location != null) onLocation(location)
                }
            }
        }
    }

    /** 한 번만 받는 요청 — 제공자 여러 개에 걸고, 첫 측정이 오면 나머지를 모두 뗀다 */
    @SuppressLint("MissingPermission")
    private class SingleShot(
        private val manager: LocationManager,
        private val cached: Location?,
        private val allowStaleFallback: Boolean,
        /** `새로고침` — 캐시와 비교하지 않고 새 측정을 그대로 쓴다 */
        private val force: Boolean,
        private val onResult: (Location?) -> Unit
    ) {
        private val main = Handler(Looper.getMainLooper())
        private var done = false

        private val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = accept(location)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        private val timeout = Runnable { finish(cached.takeIf { allowStaleFallback }) }

        fun start() {
            val providers = enabledProviders(manager)
            if (providers.isEmpty()) {
                // 위치 서비스 자체가 꺼져 있다 — 오래된 좌표라도 있으면 그걸 주고, 화면이 안내한다
                finish(cached.takeIf { allowStaleFallback })
                return
            }
            main.postDelayed(timeout, LocationPolicy.FIX_TIMEOUT_MS)
            var registered = 0
            providers.forEach { provider ->
                val ok = runCatching {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.isSuccess
                if (ok) registered++
            }
            if (registered == 0) {
                finish(cached.takeIf { allowStaleFallback })
                return
            }
            // 제공자 등록만으로는 실내에서 한참 걸린다 — 즉시 측정도 함께 건다
            probe(manager, providers, Executor { runnable -> main.post(runnable) }) { location ->
                accept(location)
            }
        }

        /** 늦게 도착한 옛 측정은 버린다 — 캐시보다 새 좌표만 결과로 쓴다 */
        private fun accept(location: Location) {
            if (done) return
            val old = cached
            val usable = force || old == null ||
                LocationPolicy.acceptFix(location.time, location.accuracy, old.time, old.accuracy)
            if (!usable) return
            finish(location)
        }

        private fun finish(location: Location?) {
            if (done) return
            done = true
            main.removeCallbacksAndMessages(null)
            runCatching { manager.removeUpdates(listener) }
            onResult(location)
        }
    }

    /**
     * 화면이 보이는 동안의 **연속 위치 감시자** (v1.0.31).
     *
     *  - 켜져 있는 제공자(GPS·네트워크·융합)에 모두 등록한다 — 하나가 측정을 안 줘도 나머지가 준다.
     *  - [LocationPolicy.WATCHDOG_INTERVAL_MS] 마다 점검해 [LocationPolicy.WATCHDOG_SILENCE_MS] 넘게
     *    새 측정이 없으면 다시 등록하고 `getCurrentLocation` 으로 강제 측정한다(터널·실내·차 안).
     *  - 늦게 도착한 옛 측정은 흘려보내고, 더 새 좌표만 [onFix] 로 넘긴다.
     */
    class Watcher internal constructor(
        private val manager: LocationManager,
        private val onFix: (Location) -> Unit
    ) {
        private val main = Handler(Looper.getMainLooper())
        private val executor = Executor { runnable -> main.post(runnable) }
        private var running = false
        private var registered = 0
        private var lastFixAt = 0L
        private var lastLocation: Location? = null

        private val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = deliver(location)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        private val watchdog = object : Runnable {
            override fun run() {
                if (!running) return
                val now = System.currentTimeMillis()
                if (LocationPolicy.shouldRestartWatchdog(registered, lastFixAt, now)) {
                    if (registered <= 0) registerAll()
                    forceNow()
                }
                main.postDelayed(this, LocationPolicy.WATCHDOG_INTERVAL_MS)
            }
        }

        fun start() {
            if (running) return
            running = true
            registerAll()
            main.postDelayed(watchdog, LocationPolicy.WATCHDOG_INTERVAL_MS)
        }

        fun stop() {
            if (!running) return
            running = false
            main.removeCallbacksAndMessages(null)
            runCatching { manager.removeUpdates(listener) }
            registered = 0
        }

        /** 지금 자리를 강제로 다시 잰다 (`새로고침` 버튼) */
        @SuppressLint("MissingPermission")
        fun forceNow() {
            if (!running) return
            val providers = enabledProviders(manager)
            if (providers.isEmpty()) return
            probe(manager, providers, executor) { location -> deliver(location) }
        }

        @SuppressLint("MissingPermission")
        private fun registerAll() {
            runCatching { manager.removeUpdates(listener) }
            val providers = enabledProviders(manager)
            var ok = 0
            providers.forEach { provider ->
                val success = runCatching {
                    manager.requestLocationUpdates(
                        provider,
                        LocationPolicy.UPDATE_INTERVAL_MS,
                        LocationPolicy.UPDATE_DISTANCE_M,
                        listener,
                        Looper.getMainLooper()
                    )
                }.isSuccess
                if (success) ok++
            }
            registered = ok
            // 등록에 성공해도 첫 측정이 늦을 수 있다 — 바로 한 번 재 본다
            if (ok > 0) probe(manager, providers, executor) { location -> deliver(location) }
        }

        private fun deliver(location: Location) {
            if (!running) return
            val prev = lastLocation
            if (prev != null &&
                !LocationPolicy.acceptFix(location.time, location.accuracy, prev.time, prev.accuracy)
            ) {
                return
            }
            lastLocation = location
            lastFixAt = System.currentTimeMillis()
            onFix(location)
        }
    }
}
