#!/usr/bin/env python3
"""콜밭 지도(SpotsMapPage) 의 setSpots 가 핀을 제대로 찍는지 실제 JavaScript 로 검사한다.

v1.0.21 버그: 앱이 `setSpots([{...},{...}])` 처럼 **배열 객체**를 넘겼는데
JS 가 무조건 `JSON.parse(json)` 을 해 `"[object Object]"` → SyntaxError → `arr = []` 로
핀이 0개 찍혔다. 이 검사는 SpotsMapPage.kt 의 HTML 템플릿에서 스크립트를 그대로 떼어 내
가짜 kakao.maps · document 를 붙인 뒤, 앱이 실제로 보내는 두 가지 모양으로 호출해
`pins.length === 200` 인지 확인한다.

    # 준비 (한 번)
    pip install quickjs
    # 실행
    python3 tools/map-pin-check/check_pins.py

마지막 줄이 `ALL PIN CHECKS PASSED` 이면 통과다. (Node 가 있으면 같은 아이디어로 Node 로 돌려도 된다.)
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
KT = ROOT / "app/src/main/java/com/youngsu/daeriledger/ui/spots/SpotsMapPage.kt"

failures = 0


def expect(name, actual, expected):
    global failures
    if actual == expected:
        print(f"PASS  {name} = {actual}")
    else:
        failures += 1
        print(f"FAIL  {name} = {actual!r} (expected {expected!r})")


def template_html() -> str:
    """SpotsMapPage.kt 의 TEMPLATE 원본 문자열을 그대로 꺼내 키만 바꾼다."""
    src = KT.read_text(encoding="utf-8")
    m = re.search(r'private val TEMPLATE = """(.*?)"""\.trimIndent\(\)', src, re.S)
    if not m:
        sys.exit("SpotsMapPage.kt 에서 TEMPLATE 를 찾지 못했습니다")
    html = m.group(1)
    return (
        html.replace("__KEY__", "TESTKEY123")
        .replace("__LAT__", "37.500000")
        .replace("__LNG__", "127.000000")
        .replace("__LEVEL__", "5")
    )


def first_script(html: str) -> str:
    """외부 src 가 없는 첫 <script> 블록 — setSpots 등 지도 로직이 들어 있다."""
    for m in re.finditer(r"<script(?![^>]*\bsrc=)[^>]*>(.*?)</script>", html, re.S):
        body = m.group(1)
        if "function setSpots" in body:
            return body
    sys.exit("HTML 템플릿에서 setSpots 스크립트를 찾지 못했습니다")


STUBS = r"""
var _overlays = [];   // 지도에 올라간 CustomOverlay 들 (setMap(null) 이면 내려간 것으로 본다)
var _bridgeCalls = [];
var Bridge = {
  onReady: function () { _bridgeCalls.push(['ready']); },
  onError: function (m) { _bridgeCalls.push(['error', String(m)]); },
  onSpotClick: function (i) { _bridgeCalls.push(['spot', Number(i)]); },
  onMapClick: function () { _bridgeCalls.push(['map']); }
};
function _el() {
  return {
    className: '', style: {}, children: [], _attrs: {}, _listeners: {},
    setAttribute: function (k, v) { this._attrs[k] = v; },
    getAttribute: function (k) { return this._attrs[k]; },
    addEventListener: function (k, fn) { this._listeners[k] = fn; },
    appendChild: function (c) { this.children.push(c); }
  };
}
var document = {
  _byId: {},
  getElementById: function (id) { return this._byId[id] || (this._byId[id] = _el()); },
  createElement: function () { return _el(); }
};
var window = { addEventListener: function () {} };
var _timers = 0;
function setTimeout(fn, ms) { _timers++; return _timers; }   // 타이머는 돌리지 않는다
var kakao = { maps: {
  LatLng: function (lat, lng) { this.lat = lat; this.lng = lng; },
  Map: function (box, opts) {
    this._box = box; this._center = opts.center; this._level = opts.level;
    this.relayout = function () {}; this.setCenter = function (c) { this._center = c; };
    this.getLevel = function () { return this._level; };
    this.setLevel = function (l) { this._level = l; };
    this.setDraggable = function () {}; this.setZoomable = function () {};
  },
  CustomOverlay: function (opts) {
    this.opts = opts; this.content = opts.content; this._map = null;
    this.setMap = function (m) { this._map = m; if (m) _overlays.push(this); };
  },
  event: { addListener: function () {} }
} };
function _livePins() { var n = 0; for (var i = 0; i < _overlays.length; i++) if (_overlays[i]._map) n++; return n; }
"""


def run_case(ctx, label, call_source, expected_pins, expect_click_index=None):
    """call_source 를 실행하고 살아 있는 핀 개수를 돌려준다."""
    ctx.eval("_overlays = [];")
    ctx.eval(call_source)
    pins = ctx.eval("_livePins()")
    expect(label, pins, expected_pins)
    if expect_click_index is not None and expected_pins > 0:
        # 첫 핀을 누르면 앱에 그 인덱스가 그대로 간다
        ctx.eval(
            "var el = _overlays[0].content;"
            "el._listeners['click']({stopPropagation: function(){}});"
        )
        calls = ctx.eval("JSON.stringify(_bridgeCalls)")
        expect(
            label + " · 핀 클릭 → onSpotClick",
            json.loads(calls)[-1],
            ["spot", expect_click_index],
        )


def main():
    import quickjs  # pip install quickjs

    html = template_html()
    script = first_script(html)

    spots = [
        {"i": i, "lat": 37.5 + (i % 20) * 0.001, "lng": 127.0 + (i // 20) * 0.001, "mine": i % 7 == 0}
        for i in range(200)
    ]
    array_text = json.dumps(spots, separators=(",", ":"), ensure_ascii=False)

    ctx = quickjs.Context()
    ctx.eval(STUBS)
    try:
        ctx.eval(script)
    except Exception as e:  # noqa: BLE001 — 스크립트 문법 오류도 실패로 보고
        sys.exit(f"지도 스크립트를 읽지 못했습니다: {e}")

    # 지도가 아직 없을 때 들어온 목록은 init 에서 찍혀야 한다
    ctx.eval("setSpots(" + array_text + ")")
    expect("init 전 setSpots → 대기", ctx.eval("_livePins()"), 0)
    ctx.eval("init()")
    expect("init → ready 브리지", json.loads(ctx.eval("JSON.stringify(_bridgeCalls)"))[0], ["ready"])
    expect("init 후 대기 목록 200핀", ctx.eval("_livePins()"), 200)

    # 앱이 실제로 보내는 두 가지 모양
    run_case(ctx, "배열 객체 setSpots([...])", "setSpots(" + array_text + ")", 200, 0)
    run_case(
        ctx,
        "문자열 setSpots(\"[...]\")",
        "setSpots(" + json.dumps(array_text) + ")",
        200,
    )

    # 다시 부르면 이전 핀은 지도에서 내려간다 (쌓이지 않는다)
    ctx.eval("setSpots(" + array_text + ")")
    ctx.eval("setSpots(" + json.dumps(json.dumps(spots[:3], separators=(",", ":")), ensure_ascii=False) + ")")
    expect("갱신 시 이전 핀 정리", ctx.eval("_livePins()"), 3)

    # 빈 목록 · 잘못된 문자열 · null 좌표
    ctx.eval("setSpots(" + json.dumps("[]") + ")")
    expect("빈 목록", ctx.eval("_livePins()"), 0)
    ctx.eval("setSpots(" + json.dumps("[{oops") + ")")
    expect("깨진 JSON 문자열", ctx.eval("_livePins()"), 0)
    ctx.eval("setSpots(null)")
    expect("null", ctx.eval("_livePins()"), 0)
    ctx.eval("setSpots([{i:0,lat:0,lng:0,mine:false},{i:1,lat:37.5,lng:127.0,mine:true}])")
    expect("0,0 좌표는 건너뛴다", ctx.eval("_livePins()"), 1)

    # 핀 모양 (CSS) — 카카오맵 위에서 보이게
    pin_css = re.search(r"\.pin\s*\{([^}]*)\}", html, re.S).group(1)
    expect("pin display:block", "display:block" in pin_css.replace(" ", ""), True)
    expect("pin box-sizing", "box-sizing:border-box" in pin_css.replace(" ", ""), True)
    expect("pin 18px", "width:18px" in pin_css.replace(" ", "") and "height:18px" in pin_css.replace(" ", ""), True)
    expect("pin 흰 테두리", "BORDER:2PXSOLID#FFFFFF" in pin_css.replace(" ", "").upper(), True)
    expect("pin 가운데 점", ".pin::after" in html, True)
    expect("mine 앰버", "#FFB020" in html, True)
    expect("basic 민트", "#2FBFB0" in html, True)

    # 🔥 핫스팟 핀 (v1.0.26) — 운행 GPS로 모인 자리는 코랄 레드 클래스로 찍힌다
    ctx.eval("_overlays = []; _bridgeCalls = [];")
    ctx.eval(
        "setSpots([{i:0,lat:37.5,lng:127.0,mine:false,hot:true},"
        "{i:1,lat:37.6,lng:127.1,mine:false,hot:false},"
        "{i:2,lat:37.7,lng:127.2,mine:true,hot:true}])"
    )
    expect("핫스팟 핀 클래스", ctx.eval("_overlays[0].content.className"), "pin hot")
    expect("일반 핀 클래스", ctx.eval("_overlays[1].content.className"), "pin basic")
    expect("내 콜밭 핀이 핫스팟보다 우선", ctx.eval("_overlays[2].content.className"), "pin mine")
    expect("핫스팟 zIndex", ctx.eval("_overlays[0].opts.zIndex"), 3)
    expect("hot 코랄 CSS", ".pin.hot" in html and "#FF4D4F" in html, True)

    # 🟢 지금 활발한 핫스팟 (v1.0.29) — 핀 테두리가 빛나고 맨 위로 뜬다
    ctx.eval("_overlays = []; _bridgeCalls = [];")
    ctx.eval(
        "setSpots([{i:0,lat:37.5,lng:127.0,mine:false,hot:true,now:true},"
        "{i:1,lat:37.6,lng:127.1,mine:false,hot:true,now:false},"
        "{i:2,lat:37.7,lng:127.2,mine:false,hot:false,now:true}])"
    )
    expect("지금 활발 핀 클래스", ctx.eval("_overlays[0].content.className"), "pin hot now")
    expect("지금 한산 핫스팟 핀", ctx.eval("_overlays[1].content.className"), "pin hot")
    expect("지금 활발 핀 zIndex", ctx.eval("_overlays[0].opts.zIndex"), 6)
    expect("now 테두리 CSS", ".pin.now" in html and "rgba(47,191,176" in html, True)

    if failures:
        print(f"\n{failures} FAILED")
        sys.exit(1)
    print("\nALL PIN CHECKS PASSED")


if __name__ == "__main__":
    main()
