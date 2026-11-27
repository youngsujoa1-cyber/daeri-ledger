package com.youngsu.daeriledger.ui.spots

import java.util.Locale

/**
 * 콜밭 탭 위쪽 **콜밭 지도** 카드가 쓰는 카카오맵 문서.
 *
 *  - [KakaoMapPage](위치 고르기)와 같은 카카오 JavaScript SDK 를 쓰되, 이 문서는 **주소 찾기(Geocoder)를
 *    하지 않는다** — 목록 지도에서 지도를 움직일 때마다 주소 API 를 부르면 느리고 쓸모도 없다.
 *  - 앱이 `setSpots(json)` 으로 콜밭 좌표를 넘기면 **핀(CustomOverlay)** 으로 찍는다. 핀은 DOM 한 칸이라
 *    카카오 `Marker` 이미지보다 가벼고, **내 콜밭·기사님 직접 제보는 노란색 · 일반 콜밭은 민트 · 운행 기록 GPS 핫스팟은 빨간색**으로 구분한다.
 *  - 핀을 누르면 `Bridge.onSpotClick(index)` 로 앱에 알리고, 앱이 이름·피크·메모 카드를 띄운다.
 *    빈 지도를 누르면 `Bridge.onMapClick()` 으로 카드를 닫는다.
 *  - 키는 `BuildConfig.KAKAO_JS_KEY`(local.properties)로만 들어오고, 문서는 카카오 콘솔에 등록한
 *    도메인([KakaoMapPage.BASE_URL])을 기준 주소로 열어 SDK 도메인 인증을 통과한다.
 *  - SDK 로드 실패·도메인 미등록·시간 초과는 `Bridge.onError` 로 알려 앱이 안내와 `다시 시도`를 띄운다.
 */
internal object SpotsMapPage {

    /** 핀을 한눈에 보기 좋은 정도(1=가까이 … 14=넓게). 5는 반경 2km 남짓이 한 화면에 들어온다. */
    const val DEFAULT_LEVEL = 5

    fun html(key: String, lat: Double, lng: Double, level: Int = DEFAULT_LEVEL): String {
        val safeKey = key.filter { it.isLetterOrDigit() }
        return TEMPLATE
            .replace("__KEY__", safeKey)
            .replace("__LAT__", String.format(Locale.US, "%.6f", lat))
            .replace("__LNG__", String.format(Locale.US, "%.6f", lng))
            .replace("__LEVEL__", level.toString())
    }

    private val TEMPLATE = """
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>콜밭 지도</title>
<style>
  html, body { margin:0; padding:0; width:100%; height:100%; overflow:hidden; background:#E9EEF3; -webkit-tap-highlight-color:transparent; }
  #map { position:absolute; left:0; top:0; width:100%; height:100%; }

  /* 콜밭 핀 — 아래로 뾰족한 물방울 모양 (내 콜밭·직접 제보 노란색 · 일반 민트 · 🔥 핫스팟 빨간색)
     카카오맵 위에서 한눈에 보이도록 18px · 흰 테두리 · 가운데 흰 점 */
  .pin {
    display:block; box-sizing:border-box;
    position:relative; width:18px; height:18px;
    border:2px solid #FFFFFF; border-radius:50% 50% 50% 0;
    transform:rotate(-45deg); transform-origin:50% 50%;
    box-shadow:0 1px 4px rgba(0,0,0,0.45); cursor:pointer;
  }
  /* 가운데 흰 점 — 앰버·민트 어느 쪽이든 점이 또렷하다 (회전해도 가운데라 그대로) */
  .pin::after {
    content:''; position:absolute; left:50%; top:50%;
    width:6px; height:6px; margin:-3px 0 0 -3px;
    border-radius:50%; background:#FFFFFF;
  }
  .pin.mine { background:#FFB020; }
  /* 🟡 기사님이 `+ 콜밭 추가` 로 직접 제보해 전체에 올라온 콜밭 — 내 콜밭과 같은 노란색 (v1.0.30) */
  .pin.direct { background:#FFB020; }
  .pin.basic { background:#2FBFB0; }
  /* 🔥 운행 기록 GPS(반경 500m·3명 이상)로 모인 핫스팟 — 빨간색 (v1.0.26 → v1.0.30) */
  .pin.hot { background:#FF4D4F; }
  /* 🟢 지금 시간대(±1시간)에 콜이 모인 핫스팟 — 민트 테두리가 빛난다 (v1.0.29) */
  .pin.now {
    width:22px; height:22px; border-width:3px;
    box-shadow:0 0 0 5px rgba(47,191,176,0.45), 0 1px 6px rgba(0,0,0,0.5);
  }
  .pin.now::after { width:7px; height:7px; margin:-3.5px 0 0 -3.5px; }

  /* 내 위치 — 파란 점 + 옅은 원 */
  .me {
    width:14px; height:14px; border-radius:50%;
    background:#2E7DF6; border:3px solid #FFFFFF;
    box-shadow:0 0 0 7px rgba(46,125,246,0.22);
  }

  #err {
    display:none; position:absolute; left:16px; right:16px; top:50%; transform:translateY(-50%);
    background:#FFFFFF; color:#111A24; border-radius:14px; padding:16px;
    font:13px/1.6 -apple-system, "Noto Sans KR", "Malgun Gothic", sans-serif; text-align:center;
    box-shadow:0 6px 20px rgba(0,0,0,0.18);
  }
</style>
</head>
<body>
<div id="map"></div>
<div id="err">카카오맵을 불러오지 못했어요.<br>잠시 뒤 다시 시도해 주세요.</div>

<script>
  var map = null, ready = false, failed = false, bootTries = 0;
  var pins = [], me = null, pendingSpots = null;

  function jReady() { try { Bridge.onReady(); } catch (e) {} }
  function jError(m) { try { Bridge.onError(String(m)); } catch (e) {} }
  function jSpot(i) { try { Bridge.onSpotClick(Number(i)); } catch (e) {} }
  function jMapClick() { try { Bridge.onMapClick(); } catch (e) {} }

  function fail(m) {
    if (failed) return;
    failed = true;
    var box = document.getElementById('err');
    if (box) box.style.display = 'block';
    jError(m || 'error');
  }

  function clearPins() {
    for (var i = 0; i < pins.length; i++) { try { pins[i].setMap(null); } catch (e) {} }
    pins = [];
  }

  /**
   * 앱이 넘긴 콜밭 좌표를 핀으로 찍는다. json = [{i, lat, lng, mine, direct, hot, now}]
   *  - 앱에서 이미 걸러 둔 목록(지역·검색·숨김)만 넘어온다 — 지도는 그리기만 한다.
   *  - `mine`(내 콜밭)과 `direct`(기사님 직접 제보)는 노란 핀, `hot`(운행 GPS 핫스팟)은 빨간 핀,
   *    나머지 기본 콜밭은 민트 핀이다 (v1.0.30).
   *  - `now`(지금 활발한 핫스팟)는 핀을 조금 크게 하고 민트 테두리를 빛나게 해 한눈에 띄게 한다.
   *  - 갱신할 때마다 이전 핀은 지도에서 떼어 낸다(쌓이지 않는다).
   *  - 넘어오는 모양은 **배열 그대로**와 **JSON 문자열** 둘 다 받는다.
   *    (`evaluateJavascript("setSpots([...])")` 는 배열 객체, `setSpots("[...]")` 는 문자열.
   *     v1.0.21 은 배열에 무조건 JSON.parse 를 해 "[object Object]" 가 되어 핀이 하나도 안 찍혔다.)
   */
  function setSpots(json) {
    var arr;
    if (Array.isArray(json)) {
      arr = json;
    } else if (typeof json === 'string') {
      try { arr = JSON.parse(json) || []; } catch (e) { arr = []; }
    } else {
      arr = [];
    }
    if (!Array.isArray(arr)) arr = [];
    // 지도가 아직 만들어지기 전에 들어오면 기억해 두었다가 init 에서 찍는다
    if (!map) { pendingSpots = arr; return; }
    pendingSpots = null;
    clearPins();
    for (var k = 0; k < arr.length; k++) {
      (function (s) {
        var lat = Number(s.lat), lng = Number(s.lng);
        if (!isFinite(lat) || !isFinite(lng) || (lat === 0 && lng === 0)) return;
        var el = document.createElement('div');
        el.className = 'pin ' + (s.mine ? 'mine' : (s.direct ? 'direct' : (s.hot ? 'hot' : 'basic'))) + (s.now ? ' now' : '');
        el.setAttribute('data-i', String(s.i));
        el.addEventListener('click', function (ev) {
          if (ev && ev.stopPropagation) ev.stopPropagation();
          jSpot(s.i);
        });
        var ov = new kakao.maps.CustomOverlay({
          position: new kakao.maps.LatLng(lat, lng),
          content: el,
          xAnchor: 0.5,
          yAnchor: 1,
          zIndex: s.now ? 6 : ((s.mine || s.direct) ? 4 : (s.hot ? 3 : 2))
        });
        ov.setMap(map);
        pins.push(ov);
      })(arr[k]);
    }
  }

  /** 내 위치 파란 점 (위치를 모르면 null 을 넘겨 지운다) */
  function setMe(lat, lng) {
    if (!map) return;
    if (me) { try { me.setMap(null); } catch (e) {} me = null; }
    if (lat === null || lng === null) return;
    var a = Number(lat), b = Number(lng);
    if (!isFinite(a) || !isFinite(b) || (a === 0 && b === 0)) return;
    var el = document.createElement('div');
    el.className = 'me';
    me = new kakao.maps.CustomOverlay({
      position: new kakao.maps.LatLng(a, b),
      content: el,
      xAnchor: 0.5,
      yAnchor: 0.5,
      zIndex: 5
    });
    me.setMap(map);
  }

  function moveTo(lat, lng, level) {
    if (!map) return;
    var a = Number(lat), b = Number(lng);
    if (!isFinite(a) || !isFinite(b)) return;
    if (level) map.setLevel(Number(level));
    map.setCenter(new kakao.maps.LatLng(a, b));
  }

  function zoomBy(delta) {
    if (!map) return;
    var lv = map.getLevel() + Number(delta || 0);
    if (lv < 1) lv = 1;
    if (lv > 14) lv = 14;
    map.setLevel(lv);
  }

  function relayout() {
    if (map) { try { map.relayout(); } catch (e) {} }
  }

  function init() {
    var box = document.getElementById('map');
    map = new kakao.maps.Map(box, {
      center: new kakao.maps.LatLng(__LAT__, __LNG__),
      level: __LEVEL__
    });
    map.setDraggable(true);
    map.setZoomable(true);
    // 빈 지도를 누르면 앱이 핀 카드를 닫는다
    kakao.maps.event.addListener(map, 'click', function () { jMapClick(); });
    ready = true;
    if (pendingSpots) setSpots(pendingSpots);
    jReady();
  }

  function boot() {
    if (failed) return;
    if (typeof kakao === 'undefined' || !kakao.maps || typeof kakao.maps.Map === 'undefined') {
      if (bootTries++ < 40) { setTimeout(boot, 120); return; }
      fail('sdk-missing');
      return;
    }
    try { init(); } catch (e) { fail(e && e.message ? e.message : 'init'); }
  }

  setTimeout(function () { if (!ready) fail('timeout'); }, 8000);

  window.addEventListener('resize', function () { relayout(); });
</script>

<script src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=__KEY__"
        onerror="fail('sdk-load')"></script>
<script>boot();</script>
</body>
</html>
""".trimIndent()
}

/**
 * 콜밭 지도 WebView 안의 JavaScript 가 앱으로 넘기는 통로(`window.Bridge`).
 *  - `@JavascriptInterface` 는 WebView 의 JavaScript 스레드에서 불리므로 모두 [main] 핸들러로 넘긴다.
 *  - 핀 인덱스는 앱이 넘긴 목록의 순서라 범위를 앱에서 다시 확인한다.
 */
class SpotsMapBridge(
    private val main: android.os.Handler,
    private val ready: () -> Unit,
    private val spotClick: (index: Int) -> Unit,
    private val mapClick: () -> Unit,
    private val error: (message: String) -> Unit
) {

    @android.webkit.JavascriptInterface
    fun onReady() {
        main.post { runCatching { ready() } }
    }

    @android.webkit.JavascriptInterface
    fun onSpotClick(index: Int) {
        if (index < 0) return
        main.post { runCatching { spotClick(index) } }
    }

    @android.webkit.JavascriptInterface
    fun onMapClick() {
        main.post { runCatching { mapClick() } }
    }

    @android.webkit.JavascriptInterface
    fun onError(message: String?) {
        val text = message.orEmpty().replace('\n', ' ').trim().take(80).ifBlank { "error" }
        main.post { runCatching { error(text) } }
    }
}
