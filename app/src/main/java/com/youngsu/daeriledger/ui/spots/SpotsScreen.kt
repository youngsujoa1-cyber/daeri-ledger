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
import com.youngsu.daeriledger.data.SpotAggregator
import com.youngsu.daeriledger.data.SpotCloud
import com.youngsu.daeriledger.data.SpotPublicState
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
 *  - 현장에 가 보니 없어진 곳은 카드를 길게 눌러 내 화면에서 숨기거나 **없어진 곳으로 신고**한다
 *    (신고는 서버로 가고, 같은 자리에서 5명 이상 모이면 모든 기사님 화면에서 숨겨진다 — 내 화면은 바로 숨김)
 *  - `+ 콜밭 추가` 로 내가 다니는 콜밭을 직접 넣는다 (구분·지역·피크·메모, GPS/지도로 좌표). 좌표가 있으면 서버에도 제보한다
 *  - 기사님들이 **5명 이상** 제보한 공용 콜밭은 `기사 제보` 배지로 목록에 함께 보인다 (5분 캐시, 실패하면 로컬 목록만)
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

    /** 전체 콜밭 (기본 + 기사 추천) + 내가 추가한 콜밭 + 기사님 제보 콜밭 — 새로고침마다 다시 만든다 */
    private var spotList: List<Hotspot> = emptyList()
    private var hiddenIds: Set<String> = emptySet()

    /** 서버에서 받은 공용 제보 상태 (5명 이상 등록 / 5명 이상 삭제) */
    private var cloudState: SpotPublicState = SpotPublicState()
    private var cloudLoading = false

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
        hiddenIds = activity.settings.hiddenSpotIds
        rebuildSpotList()

        buildRegionChips()
        updateHiddenInfo()
        renderSavedSpots()
        renderNearby()
        updateLocationCard()
        maybeLocate()
        // 서버 제보는 비동기로 — 네트워크가 안 되면 조용히 로컬 목록만 유지한다
        loadCloud()
    }

    /** 기본·기사 추천·내 콜밭 + 서버 공용 콜밭을 한 목록으로 */
    private fun rebuildSpotList() {
        val local = CallSpots.all(activity) + savedSpots.map { Hotspots.fromMySpot(it) }
        // 같은 이름이 이미 기본/기사 추천 목록에 있으면 제보 콜밭으로 또 그리지 않는다
        val cloud = cloudState.added
            .map { Hotspots.fromPublicSpot(it) }
            .filter { spot -> local.none { it.name.trim().equals(spot.name.trim(), ignoreCase = true) } }
        spotList = local + cloud
    }

    /** 공용 제보 상태(5명 이상 등록·5명 이상 삭제)를 받아 목록에 반영한다 */
    private fun loadCloud(force: Boolean = false) {
        if (cloudLoading) return
        cloudLoading = true
        SpotCloud.publicState(activity, force) { state ->
            cloudLoading = false
            if (state == cloudState) return@publicState
            cloudState = state
            rebuildSpotList()
            updateHiddenInfo()
            renderNearby()
        }
    }

    /** 기사님들이 5명 이상 '없어졌다'고 신고한 자리(반경 150m)에 있는 콜밭인지 */
    private fun isCloudRemoved(spot: Hotspot): Boolean =
        spot.hasLocation && SpotAggregator.isRemoved(spot.lat, spot.lng, cloudState.removed)

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
                // 새로고침을 누르면 기사님 제보도 다시 받아 온다 (서버는 5분 캐시)
                loadCloud(force = true)
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
                    !isCloudRemoved(it) &&
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
        val cloudGone = cloudState.removed.size
        val parts = ArrayList<String>()
        if (count > 0) parts.add("숨긴 콜밭 ${count}곳 · 되돌리기")
        if (cloudGone > 0) parts.add("기사님 신고로 숨긴 자리 ${cloudGone}곳")
        if (parts.isEmpty()) {
            binding.hiddenInfo.visibility = View.GONE
        } else {
            binding.hiddenInfo.visibility = View.VISIBLE
            binding.hiddenInfo.text = parts.joinToString(" · ")
        }
    }

    /** 카드를 길게 눌렀을 때 — 내 화면에서 숨기기, 없어진 곳 신고(5명 이상이면 공용 삭제), 내 콜밭 삭제 */
    private fun showSpotActions(spot: Hotspot) {
        val labels = ArrayList<String>()
        labels.add("내 화면에서만 숨기기")
        labels.add("없어진 곳 신고 (5명 이상이면 모든 기사님 화면에서 숨김)")
        if (spot.mine) labels.add("내 콜밭 삭제")
        MaterialAlertDialogBuilder(activity)
            .setTitle(spot.name)
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> confirmHide(spot)
                    1 -> confirmReportRemoved(spot)
                    else -> confirmDeleteMySpot(spot)
                }
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

    /** 카드를 길게 눌렀을 때 — 현장에 없어진 곳을 내 화면에서 숨긴다 */
    private fun confirmHide(spot: Hotspot) {
        Dialogs.confirm(
            activity,
            "콜밭 숨기기",
            "‘${spot.name}’을(를) 내 화면에서 숨길까요? 없어진 곳이면 숨겨 두세요.\n" +
                "숨긴 곳은 목록 위의 ‘되돌리기’로 다시 볼 수 있어요.",
            positive = "숨기기",
            destructive = true
        ) {
            hideLocally(spot, "‘${spot.name}’ 숨김")
        }
    }

    /** 내 화면에서만 숨긴다 — 되돌리기 가능 (서버에 알리지 않음) */
    private fun hideLocally(spot: Hotspot, toastText: String?) {
        activity.settings.hiddenSpotIds = activity.settings.hiddenSpotIds + spot.id
        hiddenIds = activity.settings.hiddenSpotIds
        updateHiddenInfo()
        renderNearby()
        if (toastText != null) activity.toast(toastText)
    }

    /**
     * 없어진 곳 신고 — 서버에 `kind = "삭제"` 로 보내고 내 화면은 바로 숨긴다.
     * 같은 자리에서 서로 다른 기기 5명 이상이 신고하면 모든 기사님 화면에서도 숨겨진다.
     */
    private fun confirmReportRemoved(spot: Hotspot) {
        if (!spot.hasLocation) {
            Dialogs.confirm(
                activity,
                "없어진 곳 신고",
                "‘${spot.name}’은(는) 위치 정보가 없어 다른 기사님께 알릴 수 없어요.\n" +
                    "내 화면에서만 숨길까요?",
                positive = "내 화면에서 숨기기",
                destructive = true
            ) {
                hideLocally(spot, "‘${spot.name}’ 숨김")
            }
            return
        }
        Dialogs.confirm(
            activity,
            "없어진 곳 신고",
            "‘${spot.name}’이(가) 없어졌나요?\n\n" +
                "같은 자리에서 5명 이상 신고하면 모든 기사님 화면에서 이 콜밭이 숨겨져요.\n" +
                "신고하면 내 화면에서는 바로 사라집니다.",
            positive = "신고하고 숨기기",
            destructive = true
        ) {
            hideLocally(spot, null)
            SpotCloud.reportRemoved(
                activity, spot.name, spot.lat, spot.lng, address = spot.area
            ) { _, msg -> activity.toast(msg) }
        }
    }

    private fun confirmRestoreHidden() {
        val count = hiddenIds.size
        if (count == 0) {
            if (cloudState.removed.isNotEmpty()) {
                activity.toast("기사님들이 없어진 곳으로 신고한 자리라 되돌릴 수 없어요")
            }
            return
        }
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
        AddSpotDialog.show(activity) { spot ->
            // 방금 추가한 콜밭이 바로 보이도록 검색·지역 필터를 ‘전체’로 돌리고 맨 위로
            searchQuery = ""
            binding.spotSearch.setText("")
            cityFilter = null
            // 좌표가 있으면 서버에도 제보해 다른 기사님과 나눈다 (같은 자리 5명 이상이면 공용 콜밭)
            val lat = spot.lat
            val lng = spot.lng
            if (lat != null && lng != null) {
                SpotCloud.submit(
                    activity,
                    spot.name,
                    lat,
                    lng,
                    address = spot.area,
                    note = spot.memo
                ) { ok, msg -> if (ok) activity.toast(msg) }
            }
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
            cloudState.added.isNotEmpty() -> "콜밭 ${list.size}곳 · 기사님 제보 ${cloudState.added.size}곳"
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

            // 출처 배지 — 기본 콜밭은 배지 없이, 기사 추천·기사 제보·내 콜밭만 표시
            when {
                hotspot.mine -> {
                    b.spotBadge.visibility = View.VISIBLE
                    b.spotBadge.text = "내 콜밭"
                    b.spotBadge.setBackgroundResource(R.drawable.bg_pill_accent)
                    b.spotBadge.setTextColor(activity.color(R.color.accent))
                }
                hotspot.source == Hotspots.SOURCE_CLOUD -> {
                    b.spotBadge.visibility = View.VISIBLE
                    b.spotBadge.text = Hotspots.SOURCE_CLOUD
                    b.spotBadge.setBackgroundResource(R.drawable.bg_tag_blue)
                    b.spotBadge.setTextColor(activity.color(R.color.chart_bar))
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

            // 기사님 제보 콜밭은 `기사 N명 제보 · N시간 전` 줄을 붙인다
            val reportMeta = cloudMeta(hotspot)
            if (reportMeta.isBlank()) {
                b.spotReportMeta.visibility = View.GONE
            } else {
                b.spotReportMeta.visibility = View.VISIBLE
                b.spotReportMeta.text = reportMeta
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

    /** 기사님 제보 콜밭 카드의 한 줄 — `기사 5명 제보 · 10분 전` (제보 콜밭이 아니면 빈 문자열) */
    private fun cloudMeta(hotspot: Hotspot): String {
        if (hotspot.reporters <= 0) return ""
        val time = timeAgo(hotspot.lastAt)
        return if (time.isBlank()) "기사 ${hotspot.reporters}명 제보"
        else "기사 ${hotspot.reporters}명 제보 · $time"
    }

    /** 마지막 제보 시각 -> `방금`·`N분 전`·`N시간 전`·`N일 전` */
    private fun timeAgo(millis: Long): String {
        if (millis <= 0) return ""
        val diff = System.currentTimeMillis() - millis
        if (diff < 0) return "방금"
        return when {
            diff < 60_000L -> "방금"
            diff < 3_600_000L -> "${diff / 60_000L}분 전"
            diff < 86_400_000L -> "${diff / 3_600_000L}시간 전"
            else -> "${diff / 86_400_000L}일 전"
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
