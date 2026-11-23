package com.youngsu.daeriledger.ui.spots

import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import com.youngsu.daeriledger.data.Hotspot
import com.youngsu.daeriledger.data.Hotspots
import com.youngsu.daeriledger.data.MySpot
import com.youngsu.daeriledger.data.SpotStat
import com.youngsu.daeriledger.databinding.ItemEmptyBinding
import com.youngsu.daeriledger.databinding.ItemHotspotBinding
import com.youngsu.daeriledger.databinding.ItemMySpotBinding
import com.youngsu.daeriledger.databinding.ScreenSpotsBinding
import com.youngsu.daeriledger.location.LocationHelper
import com.youngsu.daeriledger.ui.MainActivity
import com.youngsu.daeriledger.ui.Screen
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.Dialogs
import com.youngsu.daeriledger.util.hideKeyboard
import com.youngsu.daeriledger.util.setChips
import com.youngsu.daeriledger.util.toast
import kotlin.math.roundToInt

/**
 * 콜밭 화면.
 *  - 위치가 있으면 가까운 순으로 정렬한 주요 콜밭 참고 목록 + 길찾기
 *  - 이미 저장해 둔 콜밭은 따로 모아 보여준다 (새로 저장하는 기능은 없음)
 */
class SpotsScreen(private val activity: MainActivity) : Screen {

    private val binding = ScreenSpotsBinding.inflate(activity.layoutInflater)
    override val view: View get() = binding.root

    private var cityFilter: String? = null
    private var searchQuery: String = ""
    private var userLocation: Location? = null
    private var locating = false
    private var savedSpots: List<MySpot> = emptyList()
    private var spotStats: List<SpotStat> = emptyList()

    init {
        binding.locationAction.setOnClickListener { onLocationAction() }
        binding.locationCard.setOnClickListener { onLocationAction() }
        binding.spotSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            if (query != searchQuery) {
                searchQuery = query
                binding.spotSearchClear.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
                renderNearby()
            }
        }
        binding.spotSearchClear.setOnClickListener { binding.spotSearch.setText("") }
        binding.spotSearch.setOnEditorActionListener { field, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                field.hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    override fun refresh() {
        val today = BizTime.businessDayOfNow(activity.settings.cutoffHour)
        savedSpots = activity.repo.allSpots()
        spotStats = activity.repo.spotStats(today.minusDays(89), today, limit = 20)

        buildRegionChips()
        renderSavedSpots()
        renderNearby()
        updateLocationCard()
        maybeLocate()
    }

    // ------------------------------------------------------------- 위치

    private fun maybeLocate() {
        if (locating || userLocation != null) return
        if (!activity.hasLocationPermission()) return
        locating = true
        updateLocationCard()
        LocationHelper.request(activity) { location ->
            locating = false
            userLocation = location
            updateLocationCard()
            renderNearby()
        }
    }

    private fun onLocationAction() {
        when {
            !activity.hasLocationPermission() -> activity.requestLocationPermission()
            locating -> Unit
            else -> {
                userLocation = null
                locating = true
                updateLocationCard()
                LocationHelper.request(activity) { location ->
                    locating = false
                    userLocation = location
                    updateLocationCard()
                    renderNearby()
                    if (location == null) activity.toast("위치를 찾지 못했어요. 지역별로 볼 수 있어요")
                }
            }
        }
    }

    private fun updateLocationCard() {
        when {
            !activity.hasLocationPermission() -> {
                binding.locationText.text = "위치 권한이 없어 지역별로 보고 있어요"
                binding.locationAction.text = "위치 켜기"
                binding.locationAction.visibility = View.VISIBLE
            }
            locating -> {
                binding.locationText.text = "내 위치를 확인하는 중…"
                binding.locationAction.visibility = View.GONE
            }
            userLocation != null -> {
                val acc = userLocation?.accuracy?.roundToInt() ?: 0
                binding.locationText.text =
                    if (acc > 0) "내 위치 기준 가까운 순 · 오차 약 ${acc}m" else "내 위치 기준 가까운 순"
                binding.locationAction.text = "새로고침"
                binding.locationAction.visibility = View.VISIBLE
            }
            else -> {
                binding.locationText.text = "위치를 못 찾았어요 — 지역별로 보기"
                binding.locationAction.text = "다시 시도"
                binding.locationAction.visibility = View.VISIBLE
            }
        }
    }

    // ------------------------------------------------------------- 필터

    private fun buildRegionChips() {
        val items = listOf("" to "전체") + Hotspots.CITIES.map { it to it }
        binding.regionChips.setChips(items, cityFilter ?: "") { id ->
            cityFilter = id.ifBlank { null }
            renderNearby()
        }
    }

    private val filteredHotspots: List<Hotspot>
        get() {
            val query = searchQuery.trim()
            val list = Hotspots.ALL.filter {
                (cityFilter == null || it.city == cityFilter) && it.matches(query)
            }
            val loc = userLocation
            return if (loc != null) {
                list.map { it to Hotspots.distanceKm(loc.latitude, loc.longitude, it.lat, it.lng) }
                    .sortedBy { it.second }
                    .map { it.first }
            } else {
                list.sortedWith(compareBy({ Hotspots.CITIES.indexOf(it.city) }, { it.name }))
            }
        }

    private fun distanceOf(spot: Hotspot): Double? {
        val loc = userLocation ?: return null
        return Hotspots.distanceKm(loc.latitude, loc.longitude, spot.lat, spot.lng)
    }

    // -------------------------------------------------- 저장해 둔 콜밭

    private fun renderSavedSpots() {
        binding.savedSpotsColumn.removeAllViews()
        if (savedSpots.isEmpty()) {
            binding.savedSection.visibility = View.GONE
            return
        }
        binding.savedSection.visibility = View.VISIBLE
        binding.savedCountText.text = "${savedSpots.size}곳"
        savedSpots.forEach { spot ->
            val b = ItemMySpotBinding.inflate(LayoutInflater.from(activity), binding.savedSpotsColumn, false)
            b.spotName.text = spot.name
            val peakText = if (spot.peak.isBlank()) "피크 시간 미입력" else "피크 ${spot.peak}"
            b.spotMeta.text = listOf(peakText, if (spot.hasLocation) "위치 저장됨" else "이름만 저장")
                .joinToString(" · ")
            if (spot.memo.isBlank()) b.spotMemo.visibility = View.GONE else {
                b.spotMemo.visibility = View.VISIBLE
                b.spotMemo.text = spot.memo
            }
            b.navigateButton.setOnClickListener { navigate(spot.name, spot.lat, spot.lng) }
            b.deleteButton.setOnClickListener {
                Dialogs.confirm(
                    activity,
                    "콜밭 삭제",
                    "‘${spot.name}’을(를) 저장 목록에서 지울까요? 기록 통계에는 영향이 없어요.",
                    positive = "삭제",
                    destructive = true
                ) {
                    activity.repo.deleteSpot(spot.id)
                    refresh()
                }
            }
            binding.savedSpotsColumn.addView(b.root)
        }
    }

    // ------------------------------------------------------ 주요 콜밭 목록

    private fun renderNearby() {
        binding.nearbyColumn.removeAllViews()
        val list = filteredHotspots
        val query = searchQuery.trim()
        binding.nearbyCountText.text = "${list.size}곳"
        binding.spotsSubtitle.text = when {
            query.isNotEmpty() -> "‘$query’ 검색 결과 · 주요 콜밭 참고 정보"
            userLocation != null -> "내 위치에서 가까운 순 · 주요 콜밭 참고 정보"
            else -> "주요 콜밭 · 피크 시간대 참고 정보"
        }

        if (list.isEmpty()) {
            val empty = ItemEmptyBinding.inflate(LayoutInflater.from(activity), binding.nearbyColumn, false)
            empty.emptyTitle.text = "검색 결과가 없어요"
            empty.emptyMessage.text = "다른 지역·역 이름으로 찾아보거나 지역 칩에서 ‘전체’를 눌러보세요"
            binding.nearbyColumn.addView(empty.root)
            return
        }

        list.forEach { hotspot ->
            val b = ItemHotspotBinding.inflate(LayoutInflater.from(activity), binding.nearbyColumn, false)
            b.spotName.text = hotspot.name
            b.spotArea.text = hotspot.area
            b.spotPeak.text = "피크 ${hotspot.peak}"
            b.spotTip.text = hotspot.tip

            val distance = distanceOf(hotspot)
            if (distance != null) {
                b.spotDistance.visibility = View.VISIBLE
                b.spotDistance.text = Hotspots.formatDistance(distance)
            } else {
                b.spotDistance.visibility = View.GONE
            }

            val myStat = findStatFor(hotspot)
            if (myStat != null) {
                b.spotRideCount.visibility = View.VISIBLE
                b.spotRideCount.text = "내 기록 ${myStat.rides}콜"
            } else {
                b.spotRideCount.visibility = View.GONE
            }

            b.navigateButton.setOnClickListener { navigate(hotspot.name, hotspot.lat, hotspot.lng) }
            b.root.setOnClickListener { navigate(hotspot.name, hotspot.lat, hotspot.lng) }
            binding.nearbyColumn.addView(b.root)
        }
    }

    private fun findStatFor(hotspot: Hotspot): SpotStat? {
        val tokens = hotspot.name.split("·", "·", " ").map { it.trim() }.filter { it.length >= 2 }
        return spotStats.firstOrNull { stat ->
            val tag = stat.spot.trim()
            tag == hotspot.name || Hotspots.findByName(tag)?.id == hotspot.id || tokens.any { tag.contains(it) }
        }
    }

    // -------------------------------------------------------------- 길찾기

    private fun navigate(name: String, lat: Double?, lng: Double?) {
        val uri = if (lat != null && lng != null) {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})")
        } else {
            val found = Hotspots.findByName(name)
            if (found != null) {
                Uri.parse("geo:${found.lat},${found.lng}?q=${found.lat},${found.lng}(${Uri.encode(name)})")
            } else {
                Uri.parse("geo:0,0?q=${Uri.encode(name)}")
            }
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            runCatching {
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://maps.google.com/?q=${Uri.encode(name)}")
                    )
                )
            }.onFailure { activity.toast("지도 앱을 열 수 없어요") }
        }
    }
}
