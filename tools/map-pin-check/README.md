# 콜밭 지도 핀 검사 (`check_pins.py`)

`SpotsMapPage.kt` 의 HTML 템플릿에서 스크립트를 **그대로 떼어 내**, 가짜 `kakao.maps` · `document` 를 붙여
실제 JavaScript 로 `setSpots(...)` 를 돌려 보는 검사입니다. JVM·에뮬레이터 없이 핀이 몇 개 찍히는지 셉니다.

```bash
pip install quickjs
python3 tools/map-pin-check/check_pins.py   # 마지막 줄: ALL PIN CHECKS PASSED
```

확인하는 것:

- 앱이 보내는 **두 가지 모양** — 배열 객체 `setSpots([{...}])` 와 문자열 `setSpots("[{...}]")` — 모두 200곳 → **핀 200개**
- 지도(`init`) 전에 들어온 목록도 기다렸다 찍는지
- 다시 부르면 이전 핀이 지도에서 내려가는지(쌓이지 않는지)
- 빈 목록 · 깨진 JSON · `null` · `0,0` 좌표 처리, 핀 클릭이 `Bridge.onSpotClick(index)` 로 가는지
- `.pin` CSS (18px · `display:block` · `box-sizing:border-box` · 흰 2px 테두리 · 가운데 점 · 앰버/민트)

## v1.0.21 버그 (이 검사가 잡는 것)

앱이 `setSpots([...])` 를 **배열 객체**로 넘겼는데 JS 가 무조건 `JSON.parse(json)` 을 해
`"[object Object]"` → `SyntaxError` → `arr = []` 가 되어 **핀이 0개** 찍혔습니다.
그 코드로 돌리면 `FAIL  배열 객체 setSpots([...]) = 0 (expected 200)` 로 떨어집니다.
