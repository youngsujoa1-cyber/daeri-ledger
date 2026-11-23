# -*- coding: utf-8 -*-
"""콜밭 데이터 생성기.

1) tools/hotspot-data/spots_*.py 의 수도권 원본 + 기존 지방 58곳을 합친다.
2) `maps geocode` 로 좌표를 받아 캐시(tools/hotspot-data/geocode_cache.json)에 저장한다.
3) zone bbox · id 중복 · 300m 근접 중복을 검증한다.
4) Hotspots.kt 의 ALL 목록을 만들어 앱 소스에 쓴다.

사용: python3 tools/hotspot-data/build.py [--write]
  --write 없이 실행하면 검증 보고만 출력한다.
"""
import json
import os
import subprocess
import sys
import time
import importlib.util
from math import radians, sin, cos, atan2, sqrt

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CACHE = os.path.join(HERE, "geocode_cache.json")
KOTLIN = os.path.join(ROOT, "app/src/main/java/com/youngsu/daeriledger/data/Hotspots.kt")

# 구·시 단위 대략 경계 (lat_min, lat_max, lng_min, lng_max) — 좌표가 엉뚱한 동네로
# 지오코딩됐는지 잡기 위한 1차 검증용이다.
ZONES = {
    # 서울
    "yongsan": (37.512, 37.548, 126.938, 127.020),
    "jung": (37.545, 37.574, 126.966, 127.024),
    "jongno": (37.556, 37.614, 126.948, 127.022),
    "gangnam": (37.470, 37.545, 127.010, 127.110),
    "seocho": (37.460, 37.522, 126.968, 127.045),
    "songpa": (37.465, 37.528, 127.075, 127.155),
    "gangdong": (37.518, 37.576, 127.103, 127.162),
    "seongdong": (37.526, 37.568, 127.008, 127.066),
    "gwangjin": (37.526, 37.560, 127.058, 127.102),
    "dongdaemun": (37.558, 37.601, 127.023, 127.072),
    "jungnang": (37.568, 37.616, 127.068, 127.112),
    "mapo": (37.530, 37.586, 126.878, 126.962),
    "seodaemun": (37.546, 37.586, 126.918, 126.972),
    "eunpyeong": (37.593, 37.646, 126.888, 126.947),
    "yeongdeungpo": (37.498, 37.546, 126.878, 126.947),
    "dongjak": (37.468, 37.526, 126.928, 126.992),
    "gwanak": (37.448, 37.500, 126.898, 126.972),
    "guro": (37.472, 37.520, 126.845, 126.917),
    "geumcheon": (37.453, 37.491, 126.868, 126.912),
    "yangcheon": (37.508, 37.546, 126.843, 126.892),
    "gangseo": (37.533, 37.582, 126.798, 126.867),
    "seongbuk": (37.573, 37.622, 126.993, 127.042),
    "gangbuk": (37.608, 37.652, 127.003, 127.047),
    "dobong": (37.633, 37.687, 127.018, 127.067),
    "nowon": (37.608, 37.692, 127.043, 127.097),
    # 경기
    "seongnam": (37.325, 37.500, 127.075, 127.175),
    "yongin": (37.170, 37.360, 127.020, 127.260),
    "suwon": (37.220, 37.320, 126.940, 127.080),
    "hwaseong": (37.090, 37.250, 126.850, 127.150),
    "osan": (37.110, 37.190, 127.020, 127.110),
    "pyeongtaek": (36.940, 37.110, 126.930, 127.150),
    "anyang": (37.350, 37.430, 126.890, 126.990),
    "gwacheon": (37.400, 37.460, 126.960, 127.020),
    "gunpo": (37.320, 37.380, 126.900, 126.960),
    "uiwang": (37.320, 37.420, 126.920, 127.000),
    "bucheon": (37.455, 37.555, 126.690, 126.820),
    "gwangmyeong": (37.390, 37.500, 126.830, 126.910),
    "siheung": (37.300, 37.460, 126.650, 126.860),
    "ansan": (37.260, 37.360, 126.750, 126.880),
    "goyang": (37.585, 37.715, 126.700, 126.930),
    "paju": (37.680, 37.960, 126.680, 126.860),
    "gimpo": (37.570, 37.725, 126.520, 126.785),
    "uijeongbu": (37.685, 37.795, 127.000, 127.130),
    "yangju": (37.740, 37.870, 126.960, 127.120),
    "dongducheon": (37.870, 37.940, 127.020, 127.090),
    "pocheon": (37.800, 37.950, 127.120, 127.300),
    "gapyeong": (37.720, 37.900, 127.400, 127.600),
    "yangpyeong": (37.400, 37.580, 127.400, 127.600),
    "guri": (37.550, 37.640, 127.080, 127.180),
    "namyangju": (37.530, 37.720, 127.080, 127.400),
    "hanam": (37.490, 37.580, 127.150, 127.260),
    "gwangju_gg": (37.290, 37.480, 127.170, 127.400),
    "icheon": (37.180, 37.340, 127.350, 127.520),
    "yeoju": (37.200, 37.380, 127.500, 127.750),
    "anseong": (36.980, 37.120, 127.150, 127.350),
    # 인천
    "bupyeong": (37.450, 37.540, 126.680, 126.770),
    "namdong": (37.370, 37.480, 126.670, 126.760),
    "yeonsu": (37.350, 37.440, 126.580, 126.700),
    "seo": (37.460, 37.620, 126.550, 126.720),
    "michuhol": (37.410, 37.480, 126.630, 126.710),
    "ic-jung": (37.380, 37.620, 126.430, 126.680),
    "gyeyang": (37.510, 37.590, 126.690, 126.780),
}


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.SPOTS


def haversine(lat1, lng1, lat2, lng2):
    r = 6371.0
    d_lat = radians(lat2 - lat1)
    d_lng = radians(lng2 - lng1)
    a = sin(d_lat / 2) ** 2 + cos(radians(lat1)) * cos(radians(lat2)) * sin(d_lng / 2) ** 2
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))


def geocode(query):
    out = subprocess.run(
        ["maps", "geocode", query, "--country", "KR"],
        capture_output=True, text=True, timeout=60,
    )
    try:
        data = json.loads(out.stdout)
    except Exception:
        return None, out.stdout.strip()[:200] or out.stderr.strip()[:200]
    if not data.get("ok"):
        return None, data.get("message") or "geocode failed"
    d = data["data"]
    return (round(float(d["latitude"]), 7), round(float(d["longitude"]), 7), d.get("address", "")), None


def main():
    write = "--write" in sys.argv
    spots = []
    for f in ("spots_seoul.py", "spots_gyeonggi.py", "spots_incheon.py"):
        for row in load_module(f.replace(".py", ""), os.path.join(HERE, f)):
            spots.append({
                "id": row[0], "name": row[1], "area": row[2], "city": row[3],
                "zone": row[4], "query": row[5], "peak": row[6], "tip": row[7],
                "lat": None, "lng": None, "address": None,
            })
    far = json.load(open(os.path.join(HERE, "legacy_far.json"), encoding="utf-8"))
    metro_queries = {s["query"] for s in spots}

    cache = {}
    if os.path.exists(CACHE):
        cache = json.load(open(CACHE, encoding="utf-8"))

    problems = []
    for i, s in enumerate(spots, 1):
        q = s["query"]
        if q in cache and cache[q].get("lat"):
            s["lat"], s["lng"], s["address"] = cache[q]["lat"], cache[q]["lng"], cache[q].get("address", "")
        else:
            res, err = geocode(q)
            if res is None:
                problems.append(f"GEOCODE FAIL {s['id']} ({q}): {err}")
                continue
            s["lat"], s["lng"], s["address"] = res
            cache[q] = {"lat": res[0], "lng": res[1], "address": res[2]}
            json.dump(cache, open(CACHE, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
            time.sleep(0.25)
        print(f"[{i:3d}/{len(spots)}] {s['id']:24s} {s['lat']:.6f} {s['lng']:.6f}  {s['address']}")

    for s in far:
        s = dict(s)
        s.setdefault("zone", "far")
        spots.append(s)

    # ---- 검증
    ids = [s["id"] for s in spots if s["id"]]
    if len(set(ids)) != len(ids):
        dup = sorted({x for x in ids if ids.count(x) > 1})
        problems.append(f"ID 중복: {dup}")

    names = [s["name"] for s in spots]
    if len(set(names)) != len(names):
        dup = sorted({x for x in names if names.count(x) > 1})
        problems.append(f"이름 중복: {dup}")

    for s in spots:
        if s.get("lat") is None:
            continue
        if s.get("zone") in ZONES:
            lo_lat, hi_lat, lo_lng, hi_lng = ZONES[s["zone"]]
            if not (lo_lat <= s["lat"] <= hi_lat and lo_lng <= s["lng"] <= hi_lng):
                problems.append(
                    f"ZONE 이탈 {s['id']} ({s['name']}, {s['zone']}): {s['lat']}, {s['lng']} — {s.get('address')}"
                )
        if not (33.0 <= s["lat"] <= 38.7 and 124.0 <= s["lng"] <= 132.0):
            problems.append(f"좌표 이상 {s['id']}: {s['lat']}, {s['lng']}")
        if not s.get("peak"):
            problems.append(f"피크 없음 {s['id']}")
        if not s.get("tip"):
            problems.append(f"tip 없음 {s['id']}")

    # 300m 근접 중복
    near = []
    for i in range(len(spots)):
        for j in range(i + 1, len(spots)):
            a, b = spots[i], spots[j]
            if a.get("lat") is None or b.get("lat") is None:
                continue
            d = haversine(a["lat"], a["lng"], b["lat"], b["lng"]) * 1000
            if d < 300:
                near.append((round(d), a["id"], a["name"], b["id"], b["name"]))
    for row in sorted(near):
        problems.append(f"300m 근접: {row[0]}m {row[1]}({row[2]}) ↔ {row[3]}({row[4]})")

    from collections import Counter
    cnt = Counter(s["city"] for s in spots)
    print("\n=== 요약 ===")
    print("총", len(spots), "곳 /", dict(cnt))
    for city in ("서울", "경기", "인천"):
        named = [s for s in spots if s["city"] == city and s["id"]]
    print("수도권:", sum(v for k, v in cnt.items() if k in ("서울", "경기", "인천")))
    print("문제:", len(problems))
    for p in problems:
        print("  -", p)

    if problems:
        print("\n문제를 먼저 해결하세요. (파일 쓰지 않음)")
        if not write:
            return 1
        if "--force" not in sys.argv:
            return 1

    ordered = [s for s in spots if s["city"] == "서울"] + \
              [s for s in spots if s["city"] == "경기"] + \
              [s for s in spots if s["city"] == "인천"] + \
              [s for s in spots if s["city"] not in ("서울", "경기", "인천")]

    if write:
        src = open(KOTLIN, encoding="utf-8").read()
        tail_marker = "    val CITIES: List<String> = listOf("
        head_marker = "    val ALL: List<Hotspot> = listOf("
        head = src[:src.index(head_marker)]
        tail = src[src.index(tail_marker):]

        lines = [head + head_marker]
        for s in ordered:
            name = s["name"]
            tip = s["tip"]
            area = s["area"]
            peak = s["peak"]
            city = s["city"]
            for v in (name, area, peak, tip, city):
                assert '"' not in v and "\\" not in v, (s["id"], v)
            lines.append("        Hotspot(")
            lines.append(f'            id = "{s["id"]}",')
            lines.append(f'            name = "{name}",')
            lines.append(f'            area = "{area}",')
            lines.append(f'            city = "{city}",')
            lines.append(f'            lat = {s["lat"]:.6f},')
            lines.append(f'            lng = {s["lng"]:.6f},')
            lines.append(f'            peak = "{peak}",')
            lines.append(f'            tip = "{tip}"')
            lines.append("        ),")
        lines.append("    )\n\n")
        open(KOTLIN, "w", encoding="utf-8").write("\n".join(lines) + tail)
        print("작성:", KOTLIN)
    return 0


if __name__ == "__main__":
    sys.exit(main())
