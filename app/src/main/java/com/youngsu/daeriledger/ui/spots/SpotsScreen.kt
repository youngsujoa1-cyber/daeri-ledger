package com.youngsu.daeriledger.ui.spots

import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
 *  - 출처 칩 없이 **하나의 콜밭 목록** — 시·도 칩으로 걸러 보고, 이름·주소로 검색
 *  - 현장에 가 보니 없어진 곳은 카드를 길게 눌러 숨기거나(내 콜밭은 삭제) 한다 (되돌리기 가능)
 *  - `+ 콜밭 추가` 로 내가 다니는 콜밭을 직접 넣는다 (구분·지역·피크·메모, GPS/주소로 좌표)
 */
class SpotsScreen(private val activity: MainActivity) : Screen {

    private val binding = ScreenSpotsBinding.inflate(activity.layoutInflater)
    override val view: View get() = binding.root

    private var cityFilter: String? = null
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

    /** 전체 콜밭 (기본 + 기사 추천 + 탁송) + 내가 추가한 콜밭 — 새로고침마다 다시 만든다 */
    private var spotList: List<Hotspot> = emptyList()
    private var hiddenIds: Set<String> = emptySet()

    /** 화면에 그린 목록 상태 — 1,500곳이 넘어 한 번에 다 만들지 않고 40곳씩 늘린다 */
    private var renderedList: List<Hotspot> = emptyList()
    private var renderedCount = 0

    init {
        binding.locationAction.setOnClickListener { onLocationAction() }
        binding.locationCard.setOnClickListener { onLocationAction() }
        binding.addSpotButton.setOnClickListener { showAddSpotDialog() }
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
        // 기본·기사 추천·탁송 콜밭 + 내가 추가한 콜밭(DB)을 한 목록으로
        spotList = CallSpots.all(activity) + savedSpots.map { Hotspots.fromMySpot(it) }
        hiddenIds = activity.settings.hiddenSpotIds

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
                    it.id !in hiddenIds &&
                    it.matches(query)
            }
            val loc = userLocation
            return if (loc != null) {
                // 좌표를 저장하지 않은 콜밭은 거리 계산에서 빠지므로 목록 맨 뒤로 보낸다
                list.map { it to (distanceOf(it) ?: Double.MAX_VALUE) }
                    .sortedBy { it.second }
                    .map { it.first }
            } else {
                // 위치를 모를 때: 내 콜밭 → 시·도 순 → 이름 순
                list.sortedWith(
                    compareBy(
                        { !it.mine },
                        { Hotspots.CITIES.indexOf(it.city).let { i -> if (i < 0) Hotspots.CITIES.size else i } },
                        { it.name }
                    )
                )
            }
        }

    private fun distanceOf(spot: Hotspot): Double? {
        if (!spot.hasLocation) return null
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

    /** 카드를 길게 눌렀을 때 — 숨기기, 내가 추가한 콜밭이면 삭제도 고를 수 있다 */
    private fun showSpotActions(spot: Hotspot) {
        val labels = if (spot.mine) arrayOf("목록에서 숨기기", "내 콜밭 삭제")
        else arrayOf("목록에서 숨기기")
        MaterialAlertDialogBuilder(activity)
            .setTitle(spot.name)
            .setItems(labels) { _, which ->
                if (which == 0) confirmHide(spot) else confirmDeleteMySpot(spot)
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    /** 내가 추가한 콜밭을 목록에서 지운다 (기록 통계에는 영향 없음) */
    private fun confirmDeleteMySpot(spot: Hotspot) {
        val id = spot.id.removePrefix("my-").toLongOrNull() ?: return
        Dialogs.confirm(
            activity,
            "내 콜밭 삭제",
            "‘${spot.name}’을(를) 지울까요? 기록 통계에는 영향이 없어요.",
            positive = "삭제",
            destructive = true
        ) {
            activity.repo.deleteSpot(id)
            activity.toast("‘${spot.name}’ 삭제했어요")
            refresh()
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
            val peakText = if (spot.peak.isBlank()) "피크 미입력" else "피크 ${spot.peak}"
            b.spotMeta.text = listOf(
                spot.service.label,
                peakText,
                if (spot.hasLocation) "위치 저장됨" else "이름만 저장"
            ).joinToString(" · ")
            // 주소·거리 한 줄 + 메모 한 줄 (둘 다 없으면 숨김)
            val where = listOfNotNull(
                spot.area.takeIf { it.isNotBlank() },
                distanceOf(Hotspots.fromMySpot(spot))?.let { Hotspots.formatDistance(it) }
            ).joinToString(" · ")
            val memoLines = listOfNotNull(where.takeIf { it.isNotBlank() }, spot.memo.takeIf { it.isNotBlank() })
            if (memoLines.isEmpty()) {
                b.spotMemo.visibility = View.GONE
            } else {
                b.spotMemo.visibility = View.VISIBLE
                b.spotMemo.text = memoLines.joinToString("\n")
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

    // ------------------------------------------------------ 콜밭 추가

    /** `+ 콜밭 추가` — 장소명·구분·지역·피크·메모를 받아 내 콜밭으로 저장한다 */
    private fun showAddSpotDialog() {
        AddSpotDialog.show(activity) {
            // 방금 추가한 콜밭이 바로 보이도록 검색·출처·지역 필터를 ‘전체’로 돌리고 맨 위로
            searchQuery = ""
            binding.spotSearch.setText("")
            cityFilter = null
            refresh()
            binding.spotsScroll.smoothScrollTo(0, 0)
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
            userLocation != null -> "내 위치에서 가까운 순 · 콜밭 ${spotList.size}곳"
            else -> "콜밭 ${list.size}곳 · 지역으로 골라 보기"
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
            if (hotspot.area.isBlank()) {
                b.spotArea.visibility = View.GONE
            } else {
                b.spotArea.visibility = View.VISIBLE
                b.spotArea.text = hotspot.area
            }

            // 출처 배지 — 기본 콜밭은 배지 없이, 기사 추천·탁송·내 콜밭만 표시
            when {
                hotspot.mine -> {
                    b.spotBadge.visibility = View.VISIBLE
                    b.spotBadge.text = "내 콜밭"
                    b.spotBadge.setBackgroundResource(R.drawable.bg_pill_accent)
                    b.spotBadge.setTextColor(activity.color(R.color.accent))
                }
                hotspot.source.isBlank() -> b.spotBadge.visibility = View.GONE
                else -> {
                    b.spotBadge.visibility = View.VISIBLE
                    b.spotBadge.text = hotspot.source
                    val taksong = hotspot.source == Hotspots.SOURCE_TAKSONG
                    b.spotBadge.setBackgroundResource(if (taksong) R.drawable.bg_tag_blue else R.drawable.bg_tag_amber)
                    b.spotBadge.setTextColor(activity.color(if (taksong) R.color.chart_bar else R.color.accent))
                }
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
                showSpotActions(hotspot)
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
