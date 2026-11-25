package com.youngsu.daeriledger.ui.spots

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.location.Address
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
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
 *  - OpenStreetMap 타일 위에 **화면 정중앙 고정 핀**이 떠 있고, 지도를 밀거나 확대·축소해서 핀을 맞춘다.
 *  - 손을 뗀 뒤 잠잠해지면 백그라운드 Geocoder 로 핀 위치의 주소를 카드에 보여 준다.
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
            binding.bottomCard.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = bars.bottom + activity.dp(12f)
            }
            insets
        }

        val main = Handler(Looper.getMainLooper())
        val geocodeSeq = AtomicInteger(0)

        var address = ""
        var area = ""

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

        // 처음 중심: 이미 고른 좌표 → 지금 위치 → 성남 정자동
        val lastKnown = if (activity.hasLocationPermission()) LocationHelper.lastKnown(activity) else null
        val initLat = startLat ?: lastKnown?.latitude ?: SpotMapView.DEFAULT_LAT
        val initLng = startLng ?: lastKnown?.longitude ?: SpotMapView.DEFAULT_LNG
        binding.mapView.setCenter(initLat, initLng, SpotMapView.DEFAULT_ZOOM, notify = false)
        showCoords(initLat, initLng)

        binding.mapView.onMove = { lat, lng, settled ->
            showCoords(lat, lng)
            if (settled) geocode(lat, lng)
        }

        binding.closeButton.setOnClickListener { dialog.dismiss() }

        binding.zoomInButton.setOnClickListener { binding.mapView.zoomBy(1f) }
        binding.zoomOutButton.setOnClickListener { binding.mapView.zoomBy(-1f) }

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
                } else {
                    binding.mapView.setCenter(
                        location.latitude,
                        location.longitude,
                        SpotMapView.DEFAULT_ZOOM
                    )
                }
            }
        }

        binding.confirmButton.setOnClickListener {
            val lat = binding.mapView.centerLat
            val lng = binding.mapView.centerLng
            onPicked(Picked(lat, lng, address, area))
            dialog.dismiss()
        }

        dialog.show()

        // 첫 주소는 창을 띄운 뒤에 찾는다(그래야 결과를 버리지 않는다)
        geocode(initLat, initLng)
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
