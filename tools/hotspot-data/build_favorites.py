# -*- coding: utf-8 -*-
"""카카오맵 공개 즐겨찾기 3개 그룹 → 대리장부 콜밭 데이터(res/raw/callspots.tsv).

원본: tools/hotspot-data/kakao/*.json  (카카오맵 favorite/list API 응답 그대로)
  - callpoint2_6111389.json  '대리 콜 포인트2' 446곳
  - callpoint1_1688555.json  '대리 콜 포인트1' 473곳
  - taksong_1648350.json     '탁송 발주'       360곳

1) 좌표: API가 주는 lat/lon(WGS84)을 우선 쓰고, 없는 항목은 x/y(WCONGNAMUL)를
   wcong2.W.cong 변환으로 바꾼다. 변환 정확도는 lat/lon이 같이 있는 804건으로 검증한다.
2) 이름·좌표가 비었거나 중복(같은 장소 key / 200m 내 같은 이름)인 항목은 합친다.
3) 기존 손수 정리한 318곳(Hotspots.kt)과 300m 내 같은 이름이면 기존 것을 남긴다.
4) 주소가 없는 항목은 가장 가까운 주소 있는 항목의 시·도로 채운다(정확도 검증 포함).

사용: python3 tools/hotspot-data/build_favorites.py [--write]
"""
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "app/src/main/res/raw/callspots.tsv")
HOTSPOTS_KT = os.path.join(ROOT, "app/src/main/java/com/youngsu/daeriledger/data/Hotspots.kt")
sys.path.insert(0, os.path.join(ROOT, "..", "..", "work", "kakaomap"))
import wcong2  # noqa: E402  (좌표 변환 모듈)

SOURCES = [
    ("kakao/callpoint2_6111389.json", "콜포인트2"),
    ("kakao/callpoint1_1688555.json", "콜포인트1"),
    ("kakao/taksong_1648350.json", "탁송"),
]

CITY_ALIAS = {
    "서울": "서울", "서울특별시": "서울", "경기": "경기", "경기도": "경기", "인천": "인천",
    "인천광역시": "인천", "부산": "부산", "부산광역시": "부산", "대구": "대구",
    "대구광역시": "대구", "대전": "대전", "대전광역시": "대전", "광주": "광주",
    "광주광역시": "광주", "울산": "울산", "울산광역시": "울산", "세종": "세종",
    "세종특별자치시": "세종", "충북": "충북", "충청북도": "충북", "충남": "충남",
    "충청남도": "충남", "강원": "강원", "강원특별자치도": "강원", "전북": "전북",
    "전북특별자치도": "전북", "전남": "전남", "전라남도": "전남", "경북": "경북",
    "경상북도": "경북", "경남": "경남", "경상남도": "경남", "제주": "제주",
    "제주특별자치도": "제주",
}


def dist_m(lat1, lng1, lat2, lng2):
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = p2 - p1
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def norm_name(s):
    s = re.sub(r"\s+", " ", (s or "").strip())
    return s


def city_of_addr(addr):
    tok = (addr or "").strip().split(" ")
    if not tok or not tok[0]:
        return None
    return CITY_ALIAS.get(tok[0])


def area_of_addr(addr):
    """'경기 용인시 기흥구 농서동 444' -> '경기 용인시 기흥구' / '충북 진천군 이월면 진안로 55' -> '충북 진천군 이월면'"""
    tok = [t for t in (addr or "").strip().split(" ") if t]
    if not tok:
        return ""
    out = [tok[0]]
    for t in tok[1:4]:
        if re.search(r"(시|군|구|읍|면)$", t):
            out.append(t)
            if t.endswith("구") or t.endswith("군") or (t.endswith("시") and len(out) > 1):
                break
        else:
            break
    return " ".join(out) if len(out) > 1 else tok[0]


def load_curated():
    """Hotspots.kt 의 손수 정리한 목록(id, name, lat, lng)만 읽는다."""
    src = open(HOTSPOTS_KT, encoding="utf-8").read()
    body = src[src.index("val ALL: List<Hotspot> = listOf("):src.index("val CITIES")]
    items = []
    for m in re.finditer(
            r'Hotspot\(\s*id = "([^"]+)",\s*name = "([^"]+)",.*?lat = ([\d.]+),\s*lng = ([\d.]+),',
            body, re.S):
        items.append(dict(id=m.group(1), name=m.group(2), lat=float(m.group(3)), lng=float(m.group(4))))
    return items


def build():
    raw = []
    for path, label in SOURCES:
        data = json.load(open(os.path.join(HERE, path), encoding="utf-8"))
        favs = data["favorites"]
        print(f"{label:6s} {len(favs):4d}곳  ({path})")
        for it in favs:
            raw.append(dict(src=label, **it))

    stats = dict(input=len(raw), no_name=0, no_coord=0, dup_key=0, dup_near=0, curated=0, fixed_coord=0)
    coord_err = []

    rows = []
    for it in raw:
        name = norm_name(it.get("display1"))
        addr = norm_name(it.get("display2"))
        memo = norm_name(it.get("memo"))
        lat = it.get("lat")
        lng = it.get("lon")
        if lat is not None and lng is not None:
            lat, lng = float(lat), float(lng)
            # 같은 항목의 x/y 변환 결과와 비교해 변환 정확도를 잰다
            if it.get("x") and it.get("y"):
                clat, clng = wcong2.wcong_to_wgs84(it["x"], it["y"])
                coord_err.append(dist_m(lat, lng, clat, clng))
        else:
            if not it.get("x") or not it.get("y"):
                stats["no_coord"] += 1
                continue
            lat, lng = wcong2.wcong_to_wgs84(it["x"], it["y"])
            stats["fixed_coord"] += 1
        if not name:
            stats["no_name"] += 1
            continue
        if not (33.0 < lat < 39.5 and 124.0 < lng < 132.5):
            stats["no_coord"] += 1
            continue
        rows.append(dict(
            key=str(it.get("key") or ""), name=name, addr=addr, memo=memo,
            src=it["src"], lat=round(lat, 6), lng=round(lng, 6), type=it.get("type"),
        ))

    # 같은 장소(key) 중복 → 처음 그룹만 남기고, 여러 그룹에 있으면 이름 뒤에 표시하지 않는다
    seen_key = {}
    dedup = []
    for r in rows:
        k = r["key"]
        if k and k in seen_key:
            stats["dup_key"] += 1
            continue
        seen_key[k] = True
        dedup.append(r)
    rows = dedup

    # 200m 안에 이름이 같거나 한쪽이 다른 쪽을 품는 항목 → 하나만
    rows.sort(key=lambda r: (r["name"], r["src"]))
    dedup = []
    for r in rows:
        twin = None
        for q in dedup:
            if dist_m(r["lat"], r["lng"], q["lat"], q["lng"]) <= 200:
                a, b = r["name"].replace(" ", ""), q["name"].replace(" ", "")
                if a == b or a in b or b in a:
                    twin = q
                    break
        if twin:
            stats["dup_near"] += 1
            continue
        dedup.append(r)
    rows = dedup

    # 기존 318곳과 300m 안에서 이름이 겹치면 기존 것을 남긴다
    curated = load_curated()
    keep = []
    for r in rows:
        a = r["name"].replace(" ", "")
        clash = None
        for c in curated:
            if dist_m(r["lat"], r["lng"], c["lat"], c["lng"]) <= 300:
                b = c["name"].replace(" ", "")
                if a == b or a in b or b in a:
                    clash = c
                    break
        if clash:
            stats["curated"] += 1
            continue
        keep.append(r)
    rows = keep

    # 주소 없는 항목의 시·도: 가장 가까운 '주소 있는' 항목에서 가져온다.
    known = [r for r in rows if r["addr"] and city_of_addr(r["addr"])]
    # 검증: 주소 있는 항목을 하나씩 빼고 최근접 이웃 도시가 맞는지
    ok = bad = 0
    for r in known:
        best = None
        bestd = 1e18
        for q in known:
            if q is r:
                continue
            d = dist_m(r["lat"], r["lng"], q["lat"], q["lng"])
            if d < bestd:
                best, bestd = q, d
        if best and bestd < 40000:
            if city_of_addr(best["addr"]) == city_of_addr(r["addr"]):
                ok += 1
            else:
                bad += 1
    nn_acc = ok / (ok + bad) if ok + bad else 0.0

    for r in rows:
        city = city_of_addr(r["addr"])
        if not city:
            best, bestd = None, 1e18
            for q in known:
                d = dist_m(r["lat"], r["lng"], q["lat"], q["lng"])
                if d < bestd:
                    best, bestd = q, d
            city = city_of_addr(best["addr"]) if best else ""
            r["area"] = city
            r["tip"] = r["memo"] or "지도에 찍어 둔 지점"
        else:
            r["area"] = area_of_addr(r["addr"])
            r["tip"] = r["addr"] if not r["memo"] else f'{r["addr"]} · {r["memo"]}'
        r["city"] = city

    rows.sort(key=lambda r: ("서울 경기 인천 부산 대구 대전 광주 울산 세종 충북 충남 강원 전북 전남 경북 경남 제주"
                             .split().index(r["city"]) if r["city"] in
                             "서울 경기 인천 부산 대구 대전 광주 울산 세종 충북 충남 강원 전북 전남 경북 경남 제주".split()
                             else 99, r["city"], r["name"]))

    for i, r in enumerate(rows):
        r["id"] = "k" + (r["key"] or str(i))

    # 검증 보고
    coord_err.sort()
    if coord_err:
        print("\n[좌표 검증] API lat/lon이 같이 있는 %d건에서 x/y 변환값과의 차이: "
              "중앙 %.1fm · 95%% %.1fm · 최대 %.1fm (0.4m 단위 격자 + 지번/도로명 지점 차이)"
              % (len(coord_err), coord_err[len(coord_err) // 2],
                 coord_err[int(len(coord_err) * 0.95)], coord_err[-1]))
    print("[시·도 추정] 주소 있는 항목 최근접 이웃 검증 정확도 %.2f%% (%d/%d)" % (nn_acc * 100, ok, ok + bad))
    print("[정리] 입력 %d → 이름없음 %d · 좌표없음 %d · 같은 장소 %d · 200m 중복 %d · 기존 318곳과 겹침 %d → 최종 %d곳"
          % (stats["input"], stats["no_name"], stats["no_coord"], stats["dup_key"],
             stats["dup_near"], stats["curated"], len(rows)))
    from collections import Counter
    print("[그룹]", dict(Counter(r["src"] for r in rows)))
    print("[시·도]", dict(Counter(r["city"] for r in rows).most_common()))
    print("[좌표 변환으로 채운 항목]", stats["fixed_coord"])

    return rows


def write_tsv(rows):
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        for r in rows:
            f.write("\t".join([r["id"], r["src"], r["name"], r["city"], r["area"],
                               "%.6f" % r["lat"], "%.6f" % r["lng"], r["tip"]]) + "\n")
    print("\n저장: %s (%d줄)" % (OUT, len(rows)))


if __name__ == "__main__":
    rows = build()
    if "--write" in sys.argv:
        write_tsv(rows)
