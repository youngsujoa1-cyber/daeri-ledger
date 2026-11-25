package com.youngsu.daeriledger.ui.spots

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.daeriledger.BuildConfig
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
import com.youngsu.daeriledger.databinding.ScreenSpotsBinding
import com.youngsu.daeriledger.location.LocationHelper
import com.youngsu.daeriledger.ui.MainActivity
import com.youngsu.daeriledger.ui.Screen
import com.youngsu.daeriledger.ui.widget.SpotMapView
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.Dialogs
import com.youngsu.daeriledger.util.color
import com.youngsu.daeriledger.util.dp
import com.youngsu.daeriledger.util.hideKeyboard
import com.youngsu.daeriledger.util.setChips
import com.youngsu.daeriledger.util.toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
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

        /** 지도에 한꺼번에 찍는 핀 수 — 1,200곳을 다 올리면 지도가 버벅인다 */
        const val MAP_MAX_PINS = 200
        const val MAP_TIMEOUT_MS = 9_000L
        const val MAP_HEIGHT_DP = 240f
        const val MAP_HEIGHT_BIG_DP = 460f
    }
    private var userLocation: Location? = null
    private var locating = false
    private var savedSpots: List<MySpot> = emptyList()
    private var spotStats: List<SpotStat> = emptyList()

    // --- 콜밭 지도 (카카오맵 WebView) ---
    private var mapConfigured = false
    private var mapStarted = false
    private var mapReady = false
    private var mapExpanded = false
    private var mapCenteredOnMe = false
    /** 지도에 지금 찍혀 있는 핀 목록 — 핀 인덱스로 콜밭을 찾는다 */
    private var mapSpots: List<Hotspot> = emptyList()
    private var mapPinTarget: Hotspot? = null
    private val mapHandler = Handler(Looper.getMainLooper())
    private val mapPushJob = Runnable { pushMapSpots() }
    private val mapTimeoutJob = Runnable { showMapError() }

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
                centerMapOnFilter()
                true
            } else {
                false
            }
        }
        setupMap()
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
        renderNearby()
        updateLocationCard()
        maybeLocate()
        // 지도는 처음 한 번만 띄우고, 이후에는 핀만 새로 찍는다
        loadMap()
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
            onMapLocation(location)
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
                    onMapLocation(location)
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
            // 지역을 골랐으면 지도도 그 지역으로 — 안 그러면 핀이 화면 밖에 있다
            centerMapOnFilter()
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

    /** 카드를 길게 눌렀을 때 — 누를 수 있는 카드 버튼으로 내 화면에서 숨기기 · 없어진 곳 신고 · 내 콜밭 삭제 */
    private fun showSpotActions(spot: Hotspot) {
        val container = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_spot_actions, null, false)
        val title = container.findViewById<TextView>(R.id.actionTitle)
        val subtitle = container.findViewById<TextView>(R.id.actionSubtitle)
        val hideAction = container.findViewById<View>(R.id.hideAction)
        val reportAction = container.findViewById<View>(R.id.reportAction)
        val deleteAction = container.findViewById<View>(R.id.deleteAction)
        val closeAction = container.findViewById<View>(R.id.closeAction)

        title.text = spot.name
        val sub = listOfNotNull(
            spot.area.takeIf { it.isNotBlank() },
            spot.source.takeIf { it.isNotBlank() },
            if (spot.mine) "내 콜밭" else null
        ).joinToString(" · ")
        if (sub.isBlank()) {
            subtitle.visibility = View.GONE
        } else {
            subtitle.visibility = View.VISIBLE
            subtitle.text = sub
        }
        deleteAction.visibility = if (spot.mine) View.VISIBLE else View.GONE

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(container)
            .create()

        hideAction.setOnClickListener {
            dialog.dismiss()
            confirmHide(spot)
        }
        reportAction.setOnClickListener {
            dialog.dismiss()
            confirmReportRemoved(spot)
        }
        deleteAction.setOnClickListener {
            dialog.dismiss()
            confirmDeleteMySpot(spot)
        }
        closeAction.setOnClickListener { dialog.dismiss() }
        dialog.show()
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

    // ------------------------------------------------------------- 콜밭 지도

    /**
     * 지도 카드 — 카카오맵 WebView 를 준비한다. (`KAKAO_JS_KEY` 가 없는 빌드에서는 카드 자체를 숨김)
     *  - 지도 위에서 드래그·줌할 때 바깥 `NestedScrollView` 가 터치를 가로채지 않도록 막는다
     *  - 카카오맵을 못 띄우면 안내와 `다시 시도`를 보여 줌 — 목록·앱은 그대로
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMap() {
        if (BuildConfig.KAKAO_JS_KEY.isBlank()) {
            binding.mapCard.visibility = View.GONE
            return
        }
        binding.mapWeb.settings.apply {
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
        binding.mapWeb.setBackgroundColor(activity.color(R.color.bg))
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        binding.mapWeb.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) showMapError()
            }
        }
        binding.mapWeb.addJavascriptInterface(
            SpotsMapBridge(
                main = mapHandler,
                ready = { onMapReady() },
                spotClick = { index -> onMapPinTapped(index) },
                mapClick = { binding.mapPinCard.visibility = View.GONE },
                error = { showMapError() }
            ),
            "Bridge"
        )
        // 지도를 만질 때 바깥 스크롤이 가로채지 않게 (ACTION_DOWN 에서 바로 막아야 드래그·줌이 산다)
        binding.mapWeb.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    binding.spotsScroll.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    binding.spotsScroll.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        binding.mapToggle.setOnClickListener { toggleMapSize() }
        binding.mapMyLocation.setOnClickListener { centerMapOnMe() }
        binding.mapRetryButton.setOnClickListener { loadMap(force = true) }
        binding.mapPinClose.setOnClickListener { binding.mapPinCard.visibility = View.GONE }
        binding.mapPinRoute.setOnClickListener {
            mapPinTarget?.let { navigate(it.name, it.lat, it.lng) }
        }
        mapConfigured = true
    }

    /** 지도 페이지를 (다시) 띄운다 — 처음 한 번만 불러오고, `다시 시도`일 때만 다시 부른다 */
    private fun loadMap(force: Boolean = false) {
        if (!mapConfigured) return
        if (mapStarted && !force) return
        mapStarted = true
        mapReady = false
        binding.mapWeb.visibility = View.VISIBLE
        binding.mapLoading.visibility = View.VISIBLE
        binding.mapErrorBox.visibility = View.GONE
        binding.mapPinCard.visibility = View.GONE
        val lat = userLocation?.latitude
            ?: filteredHotspots.firstOrNull { it.hasLocation }?.lat
            ?: SpotMapView.DEFAULT_LAT
        val lng = userLocation?.longitude
            ?: filteredHotspots.firstOrNull { it.hasLocation }?.lng
            ?: SpotMapView.DEFAULT_LNG
        binding.mapWeb.loadDataWithBaseURL(
            KakaoMapPage.BASE_URL,
            SpotsMapPage.html(BuildConfig.KAKAO_JS_KEY, lat, lng, SpotsMapPage.DEFAULT_LEVEL),
            "text/html",
            "UTF-8",
            null
        )
        mapHandler.removeCallbacks(mapTimeoutJob)
        mapHandler.postDelayed(mapTimeoutJob, MAP_TIMEOUT_MS)
    }

    private fun onMapReady() {
        mapReady = true
        mapHandler.removeCallbacks(mapTimeoutJob)
        binding.mapLoading.visibility = View.GONE
        binding.mapErrorBox.visibility = View.GONE
        binding.mapWeb.visibility = View.VISIBLE
        pushMapSpots()
        userLocation?.let { loc ->
            setMapMe(loc.latitude, loc.longitude)
            if (!mapCenteredOnMe) {
                mapCenteredOnMe = true
                moveMap(loc.latitude, loc.longitude)
            }
        }
    }

    /** 카카오맵을 못 띄웠다 — 앱은 멈추지 않고 안내와 `다시 시도`만 보여 준다 */
    private fun showMapError() {
        mapHandler.removeCallbacks(mapTimeoutJob)
        if (mapReady) return
        binding.mapLoading.visibility = View.GONE
        binding.mapWeb.visibility = View.GONE
        binding.mapPinCard.visibility = View.GONE
        binding.mapErrorBox.visibility = View.VISIBLE
        binding.mapCountLabel.text = ""
    }

    /** 필터·검색이 바뀔 때마다 핀을 다시 계산한다 (연속 입력·필터 탭을 묶어서 한 번에) */
    private fun scheduleMapPush() {
        if (!mapConfigured || !mapStarted) return
        mapHandler.removeCallbacks(mapPushJob)
        mapHandler.postDelayed(mapPushJob, 180)
    }

    /** 지금 목록(지역 칩·검색·숨김 반영)의 콜밭을 지도에 핀으로 찍는다 */
    private fun pushMapSpots() {
        if (!mapReady) return
        val spots = mapPinsFor(filteredHotspots)
        mapSpots = spots
        binding.mapCountLabel.text = "${spots.size}곳"
        val array = JSONArray()
        spots.forEachIndexed { index, spot ->
            array.put(
                JSONObject()
                    .put("i", index)
                    .put("lat", spot.lat)
                    .put("lng", spot.lng)
                    .put("mine", spot.mine)
            )
        }
        // 배열을 그대로 넣으면 JS 쪽에 배열 객체로 가고, 문자열로 넣으면 JSON.parse 가 받는다.
        // 어느 쪽이든 setSpots 가 처리하지만, 따옴표로 감싸 넘겨야 이름·따옴표가 섞여도 안전하다.
        binding.mapWeb.evaluateJavascript(
            "setSpots(${JSONObject.quote(array.toString())})",
            null
        )
        binding.mapPinCard.visibility = View.GONE
        mapPinTarget = null
    }

    /**
     * 지도에 올릴 콜밭 — 좌표가 있는 곳만, **최대 [MAP_MAX_PINS]곳**.
     * 목록이 이미 거리순으로 정렬되어 있으니(위치를 모를 땐 내 콜밭 먼저) 앞에서부터 집는다.
     */
    private fun mapPinsFor(list: List<Hotspot>): List<Hotspot> {
        val withCoords = list.filter { it.hasLocation }
        if (withCoords.size <= MAP_MAX_PINS) return withCoords
        val mine = withCoords.filter { it.mine }
        val others = withCoords.filterNot { it.mine }
        return mine + others.take((MAP_MAX_PINS - mine.size).coerceAtLeast(0))
    }

    /** 핀을 눌렀을 때 — 지도 아래쪽에 이름·피크·메모·길찾기 카드를 띄운다 */
    private fun onMapPinTapped(index: Int) {
        val spot = mapSpots.getOrNull(index) ?: return
        mapPinTarget = spot
        binding.mapPinName.text = spot.name
        val meta = listOfNotNull(
            spot.area.takeIf { it.isNotBlank() },
            if (spot.mine) "내 콜밭" else spot.source.takeIf { it.isNotBlank() },
            if (spot.hasPeak) "피크 ${spot.peak}" else null,
            distanceOf(spot)?.let { Hotspots.formatDistance(it) }
        ).joinToString(" · ")
        if (meta.isBlank()) {
            binding.mapPinMeta.visibility = View.GONE
        } else {
            binding.mapPinMeta.visibility = View.VISIBLE
            binding.mapPinMeta.text = meta
        }
        if (spot.tip.isBlank()) {
            binding.mapPinMemo.visibility = View.GONE
        } else {
            binding.mapPinMemo.visibility = View.VISIBLE
            binding.mapPinMemo.text = spot.tip
        }
        binding.mapPinCard.visibility = View.VISIBLE
    }

    /** 지도를 크게/작게 — 지도 영역 높이만 바꾸고 카카오맵에 다시 그리라고 알린다 */
    private fun toggleMapSize() {
        mapExpanded = !mapExpanded
        binding.mapFrame.layoutParams = binding.mapFrame.layoutParams.apply {
            height = activity.dp(if (mapExpanded) MAP_HEIGHT_BIG_DP else MAP_HEIGHT_DP)
        }
        binding.mapToggle.text = if (mapExpanded) "작게" else "크게"
        binding.mapWeb.evaluateJavascript("relayout()", null)
    }

    /** `내 위치` — 지금 위치를 다시 찾아 지도 중심과 파란 점을 옮긴다 */
    private fun centerMapOnMe() {
        if (!activity.hasLocationPermission()) {
            activity.requestLocationPermission()
            activity.toast("위치 권한을 허용한 뒤 ‘내 위치’를 다시 눌러 주세요")
            return
        }
        LocationHelper.request(activity) { location ->
            if (location == null) {
                activity.toast("지금 위치를 찾지 못했어요 — 지도를 직접 움직여 주세요")
                return@request
            }
            userLocation = location
            updateLocationCard()
            renderNearby()
            mapCenteredOnMe = true
            setMapMe(location.latitude, location.longitude)
            moveMap(location.latitude, location.longitude)
        }
    }

    /** 위치를 처음 찾았을 때 — 파란 점을 찍고, 아직 내 위치로 옮긴 적이 없으면 중심도 옮긴다 */
    private fun onMapLocation(location: Location?) {
        if (location == null || !mapReady) return
        setMapMe(location.latitude, location.longitude)
        if (!mapCenteredOnMe) {
            mapCenteredOnMe = true
            moveMap(location.latitude, location.longitude)
        }
    }

    private fun setMapMe(lat: Double, lng: Double) {
        if (!mapReady) return
        binding.mapWeb.evaluateJavascript(
            String.format(Locale.US, "setMe(%.6f, %.6f)", lat, lng),
            null
        )
    }

    private fun moveMap(lat: Double, lng: Double) {
        if (!mapReady) return
        binding.mapWeb.evaluateJavascript(
            String.format(Locale.US, "moveTo(%.6f, %.6f, %d)", lat, lng, SpotsMapPage.DEFAULT_LEVEL),
            null
        )
    }

    /** 지역 칩을 고르거나 검색을 마쳤을 때 — 지도를 그 결과의 첫 콜밭으로 옮긴다 */
    private fun centerMapOnFilter() {
        if (!mapReady || !mapStarted) return
        val first = filteredHotspots.firstOrNull { it.hasLocation } ?: return
        moveMap(first.lat, first.lng)
    }

    // ------------------------------------------------------ 콜밭 목록

    private fun renderNearby() {
        binding.nearbyColumn.removeAllViews()
        val list = filteredHotspots
        val query = searchQuery.trim()
        renderedList = list
        renderedCount = 0

        binding.nearbyCountText.text = "${list.size}곳"
        scheduleMapPush()
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
