package com.youngsu.daeriledger.ui.spots

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.location.Address
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.youngsu.daeriledger.BuildConfig
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.databinding.DialogSpotPickerMapBinding
import com.youngsu.daeriledger.location.LocationHelper
import com.youngsu.daeriledger.ui.MainActivity
import com.youngsu.daeriledger.ui.widget.SpotMapView
import com.youngsu.daeriledger.util.color
import com.youngsu.daeriledger.util.dp
import com.youngsu.daeriledger.util.toast
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * `🗺️ 지도에서 위치 고르기` — 화면을 꽉 채우는 지도 피커.
 *
 *  - **카카오맵**(Kakao Maps JavaScript SDK in WebView)을 먼저 띄우고, 화면 정중앙의 앰버 고정 핀을
 *    지도로 맞춘다. 드래그·핀치 줌은 지도가 직접 받고, `+`/`−`·`내 위치` 버튼은 JS 를 불러 조작한다.
 *  - 지도가 멈출 때마다 카카오 `services.Geocoder` 가 도로명(건물명 포함)·지역명을 찾아 하단 카드에
 *    바로 보여 준다(카카오 주소를 못 받으면 Android Geocoder 로 한 번 더 시도).
 *  - 카카오 키가 없는 빌드(`local.properties` 의 `KAKAO_JS_KEY` 비어 있음)이거나 SDK·네트워크 문제로
 *    카카오맵을 못 띄우면 **기존 OpenStreetMap 타일 지도([SpotMapView])로 안전하게 폴백**하고,
 *    그 사실을 카드 위 안내 줄로 알리며 `다시 시도`를 제공한다(하얀 화면으로 멈추지 않는다).
 *  - `이 위치 선택` 을 누르면 위경도 · 주소 · 시·도가 [Picked] 로 돌아가 `+ 콜밭 추가` 폼에 채워진다.
 *  - Activity 를 띄우지 않고 다이얼로그로 띄워서, 뒤에 열려 있던 `콜밭 추가` 폼(이름·피크·메모)이 그대로 남는다.
 */
object SpotMapPicker {

    /** 고른 위치 — 주소를 못 찾았으면 address·area 가 빈 문자열이다(좌표만 저장). */
    data class Picked(
        val lat: Double,
        val lng: Double,
        val address: String,
        val area: String
    )

    private enum class Mode { KAKAO, OSM }

    /** 카카오맵이 응답하지 않을 때 폴백까지 기다리는 시간 */
    private const val KAKAO_TIMEOUT_MS = 9_000L

    fun show(
        activity: MainActivity,
        startLat: Double?,
        startLng: Double?,
        onPicked: (Picked) -> Unit
    ) {
        val binding = DialogSpotPickerMapBinding.inflate(LayoutInflater.from(activity))
        val dialog = Dialog(activity, R.style.Theme_DaeriLedger_FullScreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(activity.color(R.color.bg)))
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }

        // 상태 바·내비게이션 바에 가리지 않게
        ViewCompat.setOnApplyWindowInsetsListener(binding.pickerRoot) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.topBar.updatePadding(top = bars.top + activity.dp(6f))
            binding.sideButtons.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = bars.top + activity.dp(72f)
            }
            binding.mapErrorBar.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = bars.top + activity.dp(62f)
            }
            binding.bottomCard.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = bars.bottom + activity.dp(12f)
            }
            insets
        }

        val main = Handler(Looper.getMainLooper())
        val geocodeSeq = AtomicInteger(0)
        val kakaoAvailable = BuildConfig.KAKAO_JS_KEY.isNotBlank()

        var mode = if (kakaoAvailable) Mode.KAKAO else Mode.OSM
        var address = ""
        var area = ""
        var kakaoLat = 0.0
        var kakaoLng = 0.0
        var kakaoReady = false

        // 처음 중심: 이미 고른 좌표 → 지금 위치 → 성남 정자동
        val lastKnown = if (activity.hasLocationPermission()) LocationHelper.lastKnown(activity) else null
        val initLat = startLat ?: lastKnown?.latitude ?: SpotMapView.DEFAULT_LAT
        val initLng = startLng ?: lastKnown?.longitude ?: SpotMapView.DEFAULT_LNG
        kakaoLat = initLat
        kakaoLng = initLng

        fun showCoords(lat: Double, lng: Double) {
            binding.coordText.text = String.format(
                Locale.KOREA,
                "위도 %.5f · 경도 %.5f",
                lat,
                lng
            )
        }

        fun geocode(lat: Double, lng: Double) {
            val token = geocodeSeq.incrementAndGet()
            if (address.isBlank()) binding.addressText.text = "주소를 찾는 중…"
            Thread {
                var found: Address? = null
                if (Geocoder.isPresent()) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        found = Geocoder(activity, Locale.KOREA)
                            .getFromLocation(lat, lng, 1)
                            ?.firstOrNull()
                    }
                }
                val picked = found
                val full = picked?.let { fullAddress(it) }.orEmpty()
                val short = picked?.let { areaOf(it) }.orEmpty()
                main.post {
                    if (token != geocodeSeq.get() || !dialog.isShowing) return@post
                    if (full.isNotBlank()) {
                        address = full
                        area = short.ifBlank { full }
                        binding.addressText.text = full
                    } else {
                        address = ""
                        area = ""
                        binding.addressText.text = "주소를 찾지 못했어요 — 좌표만 저장돼요"
                    }
                }
            }.start()
        }

        fun currentLat(): Double = if (mode == Mode.KAKAO) kakaoLat else binding.mapView.centerLat

        fun currentLng(): Double = if (mode == Mode.KAKAO) kakaoLng else binding.mapView.centerLng

        /** 화면 구성(지도·핀·출처 표기)을 현재 모드에 맞춘다 */
        fun applyMode() {
            val kakao = mode == Mode.KAKAO
            binding.kakaoWeb.visibility = if (kakao) View.VISIBLE else View.GONE
            binding.mapView.visibility = if (kakao) View.GONE else View.VISIBLE
            binding.centerPin.visibility = if (kakao) View.VISIBLE else View.GONE
            binding.attributionText.text =
                if (kakao) "지도 © Kakao Corp." else "지도 데이터 © OpenStreetMap 기여자"
            if (!kakao) binding.mapLoading.visibility = View.GONE
        }

        /** OpenStreetMap 지도로 전환한다. [fallback] 이면 카카오맵 실패 안내를 함께 띄운다. */
        fun useOsm(fallback: Boolean) {
            val lat = currentLat()
            val lng = currentLng()
            mode = Mode.OSM
            binding.mapView.setCenter(lat, lng, SpotMapView.DEFAULT_ZOOM, notify = false)
            applyMode()
            binding.mapErrorBar.visibility =
                if (fallback && kakaoAvailable) View.VISIBLE else View.GONE
            binding.mapErrorText.text = "카카오맵을 불러오지 못해 기본 지도로 보여 드려요"
            showCoords(lat, lng)
            geocode(lat, lng)
        }

        val kakaoTimeout = Runnable {
            if (mode == Mode.KAKAO && !kakaoReady) {
                binding.mapLoading.visibility = View.GONE
                useOsm(fallback = true)
            }
        }

        /** 카카오맵 페이지를 (다시) 띄운다. */
        fun useKakao() {
            if (!kakaoAvailable) {
                useOsm(fallback = false)
                return
            }
            val lat = currentLat()
            val lng = currentLng()
            kakaoLat = lat
            kakaoLng = lng
            kakaoReady = false
            mode = Mode.KAKAO
            applyMode()
            binding.mapErrorBar.visibility = View.GONE
            binding.mapLoading.visibility = View.VISIBLE
            binding.kakaoWeb.loadDataWithBaseURL(
                KakaoMapPage.BASE_URL,
                KakaoMapPage.html(
                    BuildConfig.KAKAO_JS_KEY,
                    lat,
                    lng,
                    KakaoMapPage.DEFAULT_LEVEL
                ),
                "text/html",
                "UTF-8",
                null
            )
            main.removeCallbacks(kakaoTimeout)
            main.postDelayed(kakaoTimeout, KAKAO_TIMEOUT_MS)
        }

        /** 카카오맵이 떠 있지 못할 때 — 기다리지 않고 바로 폴백한다. */
        fun kakaoFailed() {
            main.removeCallbacks(kakaoTimeout)
            if (mode == Mode.KAKAO && !kakaoReady) {
                binding.mapLoading.visibility = View.GONE
                useOsm(fallback = true)
            }
        }

        val bridge = KakaoMapBridge(
            main = main,
            ready = ready@{
                if (!dialog.isShowing || mode != Mode.KAKAO) return@ready
                kakaoReady = true
                main.removeCallbacks(kakaoTimeout)
                binding.mapLoading.visibility = View.GONE
                binding.mapErrorBar.visibility = View.GONE
            },
            idle = idle@{ lat, lng ->
                if (!dialog.isShowing || mode != Mode.KAKAO) return@idle
                kakaoLat = lat
                kakaoLng = lng
                showCoords(lat, lng)
                // 카카오맵을 못 쓰게 되면 같은 자리에서 OSM 지도가 뜨도록 미리 맞춰 둔다
                binding.mapView.setCenter(lat, lng, SpotMapView.DEFAULT_ZOOM, notify = false)
            },
            address = addr@{ lat, lng, full, areaText ->
                if (!dialog.isShowing || mode != Mode.KAKAO) return@addr
                if (full.isNotBlank()) {
                    address = full
                    area = areaText.ifBlank { full }
                    binding.addressText.text = full
                } else {
                    // 카카오 주소를 못 받았으면 Android Geocoder 로 한 번 더
                    geocode(lat, lng)
                }
            },
            error = err@{
                if (!dialog.isShowing) return@err
                kakaoFailed()
            }
        )

        @SuppressLint("SetJavaScriptEnabled")
        fun startKakaoWeb() {
            val web = binding.kakaoWeb
            web.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                useWideViewPort = false
                loadWithOverviewMode = false
                javaScriptCanOpenWindowsAutomatically = false
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = true
                setGeolocationEnabled(false)
            }
            web.setBackgroundColor(activity.color(R.color.bg))
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            web.webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) kakaoFailed()
                }
            }
            web.addJavascriptInterface(bridge, "Bridge")
            useKakao()
        }

        binding.mapView.onMove = { lat, lng, settled ->
            if (mode == Mode.OSM) {
                showCoords(lat, lng)
                if (settled) geocode(lat, lng)
            }
        }
        binding.mapView.setCenter(initLat, initLng, SpotMapView.DEFAULT_ZOOM, notify = false)
        showCoords(initLat, initLng)

        binding.closeButton.setOnClickListener { dialog.dismiss() }

        binding.zoomInButton.setOnClickListener {
            if (mode == Mode.KAKAO) binding.kakaoWeb.evaluateJavascript("zoomBy(1)", null)
            else binding.mapView.zoomBy(1f)
        }
        binding.zoomOutButton.setOnClickListener {
            if (mode == Mode.KAKAO) binding.kakaoWeb.evaluateJavascript("zoomBy(-1)", null)
            else binding.mapView.zoomBy(-1f)
        }

        binding.myLocationButton.setOnClickListener {
            if (!activity.hasLocationPermission()) {
                activity.requestLocationPermission()
                activity.toast("위치 권한을 허용한 뒤 ‘내 위치’를 다시 눌러 주세요")
                return@setOnClickListener
            }
            binding.myLocationButton.isEnabled = false
            LocationHelper.request(activity) { location ->
                binding.myLocationButton.isEnabled = true
                if (location == null) {
                    activity.toast("지금 위치를 찾지 못했어요 — 지도를 직접 움직여 주세요")
                } else if (mode == Mode.KAKAO) {
                    kakaoLat = location.latitude
                    kakaoLng = location.longitude
                    binding.mapView.setCenter(
                        location.latitude,
                        location.longitude,
                        SpotMapView.DEFAULT_ZOOM,
                        notify = false
                    )
                    binding.kakaoWeb.evaluateJavascript(
                        String.format(
                            Locale.US,
                            "moveTo(%.6f, %.6f, %d)",
                            location.latitude,
                            location.longitude,
                            KakaoMapPage.DEFAULT_LEVEL
                        ),
                        null
                    )
                } else {
                    binding.mapView.setCenter(
                        location.latitude,
                        location.longitude,
                        SpotMapView.DEFAULT_ZOOM
                    )
                }
            }
        }

        binding.mapRetryButton.setOnClickListener { useKakao() }
        binding.mapOsmButton.setOnClickListener { useOsm(fallback = false) }

        binding.confirmButton.setOnClickListener {
            onPicked(Picked(currentLat(), currentLng(), address, area))
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            main.removeCallbacks(kakaoTimeout)
            runCatching {
                binding.kakaoWeb.apply {
                    removeJavascriptInterface("Bridge")
                    stopLoading()
                    loadUrl("about:blank")
                    destroy()
                }
            }
        }

        dialog.show()

        if (kakaoAvailable) {
            startKakaoWeb()
        } else {
            // 키 없는 빌드 — 예전처럼 OSM 타일 지도로 동작한다(첫 주소도 여기서 찾는다)
            useOsm(fallback = false)
        }
    }

    /** 카드에 보여 줄 주소 — 예: `경기 성남시 분당구 정자동 정자일로 95` */
    private fun fullAddress(address: Address): String {
        val street = listOfNotNull(address.thoroughfare, address.subThoroughfare)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return listOfNotNull(
            address.adminArea,
            address.locality ?: address.subAdminArea,
            address.subLocality,
            street,
            address.featureName
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
    }

    /** 시·도/지역 칸에 넣을 짧은 주소 — 예: `경기 성남시 분당구 정자동` */
    private fun areaOf(address: Address): String =
        listOfNotNull(
            address.adminArea,
            address.locality ?: address.subAdminArea,
            address.subLocality ?: address.thoroughfare
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
}
