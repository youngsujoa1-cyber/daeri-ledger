package com.youngsu.daeriledger.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 로컬 SQLite 저장소. 운행(rides)·지출(expenses)·휴무일(off_days)·내 콜밭(my_spots) 네 테이블.
 * business_day 는 'yyyy-MM-dd' 문자열로 저장해 정렬·범위 조회가 쉽다.
 */
class LedgerDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "daeri_ledger.db"

        /**
         * v2: 휴무일(off_days) 테이블 추가 — 기존 v1 데이터는 그대로 둔다.
         * v3: 운행에 경유지(via_place) 칸 추가 — 기존 기록은 빈 문자열로 남는다.
         * v4: 내 콜밭(my_spots)에 구분(service)·지역(area) 칸 추가 — 예전에 저장한 콜밭은
         *     `대리`·빈 지역으로 남고 이름·좌표·피크·메모는 그대로다.
         */
        const val DB_VERSION = 4

        const val T_RIDES = "rides"
        const val T_EXPENSES = "expenses"
        const val T_OFF_DAYS = "off_days"

        const val C_ID = "id"
        const val C_BUSINESS_DAY = "business_day"
        const val C_TIME = "time_ms"
        const val C_PLATFORM = "platform"
        const val C_SERVICE = "service"
        const val C_PAYMENT = "payment"
        const val C_GROSS = "gross"
        const val C_FEE_PERCENT = "fee_percent"
        const val C_FEE_AMOUNT = "fee_amount"
        const val C_TIP = "tip"
        const val C_INSURANCE = "insurance_amount"
        const val C_FROM = "from_place"
        const val C_VIA = "via_place"
        const val C_TO = "to_place"
        const val C_MEMO = "memo"
        const val C_SPOT = "spot"
        const val T_SPOTS = "my_spots"
        const val C_LAT = "lat"
        const val C_LNG = "lng"
        const val C_PEAK = "peak"
        const val C_AREA = "area"
        const val C_SETTLED = "settled"
        const val C_CREATED = "created_at"
        const val C_CATEGORY = "category"
        const val C_AMOUNT = "amount"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_RIDES (
                $C_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $C_BUSINESS_DAY TEXT NOT NULL,
                $C_TIME INTEGER NOT NULL,
                $C_PLATFORM TEXT NOT NULL,
                $C_SERVICE TEXT NOT NULL,
                $C_PAYMENT TEXT NOT NULL,
                $C_GROSS INTEGER NOT NULL DEFAULT 0,
                $C_FEE_PERCENT REAL NOT NULL DEFAULT 0,
                $C_FEE_AMOUNT INTEGER NOT NULL DEFAULT 0,
                $C_TIP INTEGER NOT NULL DEFAULT 0,
                $C_INSURANCE INTEGER NOT NULL DEFAULT 0,
                $C_FROM TEXT NOT NULL DEFAULT '',
                $C_VIA TEXT NOT NULL DEFAULT '',
                $C_TO TEXT NOT NULL DEFAULT '',
                $C_MEMO TEXT NOT NULL DEFAULT '',
                $C_SPOT TEXT NOT NULL DEFAULT '',
                $C_SETTLED INTEGER NOT NULL DEFAULT 1,
                $C_CREATED INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_rides_day ON $T_RIDES($C_BUSINESS_DAY)")
        db.execSQL("CREATE INDEX idx_rides_platform ON $T_RIDES($C_PLATFORM)")
        db.execSQL(
            """
            CREATE TABLE $T_EXPENSES (
                $C_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $C_BUSINESS_DAY TEXT NOT NULL,
                $C_TIME INTEGER NOT NULL,
                $C_CATEGORY TEXT NOT NULL,
                $C_AMOUNT INTEGER NOT NULL DEFAULT 0,
                $C_MEMO TEXT NOT NULL DEFAULT '',
                $C_CREATED INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_expenses_day ON $T_EXPENSES($C_BUSINESS_DAY)")
        createOffDays(db)
        db.execSQL(
            """
            CREATE TABLE $T_SPOTS (
                $C_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                $C_LAT REAL,
                $C_LNG REAL,
                $C_PEAK TEXT NOT NULL DEFAULT '',
                $C_MEMO TEXT NOT NULL DEFAULT '',
                $C_CREATED INTEGER NOT NULL DEFAULT 0,
                $C_SERVICE TEXT NOT NULL DEFAULT 'daeri',
                $C_AREA TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 → v2: 휴무일 테이블만 더한다. 기존 운행·지출 기록은 건드리지 않는다.
        // (CREATE TABLE IF NOT EXISTS 라, 이미 만들어져 있어도 안전하다)
        if (oldVersion < 2) createOffDays(db)
        // v2 → v3: 운행에 경유지 칸만 더한다. ALTER TABLE ADD COLUMN 이라 기존 행은
        // 다시 쓰지 않고, 11,876건이 그대로 남은 채 새 칸만 빈 문자열로 채워진다.
        if (oldVersion < 3) addViaColumn(db)
        // v3 → v4: 내 콜밭에 구분·지역 칸만 더한다. 저장해 둔 콜밭 이름·좌표·피크·메모는 그대로.
        if (oldVersion < 4) addSpotColumns(db)
    }

    /** 내 콜밭에 구분(service)·지역(area) 칸 — 이미 있으면 건너뛴다 */
    private fun addSpotColumns(db: SQLiteDatabase) {
        if (!hasColumn(db, T_SPOTS, C_SERVICE)) {
            db.execSQL("ALTER TABLE $T_SPOTS ADD COLUMN $C_SERVICE TEXT NOT NULL DEFAULT 'daeri'")
        }
        if (!hasColumn(db, T_SPOTS, C_AREA)) {
            db.execSQL("ALTER TABLE $T_SPOTS ADD COLUMN $C_AREA TEXT NOT NULL DEFAULT ''")
        }
    }

    /** 경유지 칸 — 이미 있으면 건너뛴다(새로 만든 DB나 다시 올린 경우에도 안전) */
    private fun addViaColumn(db: SQLiteDatabase) {
        if (hasColumn(db, T_RIDES, C_VIA)) return
        db.execSQL("ALTER TABLE $T_RIDES ADD COLUMN $C_VIA TEXT NOT NULL DEFAULT ''")
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIndex = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (nameIndex >= 0 && c.getString(nameIndex) == column) return true
            }
            false
        }

    /** 휴무일 — 하루에 한 줄. business_day 가 기본키라 두 번 표시해도 한 줄만 남는다. */
    private fun createOffDays(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_OFF_DAYS (
                $C_BUSINESS_DAY TEXT PRIMARY KEY,
                $C_CREATED INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
