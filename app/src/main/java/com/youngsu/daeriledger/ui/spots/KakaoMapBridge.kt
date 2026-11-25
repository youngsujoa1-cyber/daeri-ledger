package com.youngsu.daeriledger.ui.spots

import android.os.Handler
import android.webkit.JavascriptInterface

/**
 * 카카오맵 WebView 안의 JavaScript 가 앱으로 결과를 넘기는 통로(`window.Bridge`).
 *
 *  - `@JavascriptInterface` 메서드는 WebView 의 JavaScript 스레드에서 불리므로,
 *    모두 [main] 핸들러로 넘겨 화면(다이얼로그)을 메인 스레드에서만 건드린다.
 *  - 넘어온 문자열은 그대로 믿지 않고 빈 값·길이를 다듬어 쓴다.
 */
class KakaoMapBridge(
    private val main: Handler,
    private val ready: () -> Unit,
    private val idle: (lat: Double, lng: Double) -> Unit,
    private val address: (lat: Double, lng: Double, full: String, area: String) -> Unit,
    private val error: (message: String) -> Unit
) {

    @JavascriptInterface
    fun onReady() {
        main.post { runCatching { ready() } }
    }

    @JavascriptInterface
    fun onIdle(lat: Double, lng: Double) {
        if (!valid(lat, lng)) return
        main.post { runCatching { idle(lat, lng) } }
    }

    @JavascriptInterface
    fun onAddress(lat: Double, lng: Double, full: String?, area: String?) {
        if (!valid(lat, lng)) return
        val cleanFull = clean(full)
        val cleanArea = clean(area)
        main.post { runCatching { address(lat, lng, cleanFull, cleanArea) } }
    }

    @JavascriptInterface
    fun onError(message: String?) {
        val text = clean(message).ifBlank { "error" }
        main.post { runCatching { error(text) } }
    }

    private fun valid(lat: Double, lng: Double): Boolean =
        lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            !(lat == 0.0 && lng == 0.0)

    private fun clean(value: String?): String =
        value.orEmpty().replace('\n', ' ').replace('\r', ' ').trim().take(160)
}
