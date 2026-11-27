#!/usr/bin/env python3
"""DB v1·v2·v3 → v4 마이그레이션 무손실 검사.

앱은 Room 대신 표준 SQLite(SQLiteOpenHelper)를 쓴다. v1.0.11 에서 운행 테이블에
경유지(via_place) 칸이 늘어 DB_VERSION 이 2 → 3, v1.0.15 에서 내 콜밭에
구분(service)·지역(area) 칸이 늘어 3 → 4 가 되었다.
이 검사는 예전 버전 스키마 그대로 DB 를 만들어 (실사용 규모 11,876건 + 지출 + 휴무일 + 콜밭)
앱의 onUpgrade 가 하는 일을 그대로 적용한 뒤, 기존 값이 하나도 바뀌지 않았는지 본다.

v1.0.32 부터는 프로그램 7종(대구사랑·대구시민·대구세종·트리콜·수퍼드라이버·오픈마일·별대리)이
늘고 `사용할 프로그램 선택` 체크로 화면에 보일 프로그램을 고른다. 이 검사는 DB 스키마를 바꾸지 않고
(platform 칸은 자유 문자열) 신규 7종 기록이 예전 기록·집계·백업과 섞여도 그대로 남는지,
체크를 꺼도 기록이 지워지지 않는지, 백업·복원이 체크 상태까지 담는지도 함께 본다.

    python3 tools/migration-check/check_migration.py

마지막 줄이 ALL CHECKS PASSED 이면 통과.
"""
import hashlib
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DB_SOURCE = ROOT / "app/src/main/java/com/youngsu/daeriledger/data/LedgerDb.kt"
REPO_SOURCE = ROOT / "app/src/main/java/com/youngsu/daeriledger/data/LedgerRepository.kt"
MODELS_SOURCE = ROOT / "app/src/main/java/com/youngsu/daeriledger/data/Models.kt"
SETTINGS_SOURCE = ROOT / "app/src/main/java/com/youngsu/daeriledger/settings/AppSettings.kt"
BACKUP_SOURCE = ROOT / "app/src/main/java/com/youngsu/daeriledger/data/BackupManager.kt"
SETTINGS_SCREEN = ROOT / "app/src/main/java/com/youngsu/daeriledger/ui/settings/SettingsScreen.kt"
ADD_RIDE = ROOT / "app/src/main/java/com/youngsu/daeriledger/ui/entry/AddRideActivity.kt"

RIDES = 11_876          # 사용자가 복원한 기존 기록 수 (2021-04-06 ~ 2026-11-22)
EXPENSES = 412
OFF_DAYS = 45

failures = 0


def expect(name, actual, expected):
    global failures
    if actual == expected:
        print(f"PASS  {name} = {actual}")
    else:
        failures += 1
        print(f"FAIL  {name} = {actual} (expected {expected})")


# ---------------------------------------------------------------- 스키마 (v2 그대로)
T_RIDES_V2 = """
CREATE TABLE rides (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    business_day TEXT NOT NULL,
    time_ms INTEGER NOT NULL,
    platform TEXT NOT NULL,
    service TEXT NOT NULL,
    payment TEXT NOT NULL,
    gross INTEGER NOT NULL DEFAULT 0,
    fee_percent REAL NOT NULL DEFAULT 0,
    fee_amount INTEGER NOT NULL DEFAULT 0,
    tip INTEGER NOT NULL DEFAULT 0,
    insurance_amount INTEGER NOT NULL DEFAULT 0,
    from_place TEXT NOT NULL DEFAULT '',
    to_place TEXT NOT NULL DEFAULT '',
    memo TEXT NOT NULL DEFAULT '',
    spot TEXT NOT NULL DEFAULT '',
    settled INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT 0
)
"""

T_EXPENSES = """
CREATE TABLE expenses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    business_day TEXT NOT NULL,
    time_ms INTEGER NOT NULL,
    category TEXT NOT NULL,
    amount INTEGER NOT NULL DEFAULT 0,
    memo TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL DEFAULT 0
)
"""

T_OFF_DAYS = """
CREATE TABLE IF NOT EXISTS off_days (
    business_day TEXT PRIMARY KEY,
    created_at INTEGER NOT NULL DEFAULT 0
)
"""

T_SPOTS = """
CREATE TABLE my_spots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    lat REAL,
    lng REAL,
    peak TEXT NOT NULL DEFAULT '',
    memo TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL DEFAULT 0
)
"""

OLD_COLUMNS = [
    "id", "business_day", "time_ms", "platform", "service", "payment", "gross",
    "fee_percent", "fee_amount", "tip", "insurance_amount", "from_place", "to_place",
    "memo", "spot", "settled", "created_at",
]

PLATFORMS = ["kakao", "tmap", "handle", "roji", "colmaner", "icon", "personal"]
PAYMENTS = ["cash", "card", "unpaid"]


def build_v2(path: Path):
    """v1.0.10 이전 앱이 남긴 DB 와 같은 모양 (user_version = 2)"""
    db = sqlite3.connect(path)
    db.execute(T_RIDES_V2)
    db.execute("CREATE INDEX idx_rides_day ON rides(business_day)")
    db.execute("CREATE INDEX idx_rides_platform ON rides(platform)")
    db.execute(T_EXPENSES)
    db.execute("CREATE INDEX idx_expenses_day ON expenses(business_day)")
    db.execute(T_OFF_DAYS)
    db.execute(T_SPOTS)

    rows = []
    for i in range(RIDES):
        day_index = i // 8                       # 하루 평균 8콜
        year = 2021 + day_index // 365
        month = (day_index // 30) % 12 + 1
        day = day_index % 28 + 1
        rows.append((
            f"{year:04d}-{month:02d}-{day:02d}",
            1_600_000_000_000 + i * 3_600_000,
            PLATFORMS[i % len(PLATFORMS)],
            "daeri" if i % 5 else "taksong",
            PAYMENTS[i % len(PAYMENTS)],
            15_000 + (i % 40) * 1_000,
            20.0,
            (15_000 + (i % 40) * 1_000) // 5,
            (i % 3) * 1_000,
            1_500,
            f"출발지{i % 97}" if i % 4 else "",
            f"도착지{i % 89}" if i % 7 else "",
            f"메모 {i}" if i % 6 == 0 else "",
            "",
            1 if i % 11 else 0,
            1_600_000_000_000 + i,
        ))
    db.executemany(
        "INSERT INTO rides (business_day, time_ms, platform, service, payment, gross,"
        " fee_percent, fee_amount, tip, insurance_amount, from_place, to_place, memo,"
        " spot, settled, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        rows,
    )
    db.executemany(
        "INSERT INTO expenses (business_day, time_ms, category, amount, memo, created_at)"
        " VALUES (?,?,?,?,?,?)",
        [(f"2022-01-{i % 28 + 1:02d}", 1_650_000_000_000 + i, "식대", 8_000 + i, "", i)
         for i in range(EXPENSES)],
    )
    db.executemany(
        "INSERT INTO off_days (business_day, created_at) VALUES (?, ?)",
        [(f"2023-{i // 28 + 1:02d}-{i % 28 + 1:02d}", i) for i in range(OFF_DAYS)],
    )
    db.execute("INSERT INTO my_spots (name, lat, lng, peak, memo, created_at)"
               " VALUES ('강남역', 37.4979, 127.0276, '금·토 23시', '예전에 저장한 콜밭', 1)")
    db.commit()
    db.execute("PRAGMA user_version = 2")
    db.commit()
    return db


def column_names(db, table):
    return [r[1] for r in db.execute(f"PRAGMA table_info({table})")]


# 마이그레이션으로 새로 붙는 칸 — 지문 비교에서 뺀다 (칸마다 테이블이 달라 이름만으로는 못 가른다)
FINGERPRINT_SKIP = {("rides", "via_place"), ("my_spots", "service"), ("my_spots", "area")}


def fingerprint(db):
    """새로 붙은 칸만 빼고 전체 내용의 지문을 만든다 (마이그레이션 전후 비교용)"""
    h = hashlib.sha256()
    for table in ("rides", "expenses", "off_days", "my_spots"):
        keep = [c for c in column_names(db, table) if (table, c) not in FINGERPRINT_SKIP]
        if not keep:
            continue  # 아직 없는 테이블(v1 에는 off_days 가 없다)
        sql = f"SELECT {','.join(keep)} FROM {table} ORDER BY {keep[0]}"
        for row in db.execute(sql):
            h.update(repr(row).encode())
    return h.hexdigest()


def repository_column_order():
    """LedgerRepository.insertRidesBulk 가 넣는 칸 순서 — DB 칸 순서와 같아야 한다"""
    repo_src = REPO_SOURCE.read_text(encoding="utf-8")
    db_src = DB_SOURCE.read_text(encoding="utf-8")
    const = dict(re.findall(r'const val (C_[A-Z_]+) = "([^"]+)"', db_src))
    block = repo_src[repo_src.index("fun insertRidesBulk"):repo_src.index("private fun rideValues")]
    names = re.findall(r"\$\{LedgerDb\.(C_[A-Z_]+)\}", block)
    order = [const[n] for n in names]
    placeholders = len(re.findall(r"st\.bind", block))
    return order, placeholders


def source_alignment():
    """검사에 쓰는 값이 앱 소스와 같은지 — 스키마 이름·버전이 바뀌면 여기서 먼저 걸린다"""
    src = DB_SOURCE.read_text(encoding="utf-8")
    version = int(re.search(r"const val DB_VERSION = (\d+)", src).group(1))
    via = re.search(r'const val C_VIA = "([^"]+)"', src).group(1)
    area = re.search(r'const val C_AREA = "([^"]+)"', src).group(1)
    create_has_via = bool(re.search(
        r"\$C_FROM TEXT NOT NULL DEFAULT '',\s*\n\s*\$C_VIA TEXT NOT NULL DEFAULT ''", src))
    upgrade_has_alter = "ALTER TABLE $T_RIDES ADD COLUMN $C_VIA TEXT NOT NULL DEFAULT ''" in src
    add_spot_service = ("ALTER TABLE $T_SPOTS ADD COLUMN $C_SERVICE TEXT NOT NULL DEFAULT 'daeri'"
                        in src)
    add_spot_area = "ALTER TABLE $T_SPOTS ADD COLUMN $C_AREA TEXT NOT NULL DEFAULT ''" in src
    create_has_spot_columns = bool(re.search(
        r"\$C_MEMO TEXT NOT NULL DEFAULT '',\s*\n\s*\$C_CREATED INTEGER NOT NULL DEFAULT 0,\s*\n"
        r"\s*\$C_SERVICE TEXT NOT NULL DEFAULT 'daeri',\s*\n\s*\$C_AREA TEXT NOT NULL DEFAULT ''",
        src))
    guard = "if (!hasColumn(db, T_SPOTS, C_SERVICE))" in src and \
            "if (!hasColumn(db, T_SPOTS, C_AREA))" in src
    expect("소스 DB_VERSION", version, 4)
    expect("소스 경유 칸 이름", via, "via_place")
    expect("소스 지역 칸 이름", area, "area")
    expect("CREATE TABLE 에 경유 칸", create_has_via, True)
    expect("onUpgrade 에 ALTER TABLE(경유)", upgrade_has_alter, True)
    expect("onUpgrade 에 ALTER TABLE(내 콜밭 구분)", add_spot_service, True)
    expect("onUpgrade 에 ALTER TABLE(내 콜밭 지역)", add_spot_area, True)
    expect("CREATE TABLE 에 내 콜밭 새 칸", create_has_spot_columns, True)
    expect("내 콜밭 새 칸은 hasColumn 으로 확인", guard, True)

    repo_src = REPO_SOURCE.read_text(encoding="utf-8")
    expect("saveSpot 이 구분을 쓴다", "put(LedgerDb.C_SERVICE, spot.service.id)" in repo_src, True)
    expect("saveSpot 이 지역을 쓴다", "put(LedgerDb.C_AREA, spot.area)" in repo_src, True)
    expect("allSpots 가 구분을 읽는다",
           "ServiceType.from(c.getString(c.getColumnIndexOrThrow(LedgerDb.C_SERVICE)))" in repo_src,
           True)


def migrate_v2_to_v3(db):
    """앱 onUpgrade(oldVersion=2) 의 `< 3` 부분 그대로"""
    db.execute("ALTER TABLE rides ADD COLUMN via_place TEXT NOT NULL DEFAULT ''")
    db.commit()


def migrate_v3_to_v4(db):
    """앱 onUpgrade 의 `< 4` 부분 그대로 — 이미 있으면 건너뛴다"""
    if "service" not in column_names(db, "my_spots"):
        db.execute("ALTER TABLE my_spots ADD COLUMN service TEXT NOT NULL DEFAULT 'daeri'")
    if "area" not in column_names(db, "my_spots"):
        db.execute("ALTER TABLE my_spots ADD COLUMN area TEXT NOT NULL DEFAULT ''")
    db.execute("PRAGMA user_version = 4")
    db.commit()


V1_SCHEMA_BEFORE = T_RIDES_V2  # v1 에도 운행 테이블은 같은 모양이었다


def check_v2_to_v4(tmp: Path):
    path = tmp / "v2.db"
    db = build_v2(path)
    before_count = db.execute("SELECT COUNT(*) FROM rides").fetchone()[0]
    before_fp = fingerprint(db)
    before_cols = column_names(db, "rides")
    before_spots = db.execute(
        "SELECT name, lat, lng, peak, memo, created_at FROM my_spots").fetchall()
    db.close()

    db = sqlite3.connect(path)
    migrate_v2_to_v3(db)
    migrate_v3_to_v4(db)
    after_cols = column_names(db, "rides")
    expect("기존 운행 건수 유지", db.execute("SELECT COUNT(*) FROM rides").fetchone()[0], before_count)
    expect("기존 건수 = 11,876", before_count, RIDES)
    expect("기존 칸 그대로", after_cols[:len(before_cols)], before_cols)
    expect("맨 뒤에 경유 칸 추가", after_cols[-1], "via_place")
    expect("기존 값 지문 동일", fingerprint(db), before_fp)
    expect("경유 칸은 전부 빈 문자열",
           db.execute("SELECT COUNT(*) FROM rides WHERE via_place = ''").fetchone()[0], RIDES)
    expect("지출 건수 유지", db.execute("SELECT COUNT(*) FROM expenses").fetchone()[0], EXPENSES)
    expect("휴무일 유지", db.execute("SELECT COUNT(*) FROM off_days").fetchone()[0], OFF_DAYS)
    expect("user_version", db.execute("PRAGMA user_version").fetchone()[0], 4)
    expect("무결성 검사", db.execute("PRAGMA integrity_check").fetchone()[0], "ok")

    # 내 콜밭 — 예전에 저장한 콜밭은 그대로, 새 칸만 기본값으로 채워진다
    spot_cols = column_names(db, "my_spots")
    expect("내 콜밭 칸 순서(뒤에 구분·지역)", spot_cols[-2:], ["service", "area"])
    expect("예전에 저장한 콜밭 그대로", db.execute(
        "SELECT name, lat, lng, peak, memo, created_at FROM my_spots").fetchall(), before_spots)
    expect("예전 콜밭 구분 = 대리", db.execute(
        "SELECT service FROM my_spots WHERE name = '강남역'").fetchone()[0], "daeri")
    expect("예전 콜밭 지역 = 빈 값", db.execute(
        "SELECT area FROM my_spots WHERE name = '강남역'").fetchone()[0], "")

    # 새로 추가한 콜밭 저장·읽기
    db.execute(
        "INSERT INTO my_spots (name, lat, lng, peak, memo, created_at, service, area)"
        " VALUES (?,?,?,?,?,?,?,?)",
        ("정자역 사거리", 37.3665, 127.1085, "23:00~02:00", "2차 골목", 2, "taksong",
         "경기 성남시 분당구 정자동"))
    db.commit()
    expect("새 콜밭 읽기", db.execute(
        "SELECT name, service, area, peak FROM my_spots WHERE name = '정자역 사거리'").fetchone(),
        ("정자역 사거리", "taksong", "경기 성남시 분당구 정자동", "23:00~02:00"))
    db.execute("INSERT INTO my_spots (name, peak, memo, created_at, service, area)"
               " VALUES ('이름만', '', '', 3, 'daeri', '')")
    db.commit()
    expect("이름만 저장한 콜밭(좌표 없음)", db.execute(
        "SELECT lat FROM my_spots WHERE name = '이름만'").fetchone()[0], None)

    # 앱이 읽는 순서(LedgerRepository.toRide)대로 한 줄을 다시 읽어 본다
    cols = column_names(db, "rides")
    row = db.execute(
        "SELECT id, business_day, time_ms, platform, service, payment, gross, fee_percent,"
        " fee_amount, tip, insurance_amount, from_place, via_place, to_place, memo, spot,"
        " settled, created_at FROM rides WHERE from_place != '' LIMIT 1"
    ).fetchone()
    expect("읽기 순서 18칸", len(row), 18)
    expect("읽은 경유 값", row[12], "")
    expect("읽은 출발지", row[11].startswith("출발지"), True)

    order, binds = repository_column_order()
    expect("저장 칸 = DB 칸(이름 기준, 경유 포함)", set(order), set(after_cols[1:]))
    expect("저장 칸 수 = 바인딩 수", binds, len(order))

    # 경유를 넣고 다시 읽기 — 새 칸이 정상 저장되는지
    db.execute("UPDATE rides SET via_place = '광교' WHERE id = ?", (row[0],))
    db.commit()
    expect("경유 저장", db.execute(
        "SELECT via_place FROM rides WHERE id = ?", (row[0],)).fetchone()[0], "광교")
    expect("경유 넣어도 다른 값 그대로", db.execute(
        "SELECT from_place, to_place FROM rides WHERE id = ?", (row[0],)).fetchone(),
        (row[11], row[13]))
    db.close()


def check_v1_to_v4(tmp: Path):
    """v1 DB(휴무일 테이블 없음)에서 바로 올라오는 경우 — off_days 생성 + 경유·콜밭 칸 추가"""
    path = tmp / "v1.db"
    db = sqlite3.connect(path)
    db.execute(T_RIDES_V2)
    db.execute(T_EXPENSES)
    db.execute(T_SPOTS)
    db.executemany(
        "INSERT INTO rides (business_day, time_ms, platform, service, payment, gross,"
        " fee_percent, fee_amount, tip, insurance_amount, from_place, to_place, memo, spot,"
        " settled, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(f"2021-04-{i % 28 + 6:02d}", i, "kakao", "daeri", "cash", 20_000, 20.0, 5_000,
          0, 1_500, "분당", "강남", "", "", 1, i) for i in range(500)],
    )
    db.execute("INSERT INTO my_spots (name, lat, lng, peak, memo, created_at)"
               " VALUES ('수지구청역', 37.3222, 127.0956, '', '', 9)")
    db.execute("PRAGMA user_version = 1")
    db.commit()
    before = db.execute("SELECT COUNT(*) FROM rides").fetchone()[0]
    fp = fingerprint(db)
    db.close()

    db = sqlite3.connect(path)
    db.execute(T_OFF_DAYS)                                        # if (oldVersion < 2)
    if "via_place" not in column_names(db, "rides"):              # if (oldVersion < 3)
        db.execute("ALTER TABLE rides ADD COLUMN via_place TEXT NOT NULL DEFAULT ''")
    migrate_v3_to_v4(db)                                          # if (oldVersion < 4)
    expect("v1 운행 건수 유지", db.execute("SELECT COUNT(*) FROM rides").fetchone()[0], before)
    expect("v1 기존 값 지문 동일", fingerprint(db), fp)
    expect("v1 → 경유 칸 생김", column_names(db, "rides")[-1], "via_place")
    expect("v1 → 휴무일 테이블", column_names(db, "off_days"), ["business_day", "created_at"])
    expect("v1 → 내 콜밭 새 칸", column_names(db, "my_spots")[-2:], ["service", "area"])
    expect("v1 콜밭은 대리·지역 없음", db.execute(
        "SELECT name, service, area FROM my_spots").fetchone(), ("수지구청역", "daeri", ""))
    expect("v1 무결성", db.execute("PRAGMA integrity_check").fetchone()[0], "ok")
    db.close()


def fresh_rides_schema():
    """LedgerDb.kt 의 CREATE TABLE rides 문을 그대로 꺼내 온다 (새로 설치한 경우의 스키마)"""
    return _fresh_schema("T_RIDES", "rides")


# ------------------------------------------------ v1.0.32 프로그램 7종 + 체크 필터
NEW_PLATFORMS = [
    ("daegusarang", "대구사랑"),
    ("daegucitizen", "대구시민"),
    ("daegusejong", "대구세종"),
    ("trincall", "트리콜"),
    ("superdrive", "수퍼드라이버"),
    ("openmile", "오픈마일"),
    ("byuldaeri", "별대리"),
]
LEGACY_PLATFORMS = [
    ("kakao", "카카오T"),
    ("tmap", "티맵"),
    ("handle", "핸들"),
    ("roji", "로지"),
    ("colmaner", "콜마너"),
    ("icon", "아이콘"),
    ("personal", "개인콜"),
]
EXPECTED_ORDER = [p[0] for p in LEGACY_PLATFORMS[:6]] + [p[0] for p in NEW_PLATFORMS] + \
    ["personal"]


def platform_defs():
    """Models.kt 의 Platforms.ALL 정의를 그대로 읽는다 — (id, label, 수수료, 총매출모드)"""
    src = MODELS_SOURCE.read_text(encoding="utf-8")
    block = src[src.index("object Platforms"):src.index("fun byId(")]
    rows = re.findall(
        r'PlatformDef\(\s*"([a-z]+)", "([^"]+)", ([0-9.]+), (true|false), 0x[0-9A-Fa-f]+',
        block,
    )
    return [(pid, label, float(fee), net == "true") for pid, label, fee, net in rows]


def check_platform_source():
    """신규 7종 정의·순서·기본 체크 상태가 소스에 그대로 있는지"""
    defs = platform_defs()
    expect("프로그램 14종 정의", len(defs), 14)
    expect("프로그램 순서", [d[0] for d in defs], EXPECTED_ORDER)
    expect("신규 7종 이름", [d[1] for d in defs][6:13], [p[1] for p in NEW_PLATFORMS])
    for pid, label, fee, net in defs[6:13]:
        expect(f"{label} 기본 수수료 20%", fee, 20.0)
        expect(f"{label} 총매출 모드", net, False)
    expect("기존 7종 이름 그대로", [d[1] for d in defs if d[0] not in dict(NEW_PLATFORMS)],
           [p[1] for p in LEGACY_PLATFORMS])

    src = MODELS_SOURCE.read_text(encoding="utf-8")
    defaults = re.search(r"DEFAULT_ENABLED_IDS: List<String> =\s*listOf\(([^)]*)\)", src, re.S)
    default_ids = re.findall(r'"([a-z]+)"', defaults.group(1)) if defaults else []
    expect("기본 체크 7종", default_ids, [p[0] for p in LEGACY_PLATFORMS])
    expect("신규 7종은 기본 미체크", all(p[0] not in default_ids for p in NEW_PLATFORMS), True)
    expect("마지막 한 개 보호", "fun toggleEnabled(" in src and "next.isEmpty()" in src, True)
    expect("체크한 것만 순서대로", "fun visible(" in src and "ALL.filter { it.id in enabledIds }" in src, True)
    expect("수정 기록은 임시 포함", "fun visibleWith(" in src, True)
    expect("저장값 정리", "fun normalizeEnabled(" in src, True)

    st = SETTINGS_SOURCE.read_text(encoding="utf-8")
    expect("설정에 체크 저장", '"enabled_platforms"' in st, True)
    expect("저장값은 Platforms 정리기를 거친다",
           st.count("Platforms.normalizeEnabled(") >= 2, True)

    ss = SETTINGS_SCREEN.read_text(encoding="utf-8")
    expect("설정 수수료 목록은 체크한 것만",
           "Platforms.visible(settings.enabledPlatformIds)" in ss, True)
    expect("14종 전체를 그리지 않는다",
           "Platforms.ALL.forEach { platform -> platformRow" not in ss, True)
    expect("사용할 프로그램 선택 줄", '"사용할 프로그램 선택"' in ss, True)
    expect("최소 1개 안내", "최소 1개" in ss, True)

    ar = ADD_RIDE.read_text(encoding="utf-8")
    expect("운행 기록 칩도 체크한 것만", "Platforms.visibleWith(" in ar, True)
    expect("수정 기록 플랫폼 임시 표시", "includeId = existing?.platformId" in ar, True)
    expect("꺼진 프로그램 안내", "체크를 끈 프로그램" in ar, True)

    bm = BACKUP_SOURCE.read_text(encoding="utf-8")
    expect("백업에 체크 상태", '"enabledPlatforms"' in bm, True)
    expect("복원에 체크 상태", 'optJSONArray("enabledPlatforms")' in bm, True)
    expect("백업 수수료는 14종 모두", "Platforms.ALL.forEach { p ->" in bm, True)


def check_platform_db(tmp: Path):
    """DB 에 신규 7종 기록을 넣어도 예전 기록·집계·백업 구조가 그대로인지"""
    schema = fresh_rides_schema()
    expect("platform 칸에 값 제한(CHECK) 없음", "CHECK" not in schema.upper(), True)
    db = sqlite3.connect(tmp / "platforms.db")
    db.execute(schema)
    order, binds = repository_column_order()
    expect("저장 칸 = 바인딩 수", binds, len(order))

    def insert(day: str, ms: int, pid: str, gross: int) -> None:
        values = {
            "business_day": day, "time_ms": ms, "platform": pid, "service": "daeri",
            "payment": "card", "gross": gross, "fee_percent": 20.0,
            "fee_amount": gross // 5, "tip": 0, "insurance_amount": 1_500,
            "from_place": "정자동", "via_place": "", "to_place": "강남역",
            "memo": "", "spot": "", "settled": 1, "created_at": ms,
        }
        expect_cols = set(order)
        assert expect_cols <= set(values), sorted(expect_cols - set(values))
        db.execute(
            f"INSERT INTO rides ({','.join(order)}) VALUES ({','.join('?' * len(order))})",
            tuple(values[c] for c in order),
        )

    # 예전 7종 한 건씩 + 신규 7종 한 건씩
    for i, (pid, _label) in enumerate(LEGACY_PLATFORMS + NEW_PLATFORMS):
        insert("2026-11-28", 1_764_000_000_000 + i, pid, 25_000)
    db.commit()
    expect("14종 기록 저장", db.execute("SELECT COUNT(*) FROM rides").fetchone()[0], 14)
    groups = db.execute(
        "SELECT platform, COUNT(*), SUM(gross), SUM(fee_amount) FROM rides GROUP BY platform"
    ).fetchall()
    expect("플랫폼별 집계 14줄", len(groups), 14)
    expect("신규 7종도 집계에 나온다",
           sorted(pid for pid, *_ in groups), sorted(p[0] for p in LEGACY_PLATFORMS + NEW_PLATFORMS))
    new_rows = {row[0]: row for row in groups if row[0] in dict(NEW_PLATFORMS)}
    expect("신규 7종 수수료 20%", [r[3] for r in new_rows.values()], [5_000] * 7)

    # 체크를 끈 상태(기본 7종만 체크)여도 기록은 그대로 — 화면에서 숨을 뿐이다
    marks = ",".join("?" * len(LEGACY_PLATFORMS))
    legacy_count = db.execute(
        f"SELECT COUNT(*) FROM rides WHERE platform IN ({marks})",
        [p[0] for p in LEGACY_PLATFORMS],
    ).fetchone()[0]
    total = db.execute("SELECT COUNT(*) FROM rides").fetchone()[0]
    expect("체크한 7종 기존 기록", legacy_count, 7)
    expect("체크 끈 신규 7종 기록도 남아 있다", total - legacy_count, 7)
    labels = dict(LEGACY_PLATFORMS + NEW_PLATFORMS)
    hidden = db.execute(
        f"SELECT platform FROM rides WHERE platform NOT IN ({marks})",
        [p[0] for p in LEGACY_PLATFORMS],
    ).fetchall()
    expect("체크 끈 기록도 이름을 읽는다",
           [labels.get(pid, "카카오T") for (pid,) in hidden], [p[1] for p in NEW_PLATFORMS])
    expect("무결성 검사", db.execute("PRAGMA integrity_check").fetchone()[0], "ok")
    db.close()

    # JSON 백업 — 체크 상태 칸과 14종 수수료가 모두 들어가고, 옛 백업은 기본값을 지킨다
    defs = platform_defs()
    backup = {
        "settings": {
            "enabledPlatforms": ["kakao", "tmap", "handle", "roji", "colmaner", "icon", "daegusarang"],
            "platformFees": {d[0]: {"percent": d[2], "netShown": d[3]} for d in defs},
        }
    }
    settings_json = backup["settings"]
    expect("백업의 수수료 14종", len(settings_json["platformFees"]), 14)
    expect("백업의 신규 7종 수수료",
           [settings_json["platformFees"][p[0]]["percent"] for p in NEW_PLATFORMS], [20.0] * 7)
    known = [d[0] for d in defs]
    restored = [p for p in settings_json["enabledPlatforms"] if p in known]
    expect("복원한 체크 상태(순서 유지)", restored,
           ["kakao", "tmap", "handle", "roji", "colmaner", "icon", "daegusarang"])
    st = SETTINGS_SOURCE.read_text(encoding="utf-8")
    expect("한 번도 안 건드린 기기는 기본 7종(null)",
           "prefs.getStringSet(KEY_ENABLED_PLATFORMS, null)" in st, True)
    expect("옛 백업(체크 칸 없음)은 기존 체크 상태를 덮지 않는다",
           "if (ids.isNotEmpty()) settings.enabledPlatformIds = ids.toSet()"
           in BACKUP_SOURCE.read_text(encoding="utf-8"), True)



def fresh_spots_schema():
    """LedgerDb.kt 의 CREATE TABLE my_spots 문 그대로 (새로 설치한 경우)"""
    return _fresh_schema("T_SPOTS", "my_spots")


def _fresh_schema(table_const: str, table_name: str):
    src = DB_SOURCE.read_text(encoding="utf-8")
    const = dict(re.findall(r'const val (C_[A-Z_]+) = "([^"]+)"', src))
    block = re.search(
        r"CREATE TABLE \$" + table_const + r" \((.*?)\n\s*\)\s*\"\"\"", src, re.S).group(1)
    for name, value in const.items():
        block = block.replace("${" + name + "}", value).replace("$" + name, value)
    return f"CREATE TABLE {table_name} (" + block + ")"


def check_fresh_v4(tmp: Path):
    """새로 설치한 DB(v4 CREATE TABLE) — 경유 칸 위치, 내 콜밭 새 칸, 저장·읽기"""
    schema = fresh_rides_schema()
    expect("새 DB 스키마에 경유 칸", "via_place" in schema, True)
    db = sqlite3.connect(tmp / "fresh.db")
    db.execute(schema)
    cols = column_names(db, "rides")
    expect("새 DB 칸 순서(출발·경유·도착)", cols[11:14], ["from_place", "via_place", "to_place"])
    order, binds = repository_column_order()
    expect("새 DB: 저장 칸 순서 = DB 칸 순서", order, cols[1:])
    expect("새 DB: 바인딩 수", binds, len(order))
    placeholders = ",".join("?" * len(order))
    db.execute(f"INSERT INTO rides ({','.join(order)}) VALUES ({placeholders})",
               ("2026-11-24", 1_763_000_000_000, "kakao", "daeri", "card", 25_000, 20.0, 5_000,
                0, 1_500, "정자동", "광교", "수지구청역", "", "", 1, 1))
    db.commit()
    expect("새 DB 읽기(경유 포함)", db.execute(
        "SELECT from_place, via_place, to_place, payment FROM rides").fetchone(),
        ("정자동", "광교", "수지구청역", "card"))

    # 내 콜밭 — 새로 설치한 DB 스키마가 앱의 saveSpot 이 넣는 칸을 모두 담고 있는지
    spots_schema = fresh_spots_schema()
    db.execute(spots_schema)
    spot_cols = column_names(db, "my_spots")
    expect("새 DB 내 콜밭 칸", spot_cols,
           ["id", "name", "lat", "lng", "peak", "memo", "created_at", "service", "area"])
    repo_src = REPO_SOURCE.read_text(encoding="utf-8")
    db_src = DB_SOURCE.read_text(encoding="utf-8")
    const = dict(re.findall(r'const val (C_[A-Z_]+) = "([^"]+)"', db_src))
    block = repo_src[repo_src.index("fun saveSpot"):repo_src.index("fun deleteSpot")]
    save_keys = re.findall(r'put\("([a-z_]+)"', block) + \
        [const[n] for n in re.findall(r'put\(LedgerDb\.(C_[A-Z_]+)', block)]
    expect("saveSpot 이 넣는 칸이 모두 DB 에 있다",
           set(save_keys) <= set(spot_cols), True)
    db.execute("INSERT INTO my_spots (name, peak, memo, created_at, service, area)"
               " VALUES ('정자역', '23:00~02:00', '', 5, 'daeri', '경기 성남시 분당구 정자동')")
    db.commit()
    expect("새 DB 내 콜밭 읽기", db.execute(
        "SELECT name, service, area FROM my_spots").fetchone(),
        ("정자역", "daeri", "경기 성남시 분당구 정자동"))
    expect("새 DB 무결성", db.execute("PRAGMA integrity_check").fetchone()[0], "ok")
    db.close()


def check_column_guard():
    """이미 새 칸이 있는 DB(새로 설치했거나 다시 올린 경우)에 또 ALTER 하지 않는다"""
    db = sqlite3.connect(":memory:")
    db.execute(T_RIDES_V2.replace("to_place TEXT NOT NULL DEFAULT ''",
                                  "to_place TEXT NOT NULL DEFAULT '',\n    via_place TEXT NOT NULL DEFAULT ''"))
    try:
        db.execute("ALTER TABLE rides ADD COLUMN via_place TEXT NOT NULL DEFAULT ''")
        duplicated = True
    except sqlite3.OperationalError:
        duplicated = False
    expect("경유 칸이 이미 있으면 ALTER 가 막힌다(그래서 hasColumn 확인 필요)", duplicated, False)

    db.execute(T_SPOTS)
    db.execute("ALTER TABLE my_spots ADD COLUMN service TEXT NOT NULL DEFAULT 'daeri'")
    try:
        db.execute("ALTER TABLE my_spots ADD COLUMN service TEXT NOT NULL DEFAULT 'daeri'")
        duplicated_spot = True
    except sqlite3.OperationalError:
        duplicated_spot = False
    expect("내 콜밭 칸이 이미 있으면 ALTER 가 막힌다", duplicated_spot, False)
    db.close()


def check_personal_no_insurance():
    """v1.0.33 — 개인콜은 건당 보험료도 고용·산재·세금(3.3%)도 붙지 않는다"""
    models = MODELS_SOURCE.read_text(encoding="utf-8")
    personal = re.search(r'PlatformDef\(\s*"personal".*?\)', models, re.S)
    expect("소스에 개인콜 정의", personal is not None, True)
    block = personal.group(0) if personal else ""
    expect("개인콜 건당 보험료 제외", "chargesPerCallInsurance = false" in block, True)
    expect("개인콜 고용·산재·세금 제외", "chargesEmploymentTax = false" in block, True)
    expect("세금 제외 판정 함수", "fun chargesEmploymentTax(" in models, True)
    # 카카오T·티맵은 건당만 빠지고 3.3% 세금은 그대로
    for pid in ("kakao", "tmap"):
        line = re.search(r'PlatformDef\("' + pid + r'".*?\),', models, re.S)
        expect(f"{pid} 건당만 제외(세금 유지)", "chargesEmploymentTax = false" not in (line.group(0) if line else ""), True)

    payout = (ROOT / "app/src/main/java/com/youngsu/daeriledger/data/Payout.kt").read_text(encoding="utf-8")
    expect("적용 세금 비율 함수", "fun employmentPercentFor(" in payout, True)
    expect("보험&세금 규칙 한 곳", "fun insuranceRuleFor(" in payout, True)
    expect("세금 비율은 Platforms 판정을 거친다",
           "if (Platforms.chargesEmploymentTax(platformId))" in payout, True)
    expect("건당 보험료도 Platforms 판정을 거친다",
           "if (Platforms.chargesPerCallInsurance(platformId))" in payout, True)

    add = ADD_RIDE.read_text(encoding="utf-8")
    expect("입력 화면이 규칙을 거쳐 계산", "Payout.insuranceRuleFor(" in add, True)
    expect("입력 화면 보험&세금 제외 라벨", "· 보험료·세금 제외" in add, True)
    expect("입력 화면 미리보기 내역", "보험료·세금 없음" in add, True)
    expect("입력 화면에 설정 세금률 직접 주입 없음",
           "employmentInsurancePercent = settings.employmentInsurancePercent" in add, False)
    expect("구분 줄 잠금은 건당 보험 유무", "binding.insuranceInput.isEnabled = !perCallExempt" in add, True)

    screen = SETTINGS_SCREEN.read_text(encoding="utf-8")
    expect("설정 안내에 개인콜 제외", "개인콜은 건당 보험료도 3.3% 세금도 붙지 않고" in screen, True)


def main():
    source_alignment()
    check_platform_source()
    check_personal_no_insurance()
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        check_v2_to_v4(Path(tmp))
        check_v1_to_v4(Path(tmp))
        check_fresh_v4(Path(tmp))
        check_column_guard()
        check_platform_db(Path(tmp))
    print("ALL CHECKS PASSED" if failures == 0 else f"{failures} CHECK(S) FAILED")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
