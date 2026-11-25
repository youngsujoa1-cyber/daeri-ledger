package com.youngsu.daeriledger.ui.spots

import java.util.Locale

/**
 * 카카오맵 JavaScript SDK 를 담은 WebView 문서.
 *
 *  - 키는 `BuildConfig.KAKAO_JS_KEY`(local.properties)로만 들어온다 — 저장소·소스에는 남기지 않는다.
 *  - 문서를 **등록된 도메인**(`https://youngsujoa1-cyber.github.io`)을 기준 주소로 열어야
 *    카카오 SDK 의 JavaScript SDK 도메인 인증을 통과한다(`loadDataWithBaseURL` 의 baseUrl).
 *  - 지도는 드래그·핀치 줌이 그대로 되고, 앱의 `+`/`−`·`내 위치` 버튼은 [zoomBy]·[moveTo] 를
 *    `evaluateJavascript` 로 불러 조작한다.
 *  - 지도가 멈출 때(`idle`)마다 중심 위경도를, 카카오 `services.Geocoder` 로 주소를 찾으면
 *    도로명(건물명 포함)·시·도 단위 지역명을 `Bridge.onIdle` / `Bridge.onAddress` 로 넘긴다.
 *  - SDK 스크립트 로드 실패·도메인 미등록·시간 초과는 `Bridge.onError` 로 알려 앱이 OSM 지도로
 *    안전하게 폴백할 수 있게 한다(화면이 하얗게 멈추지 않는다).
 */
internal object KakaoMapPage {

    /** 카카오 콘솔에 등록한 JavaScript SDK 도메인의 문서 주소 — 기준 주소로만 쓴다. */
    const val BASE_URL = "https://youngsujoa1-cyber.github.io/daeri-ledger/kakaomap.html"

    /** 카카오맵 레벨(1=가까이 … 14=넓게). OSM 16줌과 비슷한 정도가 3이다. */
    const val DEFAULT_LEVEL = 3

    fun html(key: String, lat: Double, lng: Double, level: Int = DEFAULT_LEVEL): String {
        // 키는 영문·숫자만 남겨 HTML/JS 문자열에 안전하게 넣는다
        val safeKey = key.filter { it.isLetterOrDigit() }
        val html = TEMPLATE
            .replace("__KEY__", safeKey)
            .replace("__LAT__", String.format(Locale.US, "%.6f", lat))
            .replace("__LNG__", String.format(Locale.US, "%.6f", lng))
            .replace("__LEVEL__", level.toString())
        return html
    }

    private val TEMPLATE = """
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>카카오맵</title>
<style>
  html, body { margin:0; padding:0; width:100%; height:100%; overflow:hidden; background:#E9EEF3; -webkit-tap-highlight-color:transparent; }
  #map { position:absolute; left:0; top:0; width:100%; height:100%; }
  #err {
    display:none; position:absolute; left:16px; right:16px; top:50%; transform:translateY(-50%);
    background:#FFFFFF; color:#111A24; border-radius:14px; padding:18px 16px;
    font:14px/1.6 -apple-system, "Noto Sans KR", "Malgun Gothic", sans-serif;
    text-align:center; box-shadow:0 6px 20px rgba(0,0,0,0.18);
  }
</style>
</head>
<body>
<div id="map"></div>
<div id="err">카카오맵을 불러오지 못했어요.<br>잠시 뒤 다시 시도해 주세요.</div>

<script>
  var map = null, geocoder = null, seq = 0, ready = false, failed = false, bootTries = 0;

  function jReady() { try { Bridge.onReady(); } catch (e) {} }
  function jIdle(a, b) { try { Bridge.onIdle(a, b); } catch (e) {} }
  function jAddress(a, b, c, d) { try { Bridge.onAddress(a, b, c, d); } catch (e) {} }
  function jError(m) { try { Bridge.onError(String(m)); } catch (e) {} }

  function fail(m) {
    if (failed) return;
    failed = true;
    var box = document.getElementById('err');
    if (box) box.style.display = 'block';
    jError(m || 'error');
  }

  // 지도가 멈출 때마다 중심 좌표와 주소를 앱에 알린다
  function onIdle() {
    if (!map) return;
    var c = map.getCenter();
    var lat = c.getLat(), lng = c.getLng();
    jIdle(lat, lng);
    if (!geocoder) return;
    var mine = ++seq;
    try {
      geocoder.coord2Address(lng, lat, function (result, status) {
        if (mine !== seq) return;
        var full = '', area = '';
        try {
          if (status === kakao.maps.services.Status.OK && result && result.length > 0) {
            var r = result[0];
            var road = r.road_address;
            var jibun = r.address;
            if (road && road.address_name) {
              full = road.address_name;
              if (road.building_name) full += ' (' + road.building_name + ')';
            } else if (jibun && jibun.address_name) {
              full = jibun.address_name;
            }
            var base = jibun || road || {};
            var parts = [];
            if (base.region_1depth_name) parts.push(base.region_1depth_name);
            if (base.region_2depth_name) parts.push(base.region_2depth_name);
            if (base.region_3depth_name) parts.push(base.region_3depth_name);
            area = parts.join(' ');
          }
        } catch (e) {}
        jAddress(lat, lng, full, area);
      });
    } catch (e) {
      jAddress(lat, lng, '', '');
    }
  }

  // 앱의 확대(+)·축소(−) 버튼 — 애니메이션 없이 바로 옮겨 연속 탭도 빠뜨리지 않는다
  function zoomBy(delta) {
    if (!map) return;
    var lv = map.getLevel() + Number(delta || 0);
    if (lv < 1) lv = 1;
    if (lv > 14) lv = 14;
    map.setLevel(lv);
  }

  // 앱의 내 위치 버튼
  function moveTo(lat, lng, level) {
    if (!map) return;
    if (level) map.setLevel(Number(level));
    map.setCenter(new kakao.maps.LatLng(Number(lat), Number(lng)));
  }

  function init() {
    var box = document.getElementById('map');
    map = new kakao.maps.Map(box, {
      center: new kakao.maps.LatLng(__LAT__, __LNG__),
      level: __LEVEL__
    });
    map.setDraggable(true);
    map.setZoomable(true);
    geocoder = new kakao.maps.services.Geocoder();
    kakao.maps.event.addListener(map, 'idle', onIdle);
    ready = true;
    jReady();
    onIdle();
  }

  function boot() {
    if (failed) return;
    // SDK 로더는 본체(kakao.js)를 document.write 로 이어 부른다 — 잠시 기다렸다 다시 본다
    if (typeof kakao === 'undefined' || !kakao.maps || typeof kakao.maps.Map === 'undefined') {
      if (bootTries++ < 40) { setTimeout(boot, 120); return; }
      fail('sdk-missing');
      return;
    }
    try { init(); } catch (e) { fail(e && e.message ? e.message : 'init'); }
  }

  // SDK 가 응답하지 않을 때(도메인 미등록·네트워크 차단 등) 8초 뒤 앱에 알린다
  setTimeout(function () { if (!ready) fail('timeout'); }, 8000);

  window.addEventListener('resize', function () { if (map) map.relayout(); });
</script>

<script src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=__KEY__&libraries=services"
        onerror="fail('sdk-load')"></script>
<script>boot();</script>
</body>
</html>
""".trimIndent()
}
