package com.youngsu.daeriledger.ui.spots

import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.CallSpots
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
import com.youngsu.daeriledger.util.color
import com.youngsu.daeriledger.util.hideKeyboard
import com.youngsu.daeriledger.util.setChips
import com.youngsu.daeriledger.util.toast
import kotlin.math.roundToInt

/**
 * 콜밭 화면.
 *  - 위치가 있으면 가까운 순으로 정렬한 콜밭 목록 + 길찾기
 *  - 출처(기본·콜포인트1·콜포인트2·탁송)와 시·도로 걸러 보고, 이름·주소로 검색
 *  - 현장에 가 보니 없어진 곳은 카드를 길게 눌러 숨긴다 (되돌리기 가능)
 *  - 이미 저장해 둔 콜밭은 따로 모아 보여준다 (새로 저장하는 기능은 없음)
 */
class SpotsScreen(private val activity: MainActivity) : Screen {

    private val binding = ScreenSpotsBinding.inflate(activity.layoutInflater)
    override val view: View get() = binding.root

    private var cityFilter: String? = null
    private var sourceFilter: String? = null
    private var searchQuery: String = ""

    private companion object {
        /** 한 번에 그리는 카드 수 — 1,500곳 넘는 목록을 한꺼번에 inflate 하지 않기 위한 값 */
        const val PAGE_SIZE = 40
        const val SEARCH_PAGE_SIZE = 60
    }
    private var userLocation: Location? = null
    private var locating = false
    private var savedSpots: List<MySpot> = emptyList()
    private var spotStats: List<SpotStat> = emptyList()

    /** 전체 콜밭 (기본 + 카카오맵 즐겨찾기) — 처음 한 번만 읽는다 */
    private var spotList: List<Hotspot> = emptyList()
    private var hiddenIds: Set<String> = emptySet()

    /** 화면에 그린 목록 상태 — 1,500곳이 넘어 한 번에 다 만들지 않고 40곳씩 늘린다 */
    private var renderedList: List<Hotspot> = emptyList()
    private var renderedCount = 0

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
        binding.hiddenInfo.setOnClickListener { confirmRestoreHidden() }
        binding.moreSpotsButton.setOnClickListener {
            val list = renderedList
            if (list.isEmpty()) return@setOnClickListener
            val next = (renderedCount + PAGE_SIZE).coerceAtMost(list.size)
            appendRows(list, renderedCount, next)
            updateMoreButton(list)
        }
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
        spotList = CallSpots.all(activity)
        hiddenIds = activity.settings.hiddenSpotIds

        buildSourceChips()
        buildRegionChips()
        updateHiddenInfo()
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

    /** 출처 칩 — 전체 / 기본 / 콜포인트1 / 콜포인트2 / 탁송 */
    private fun buildSourceChips() {
        val allId = "all"
        val items = listOf(allId to "전체") + Hotspots.SOURCES.map { it.first to it.second }
        binding.sourceChips.setChips(items, sourceFilter ?: allId) { id ->
            sourceFilter = if (id == allId) null else id
            renderNearby()
        }
    }

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
            val list = spotList.filter {
                (cityFilter == null || it.city == cityFilter) &&
                    (sourceFilter == null || it.source == sourceFilter) &&
                    it.id !in hiddenIds &&
                    it.matches(query)
            }
            val loc = userLocation
            return if (loc != null) {
                list.map { it to Hotspots.distanceKm(loc.latitude, loc.longitude, it.lat, it.lng) }
                    .sortedBy { it.second }
                    .map { it.first }
            } else {
                list.sortedWith(compareBy({ sourceOrder(it.source) }, { Hotspots.CITIES.indexOf(it.city) }, { it.name }))
            }
        }

    /** 위치 정보가 없을 때 정렬용 — 기본 콜밭을 먼저, 그 다음 출처별 */
    private fun sourceOrder(source: String): Int {
        val index = Hotspots.SOURCES.indexOfFirst { it.first == source }
        return if (index < 0) Hotspots.SOURCES.size else index
    }

    private fun sourceLabel(source: String?): String =
        Hotspots.SOURCES.firstOrNull { it.first == (source ?: "") }?.second ?: ""

    private fun distanceOf(spot: Hotspot): Double? {
        val loc = userLocation ?: return null
        return Hotspots.distanceKm(loc.latitude, loc.longitude, spot.lat, spot.lng)
    }

    // ------------------------------------------------------- 숨긴 콜밭

    private fun updateHiddenInfo() {
        val count = hiddenIds.size
        if (count == 0) {
            binding.hiddenInfo.visibility = View.GONE
        } else {
            binding.hiddenInfo.visibility = View.VISIBLE
            binding.hiddenInfo.text = "숨긴 콜밭 ${count}곳 · 되돌리기"
        }
    }

    /** 카드를 길게 눌렀을 때 — 현장에 없어진 곳을 목록에서 숨긴다 */
    private fun confirmHide(spot: Hotspot) {
        Dialogs.confirm(
            activity,
            "콜밭 숨기기",
            "‘${spot.name}’을(를) 목록에서 숨길까요? 없어진 곳이면 숨겨 두세요.\n" +
                "숨긴 곳은 목록 위의 ‘되돌리기’로 다시 볼 수 있어요.",
            positive = "숨기기",
            destructive = true
        ) {
            activity.settings.hiddenSpotIds = activity.settings.hiddenSpotIds + spot.id
            hiddenIds = activity.settings.hiddenSpotIds
            updateHiddenInfo()
            renderNearby()
            activity.toast("‘${spot.name}’ 숨김")
        }
    }

    private fun confirmRestoreHidden() {
        val count = hiddenIds.size
        if (count == 0) return
        Dialogs.confirm(
            activity,
            "숨긴 콜밭 되돌리기",
            "숨겨 둔 콜밭 ${count}곳을 모두 목록에 다시 보이게 할까요?",
            positive = "되돌리기"
        ) {
            activity.settings.hiddenSpotIds = emptySet()
            hiddenIds = emptySet()
            updateHiddenInfo()
            renderNearby()
            activity.toast("숨긴 콜밭 ${count}곳을 다시 보이게 했어요")
        }
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

    // ------------------------------------------------------ 콜밭 목록

    private fun renderNearby() {
        binding.nearbyColumn.removeAllViews()
        val list = filteredHotspots
        val query = searchQuery.trim()
        renderedList = list
        renderedCount = 0

        binding.nearbyCountText.text = "${list.size}곳"
        binding.spotsSubtitle.text = when {
            query.isNotEmpty() -> "‘$query’ 검색 결과 · 콜밭 참고 정보"
            sourceFilter != null -> "${sourceLabel(sourceFilter)} 콜밭 · 가까운 순 참고 정보"
            userLocation != null -> "내 위치에서 가까운 순 · 콜밭 ${spotList.size}곳"
            else -> "콜밭 ${list.size}곳 · 출처·지역으로 골라 보기"
        }

        if (list.isEmpty()) {
            binding.moreSpotsButton.visibility = View.GONE
            val empty = ItemEmptyBinding.inflate(LayoutInflater.from(activity), binding.nearbyColumn, false)
            empty.emptyTitle.text = "검색 결과가 없어요"
            empty.emptyMessage.text = "다른 지역·역 이름으로 찾아보거나 ‘전체’ 칩을 눌러보세요"
            binding.nearbyColumn.addView(empty.root)
            return
        }

        // 검색 결과는 조금 더 넉넉히(60곳), 평소 목록은 40곳까지 먼저 그린다.
        val firstPage = if (query.isNotEmpty()) SEARCH_PAGE_SIZE else PAGE_SIZE
        val first = firstPage.coerceAtMost(list.size)
        appendRows(list, 0, first)
        updateMoreButton(list)
    }

    /** [from, to) 구간만 새로 만들어 목록 아래에 붙인다 (전체를 다시 그리지 않음) */
    private fun appendRows(list: List<Hotspot>, from: Int, to: Int) {
        if (to <= from) return
        val inflater = LayoutInflater.from(activity)
        for (index in from until to) {
            val hotspot = list[index]
            val b = ItemHotspotBinding.inflate(inflater, binding.nearbyColumn, false)
            b.spotName.text = hotspot.name
            b.spotArea.text = hotspot.area

            // 출처 배지 — 기본 콜밭은 배지 없이, 즐겨찾기에서 온 곳만 표시
            if (hotspot.source.isBlank()) {
                b.spotBadge.visibility = View.GONE
            } else {
                b.spotBadge.visibility = View.VISIBLE
                b.spotBadge.text = hotspot.source
                val taksong = hotspot.source == "탁송"
                b.spotBadge.setBackgroundResource(if (taksong) R.drawable.bg_tag_blue else R.drawable.bg_tag_amber)
                b.spotBadge.setTextColor(activity.color(if (taksong) R.color.chart_bar else R.color.accent))
            }

            if (hotspot.hasPeak) {
                b.spotPeak.visibility = View.VISIBLE
                b.spotPeak.text = "피크 ${hotspot.peak}"
            } else {
                b.spotPeak.visibility = View.GONE
            }

            if (hotspot.tip.isBlank()) {
                b.spotTip.visibility = View.GONE
            } else {
                b.spotTip.visibility = View.VISIBLE
                b.spotTip.text = hotspot.tip
            }

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
            b.root.setOnLongClickListener {
                confirmHide(hotspot)
                true
            }
            binding.nearbyColumn.addView(b.root)
        }
        renderedCount = to
    }

    private fun updateMoreButton(list: List<Hotspot>) {
        val remaining = list.size - renderedCount
        if (remaining > 0) {
            binding.moreSpotsButton.visibility = View.VISIBLE
            binding.moreSpotsButton.text = "나머지 ${remaining}곳 더 보기"
        } else {
            binding.moreSpotsButton.visibility = View.GONE
        }
    }

    private fun findStatFor(hotspot: Hotspot): SpotStat? {
        val tokens = hotspot.name.split("·", "·", " ").map { it.trim() }.filter { it.length >= 2 }
        return spotStats.firstOrNull { stat ->
            val tag = stat.spot.trim()
            tag == hotspot.name || Hotspots.findByName(tag, spotList)?.id == hotspot.id ||
                tokens.any { tag.contains(it) }
        }
    }

    // -------------------------------------------------------------- 길찾기

    private fun navigate(name: String, lat: Double?, lng: Double?) {
        val uri = if (lat != null && lng != null) {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})")
        } else {
            val found = Hotspots.findByName(name, spotList)
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
