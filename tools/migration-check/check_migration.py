#!/usr/bin/env python3
"""DB v1·v2 → v3 마이그레이션 무손실 검사.

앱은 Room 대신 표준 SQLite(SQLiteOpenHelper)를 쓴다. v1.0.11 에서 운행 테이블에
경유지(via_place) 칸이 늘어 DB_VERSION 이 2 → 3 이 되었다.
이 검사는 예전 버전 스키마 그대로 DB 를 만들어 (실사용 규모 11,876건 + 지출 + 휴무일)
앱의 onUpgrade 가 하는 일을 그대로 적용한 뒤, 기존 값이 하나도 바뀌지 않았는지 본다.

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
               " VALUES ('강남역', 37.4979, 127.0276, '금·토 23시', '', 1)")
    db.commit()
    db.execute("PRAGMA user_version = 2")
    db.commit()
    return db


def column_names(db, table):
    return [r[1] for r in db.execute(f"PRAGMA table_info({table})")]


def fingerprint(db):
    """경유 칸만 빼고 전체 내용의 지문을 만든다 (마이그레이션 전후 비교용)"""
    h = hashlib.sha256()
    for table in ("rides", "expenses", "off_days", "my_spots"):
        keep = [c for c in column_names(db, table) if c != "via_place"]
        if not keep:
            continue  # 아직 없는 테이블(v1 에는 off_days 가 없다)
        sql = f"SELECT {','.join(keep)} FROM {table} ORDER BY {keep[0]}"
        for row in db.execute(sql):
            h.update(repr(row).encode())
    return h.hexdigest()


def repository_column_order():
    """LedgerRepository.insertRidesBulk 가 넣는 칸 순서 — DB 칸 순서와 같아야 한다"""
    repo_src = (ROOT / "app/src/main/java/com/youngsu/daeriledger/data/LedgerRepository.kt").read_text(
        encoding="utf-8")
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
    create_has_via = bool(re.search(
        r"\$C_FROM TEXT NOT NULL DEFAULT '',\s*\n\s*\$C_VIA TEXT NOT NULL DEFAULT ''", src))
    upgrade_has_alter = "ALTER TABLE $T_RIDES ADD COLUMN $C_VIA TEXT NOT NULL DEFAULT ''" in src
    expect("소스 DB_VERSION", version, 3)
    expect("소스 경유 칸 이름", via, "via_place")
    expect("CREATE TABLE 에 경유 칸", create_has_via, True)
    expect("onUpgrade 에 ALTER TABLE", upgrade_has_alter, True)


def migrate_v2_to_v3(path: Path):
    """앱 onUpgrade(oldVersion=2) 가 하는 일 그대로"""
    db = sqlite3.connect(path)
    db.execute("ALTER TABLE rides ADD COLUMN via_place TEXT NOT NULL DEFAULT ''")
    db.execute("PRAGMA user_version = 3")
    db.commit()
    return db


V1_SCHEMA_BEFORE = T_RIDES_V2  # v1 에도 운행 테이블은 같은 모양이었다


def check_v2_to_v3(tmp: Path):
    path = tmp / "v2.db"
    db = build_v2(path)
    before_count = db.execute("SELECT COUNT(*) FROM rides").fetchone()[0]
    before_fp = fingerprint(db)
    before_cols = column_names(db, "rides")
    db.close()

    db = migrate_v2_to_v3(path)
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
    expect("user_version", db.execute("PRAGMA user_version").fetchone()[0], 3)
    expect("무결성 검사", db.execute("PRAGMA integrity_check").fetchone()[0], "ok")

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


def check_v1_to_v3(tmp: Path):
    """v1 DB(휴무일 테이블 없음)에서 바로 올라오는 경우 — off_days 생성 + 경유 칸 추가"""
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
    db.execute("PRAGMA user_version = 1")
    db.commit()
    before = db.execute("SELECT COUNT(*) FROM rides").fetchone()[0]
    fp = fingerprint(db)
    db.close()

    db = sqlite3.connect(path)
    db.execute(T_OFF_DAYS)                                        # if (oldVersion < 2)
    if "via_place" not in column_names(db, "rides"):              # if (oldVersion < 3)
        db.execute("ALTER TABLE rides ADD COLUMN via_place TEXT NOT NULL DEFAULT ''")
    db.execute("PRAGMA user_version = 3")
    db.commit()
    expect("v1 운행 건수 유지", db.execute("SELECT COUNT(*) FROM rides").fetchone()[0], before)
    expect("v1 기존 값 지문 동일", fingerprint(db), fp)
    expect("v1 → 경유 칸 생김", column_names(db, "rides")[-1], "via_place")
    expect("v1 → 휴무일 테이블", column_names(db, "off_days"), ["business_day", "created_at"])
    expect("v1 무결성", db.execute("PRAGMA integrity_check").fetchone()[0], "ok")
    db.close()


def fresh_rides_schema():
    """LedgerDb.kt 의 CREATE TABLE rides 문을 그대로 꺼내 온다 (새로 설치한 경우의 스키마)"""
    src = DB_SOURCE.read_text(encoding="utf-8")
    const = dict(re.findall(r'const val (C_[A-Z_]+) = "([^"]+)"', src))
    block = re.search(r"CREATE TABLE \$T_RIDES \((.*?)\n\s*\)\s*\"\"\"", src, re.S).group(1)
    for name, value in const.items():
        block = block.replace("${" + name + "}", value).replace("$" + name, value)
    return "CREATE TABLE rides (" + block + ")"


def check_fresh_v3(tmp: Path):
    """새로 설치한 DB(v3 CREATE TABLE) — 경유 칸이 출발지와 도착지 사이에 있고 저장·읽기가 된다"""
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
    expect("새 DB 무결성", db.execute("PRAGMA integrity_check").fetchone()[0], "ok")
    db.close()


def check_column_guard():
    """이미 경유 칸이 있는 DB(새로 설치했거나 다시 올린 경우)에 또 ALTER 하지 않는다"""
    db = sqlite3.connect(":memory:")
    db.execute(T_RIDES_V2.replace("to_place TEXT NOT NULL DEFAULT ''",
                                  "to_place TEXT NOT NULL DEFAULT '',\n    via_place TEXT NOT NULL DEFAULT ''"))
    try:
        db.execute("ALTER TABLE rides ADD COLUMN via_place TEXT NOT NULL DEFAULT ''")
        duplicated = True
    except sqlite3.OperationalError:
        duplicated = False
    expect("경유 칸이 이미 있으면 ALTER 가 막힌다(그래서 hasColumn 확인 필요)", duplicated, False)
    db.close()


def main():
    source_alignment()
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        check_v2_to_v3(Path(tmp))
        check_v1_to_v3(Path(tmp))
        check_fresh_v3(Path(tmp))
        check_column_guard()
    print("ALL CHECKS PASSED" if failures == 0 else f"{failures} CHECK(S) FAILED")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
