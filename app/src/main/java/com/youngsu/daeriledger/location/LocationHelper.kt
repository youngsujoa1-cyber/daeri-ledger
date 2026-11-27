package com.youngsu.daeriledger.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * 마지막으로 알려진 위치를 최대한 빨리 가져온다.
 * 권한이 없거나 위치를 못 얻으면 null — 콜밭 화면은 지역 필터/기본 순서로 동작한다.
 */
object LocationHelper {

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var best: Location? = null
        providers.forEach { provider ->
            val loc = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (loc != null && (best == null || loc.time > best!!.time)) best = loc
        }
        return best
    }

    /**
     * 마지막 위치가 없거나 [maxCacheAgeMs] 보다 오래됐으면 한 번만 위치 갱신을 요청한다. 최대 6초 기다린다.
     * onResult 는 메인 스레드에서 한 번만 호출된다.
     *
     * @param maxCacheAgeMs 마지막 좌표를 믿을 수 있는 나이 — 기본(-1)은 나이와 상관없이 바로 쓰는 예전 방식.
     *   `📍 현위치` 처럼 지금 서 있는 자리가 필요한 곳은 짧게 줘서 오래된 좌표(다른 동네)를 쓰지 않게 한다.
     */
    @SuppressLint("MissingPermission")
    fun request(context: Context, maxCacheAgeMs: Long = -1L, onResult: (Location?) -> Unit) {
        val cached = lastKnown(context)
        val cacheFresh = cached != null &&
            (maxCacheAgeMs < 0 || System.currentTimeMillis() - cached.time <= maxCacheAgeMs)
        if (cacheFresh) {
            onResult(cached)
            return
        }
        if (!hasPermission(context)) {
            onResult(null)
            return
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            onResult(null)
            return
        }
        val main = Handler(Looper.getMainLooper())
        var delivered = false
        fun deliver(location: Location?) {
            if (delivered) return
            delivered = true
            main.removeCallbacksAndMessages(null)
            listenerPlaceholder?.let { l -> runCatching { manager.removeUpdates(l) } }
            onResult(location)
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = deliver(location)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        listenerPlaceholder = listener

        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            deliver(null)
            return
        }
        runCatching {
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }.onFailure {
            deliver(null)
            return
        }
        main.postDelayed({ deliver(null) }, 6_000L)
    }

    private var listenerPlaceholder: LocationListener? = null
}
