package com.youngsu.daeriledger.ui.spots

import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
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
 *  - **현재 GPS 위치 쓰기** 스위치로 지금 위치를, **주소로 위치 찾기**(Android Geocoder)로
 *    찾은 좌표를, **🗺️ 지도에서 위치 고르기**(OpenStreetMap 지도 피커)로 골라 넣은 좌표를 저장한다.
 *    좌표 없이 이름만 저장해도 된다.
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
        val locationSwitch = container.findViewById<MaterialSwitch>(R.id.useLocationSwitch)
        val mapButton = container.findViewById<MaterialButton>(R.id.pickOnMapButton)
        val findButton = container.findViewById<MaterialButton>(R.id.findAddressButton)
        val locationValue = container.findViewById<TextView>(R.id.locationValueText)

        var service = ServiceType.DAERI
        var lat: Double? = null
        var lng: Double? = null

        /** 지도에서 고른 주소(있으면 좌표 대신 보여 준다) */
        var placeLabel: String? = null

        fun showLocation() {
            val la = lat
            val ln = lng
            locationValue.text = when {
                la != null && ln != null && placeLabel != null ->
                    String.format(Locale.KOREA, "위치 저장: %s · %.5f, %.5f", placeLabel, la, ln)
                la != null && ln != null ->
                    String.format(Locale.KOREA, "위치 저장: %.5f, %.5f · 길찾기에 쓰여요", la, ln)
                else -> "위치 없음 — 이름만 저장돼요"
            }
        }

        serviceChips.setChips(
            listOf(ServiceType.DAERI.id to "대리", ServiceType.TAKSONG.id to "탁송"),
            service.id
        ) { id -> service = ServiceType.from(id) }

        // 현재 GPS 위치
        locationSwitch.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                lat = null
                lng = null
                placeLabel = null
                showLocation()
                return@setOnCheckedChangeListener
            }
            if (!activity.hasLocationPermission()) {
                locationSwitch.isChecked = false
                activity.requestLocationPermission()
                activity.toast("위치 권한을 허용한 뒤 스위치를 다시 켜 주세요")
                return@setOnCheckedChangeListener
            }
            locationValue.text = "현재 위치를 확인하는 중…"
            LocationHelper.request(activity) { location ->
                if (location == null) {
                    locationSwitch.isChecked = false
                    activity.toast("위치를 찾지 못했어요. 주소로 찾아보세요")
                    showLocation()
                } else {
                    lat = location.latitude
                    lng = location.longitude
                    placeLabel = null
                    showLocation()
                    // 지역 칸이 비어 있으면 현재 위치의 주소로 채워 준다
                    if (areaInput.text.isNullOrBlank()) {
                        findAddress(activity, location.latitude, location.longitude) { address ->
                            if (areaInput.text.isNullOrBlank() && address.isNotBlank()) areaInput.setText(address)
                        }
                    }
                }
            }
        }

        // 지도에서 위치 고르기 (OpenStreetMap 타일 피커)
        fun pickOnMap(startLat: Double?, startLng: Double?) {
            areaInput.hideKeyboard()
            SpotMapPicker.show(activity, startLat, startLng) { picked ->
                locationSwitch.isChecked = false
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

        // 주소로 위치 찾기 (Android Geocoder)
        findButton.setOnClickListener {
            val query = areaInput.text.toString().trim()
            if (query.isEmpty()) {
                areaInput.error = "주소나 지역 이름을 먼저 적어 주세요"
                areaInput.requestFocus()
                return@setOnClickListener
            }
            if (!Geocoder.isPresent()) {
                activity.toast("이 폰에서는 주소 검색을 쓸 수 없어요 — 현재 GPS 위치를 켜 주세요")
                return@setOnClickListener
            }
            locationValue.text = "‘$query’ 위치를 찾는 중…"
            locationSwitch.isChecked = false
            lookupPlace(activity, query) { found ->
                if (found == null) {
                    locationValue.text = "위치 없음 — 이름만 저장돼요"
                    activity.toast("‘$query’ 위치를 찾지 못했어요. 다른 이름으로 해보세요")
                } else {
                    lat = found.first
                    lng = found.second
                    placeLabel = null
                    showLocation()
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("콜밭 추가")
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
