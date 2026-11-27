package com.youngsu.daeriledger.ui.spots

import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.MySpot
import com.youngsu.daeriledger.data.ServiceType
import com.youngsu.daeriledger.location.LocationHelper
import com.youngsu.daeriledger.ui.MainActivity
import com.youngsu.daeriledger.util.color
import com.youngsu.daeriledger.util.hideKeyboard
import com.youngsu.daeriledger.util.setChips
import com.youngsu.daeriledger.util.toast
import java.util.Locale

/**
 * `+ 콜밭 추가` 다이얼로그.
 *
 *  - 장소명(필수) · 구분(대리/탁송) · 시·도/지역 또는 주소 · 피크 · 메모를 받아
 *  - 창을 열면 **현재 GPS 위치를 자동으로 잡아** 좌표와 시·도/지역 칸을 미리 채우고,
 *    **🗺️ 지도에서 위치 고르기**(카카오맵 피커)로 다른 자리를 골라 덮어쓸 수 있다.
 *    위치를 못 잡으면 `위치를 아직 못 잡았어요 — 지도에서 골라 주세요` 로 안내한다.
 *  - 좌표 없이 이름만 저장해도 된다.
 *  - **👑 운영자 모드**(v1.0.23): 설정에서 암호로 잠금을 풀어 두면 창 위에 `등록 시 3명 대기 없이 바로 전체 공개`
 *    배지가 보이고, 저장한 콜밭이 모든 기사님 앱의 공용 콜밭에 바로 올라간다.
 *  - 저장한 콜밭은 DB(my_spots)에 남아 콜밭 목록·검색·거리순 정렬·길찾기·숨기기에 그대로 쓰이고,
 *    JSON 백업에도 함께 들어간다.
 */
object AddSpotDialog {

    fun show(activity: MainActivity, onSaved: (MySpot) -> Unit) {
        val container = LayoutInflater.from(activity).inflate(R.layout.dialog_callspot_add, null)
        val nameInput = container.findViewById<EditText>(R.id.spotNameInput)
        val areaInput = container.findViewById<EditText>(R.id.spotAreaInput)
        val peakInput = container.findViewById<EditText>(R.id.spotPeakInput)
        val memoInput = container.findViewById<EditText>(R.id.spotMemoInput)
        val serviceChips = container.findViewById<ChipGroup>(R.id.spotServiceChips)
        val mapButton = container.findViewById<MaterialButton>(R.id.pickOnMapButton)
        val locationValue = container.findViewById<TextView>(R.id.locationValueText)
        val adminHint = container.findViewById<View>(R.id.adminModeHint)

        // 👑 운영자 모드 — 이 창에서 등록하는 콜밭이 3명 대기 없이 바로 전체 공개된다
        val adminMode = activity.settings.isAdminMode
        adminHint.visibility = if (adminMode) View.VISIBLE else View.GONE

        var service = ServiceType.DAERI
        var lat: Double? = null
        var lng: Double? = null

        /** 지도에서 고른 주소(있으면 좌표 대신 보여 준다) */
        var placeLabel: String? = null

        /** 지도에서 직접 고른 뒤에는 늦게 도착한 자동 위치가 덮어쓰지 않는다 */
        var userPicked = false

        fun showLocation() {
            val la = lat
            val ln = lng
            locationValue.text = when {
                la != null && ln != null && placeLabel != null ->
                    String.format(Locale.KOREA, "위치: %s · %.5f, %.5f", placeLabel, la, ln)
                la != null && ln != null ->
                    String.format(Locale.KOREA, "위치: %.5f, %.5f · 길찾기에 쓰여요", la, ln)
                else -> "위치를 아직 못 잡았어요 — 지도에서 골라 주세요"
            }
            mapButton.text =
                if (la != null && ln != null) "🗺️ 지도에서 위치 바꾸기" else "🗺️ 지도에서 위치 고르기"
        }

        serviceChips.setChips(
            listOf(ServiceType.DAERI.id to "대리", ServiceType.TAKSONG.id to "탁송"),
            service.id
        ) { id -> service = ServiceType.from(id) }

        // 창을 열면 지금 위치를 자동으로 잡는다 (권한이 없으면 안내만)
        fun autoLocate() {
            if (!activity.hasLocationPermission()) {
                showLocation()
                return
            }
            LocationHelper.lastKnown(activity)?.let { last ->
                if (!userPicked) {
                    lat = last.latitude
                    lng = last.longitude
                    placeLabel = null
                    showLocation()
                }
            }
            if (lat == null) locationValue.text = "현재 위치를 확인하는 중…"
            LocationHelper.request(activity) { location ->
                if (userPicked) return@request
                if (location == null) {
                    showLocation()
                    return@request
                }
                lat = location.latitude
                lng = location.longitude
                placeLabel = null
                showLocation()
                // 지역 칸이 비어 있으면 지금 위치의 주소로 채워 준다
                if (areaInput.text.isNullOrBlank()) {
                    findAddress(activity, location.latitude, location.longitude) { address ->
                        if (!userPicked && areaInput.text.isNullOrBlank() && address.isNotBlank()) {
                            areaInput.setText(address)
                        }
                    }
                }
            }
        }

        // 지도에서 위치 고르기 → 고른 좌표·주소로 덮어쓴다
        fun pickOnMap(startLat: Double?, startLng: Double?) {
            areaInput.hideKeyboard()
            SpotMapPicker.show(activity, startLat, startLng) { picked ->
                userPicked = true
                lat = picked.lat
                lng = picked.lng
                placeLabel = picked.address.ifBlank { null }
                if (picked.area.isNotBlank()) areaInput.setText(picked.area)
                showLocation()
                activity.toast("지도에서 고른 위치를 넣었어요")
            }
        }

        mapButton.setOnClickListener {
            val knownLat = lat
            val knownLng = lng
            val typedArea = areaInput.text.toString().trim()
            when {
                // 이미 좌표가 있으면 그 자리에서 시작
                knownLat != null && knownLng != null -> pickOnMap(knownLat, knownLng)
                // 주소만 적어 두었으면 그 근처에서 시작
                typedArea.isNotEmpty() && Geocoder.isPresent() -> {
                    mapButton.isEnabled = false
                    locationValue.text = "‘$typedArea’ 근처를 지도에서 여는 중…"
                    lookupPlace(activity, typedArea) { found ->
                        mapButton.isEnabled = true
                        showLocation()
                        val last = LocationHelper.lastKnown(activity)
                        pickOnMap(
                            found?.first ?: last?.latitude,
                            found?.second ?: last?.longitude
                        )
                    }
                }
                // 아니면 지금 위치에서 시작
                else -> {
                    val last = LocationHelper.lastKnown(activity)
                    pickOnMap(last?.latitude, last?.longitude)
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (adminMode) "콜밭 추가 · 👑 운영자" else "콜밭 추가")
            .setView(container)
            .setNegativeButton("취소", null)
            .setPositiveButton("추가", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(activity.color(R.color.accent))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(activity.color(R.color.text_secondary))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    nameInput.error = "장소명을 적어 주세요"
                    nameInput.requestFocus()
                    return@setOnClickListener
                }
                val spot = MySpot(
                    name = name,
                    lat = lat,
                    lng = lng,
                    peak = peakInput.text.toString().trim(),
                    memo = memoInput.text.toString().trim(),
                    service = service,
                    area = areaInput.text.toString().trim()
                )
                activity.repo.saveSpot(spot)
                dialog.dismiss()
                activity.toast("‘$name’ 콜밭을 추가했어요")
                onSaved(spot)
            }
        }
        dialog.show()
        showLocation()
        autoLocate()
        nameInput.requestFocus()
    }

    /** 주소·지역 이름 → 좌표 (Geocoder 는 네트워크를 타므로 백그라운드 스레드에서) */
    private fun lookupPlace(
        activity: MainActivity,
        query: String,
        onResult: (Pair<Double, Double>?) -> Unit
    ) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            var result: Pair<Double, Double>? = null
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(activity, Locale.KOREA).getFromLocationName(query, 1)
                    ?.firstOrNull()
                    ?.let { address -> result = address.latitude to address.longitude }
            }
            main.post { onResult(result) }
        }.start()
    }

    /** 좌표 → 주소 (지역 칸이 비었을 때만 채우는 용도) */
    private fun findAddress(
        activity: MainActivity,
        lat: Double,
        lng: Double,
        onResult: (String) -> Unit
    ) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            var address = ""
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(activity, Locale.KOREA).getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
                    ?.let { found ->
                        listOfNotNull(
                            found.adminArea,
                            found.locality ?: found.subAdminArea,
                            found.thoroughfare ?: found.subLocality
                        ).filter { it.isNotBlank() }.distinct().joinToString(" ")
                    }
                    ?.let { if (it.isNotBlank()) address = it }
            }
            main.post { onResult(address) }
        }.start()
    }
}
