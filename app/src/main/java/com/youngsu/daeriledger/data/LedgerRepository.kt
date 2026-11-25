package com.youngsu.daeriledger.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.youngsu.daeriledger.util.BizTime
import java.time.LocalDate

/** 운행·지출 저장과 집계를 담당하는 저장소 */
class LedgerRepository(context: Context) {

    private val helper = LedgerDb(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    // ---------------------------------------------------------------- rides

    fun saveRide(ride: Ride): Long {
        val values = rideValues(ride)
        return if (ride.id == 0L) {
            db.insert(LedgerDb.T_RIDES, null, values)
        } else {
            db.update(LedgerDb.T_RIDES, values, "id = ?", arrayOf(ride.id.toString()))
            ride.id
        }
    }

    fun insertRideReturning(ride: Ride): Ride {
        val id = db.insert(LedgerDb.T_RIDES, null, rideValues(ride))
        return ride.copy(id = id)
    }

    /**
     * 백업 복원처럼 한꺼번에 많은 기록을 넣을 때 — 한 트랜잭션에서 준비된 구문으로 넣는다.
     * 건당 insert 는 매번 디스크 동기화를 하므로 1만 건이면 수십 초가 걸린다.
     */
    fun insertRidesBulk(rides: List<Ride>): Int {
        if (rides.isEmpty()) return 0
        val sql = """
            INSERT INTO ${LedgerDb.T_RIDES} (
                ${LedgerDb.C_BUSINESS_DAY}, ${LedgerDb.C_TIME}, ${LedgerDb.C_PLATFORM},
                ${LedgerDb.C_SERVICE}, ${LedgerDb.C_PAYMENT}, ${LedgerDb.C_GROSS},
                ${LedgerDb.C_FEE_PERCENT}, ${LedgerDb.C_FEE_AMOUNT}, ${LedgerDb.C_TIP},
                ${LedgerDb.C_INSURANCE}, ${LedgerDb.C_FROM}, ${LedgerDb.C_VIA}, ${LedgerDb.C_TO}, ${LedgerDb.C_MEMO},
                ${LedgerDb.C_SPOT}, ${LedgerDb.C_SETTLED}, ${LedgerDb.C_CREATED}
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()
        val database = db
        val now = System.currentTimeMillis()
        var count = 0
        database.beginTransaction()
        try {
            database.compileStatement(sql).use { st ->
                rides.forEach { ride ->
                    st.clearBindings()
                    st.bindString(1, BizTime.dayKey(ride.businessDay))
                    st.bindLong(2, ride.timeMillis)
                    st.bindString(3, ride.platformId)
                    st.bindString(4, ride.service.id)
                    st.bindString(5, ride.payment.id)
                    st.bindLong(6, ride.gross.toLong())
                    st.bindDouble(7, ride.feePercent)
                    st.bindLong(8, ride.feeAmount.toLong())
                    st.bindLong(9, ride.tip.toLong())
                    st.bindLong(10, ride.insuranceAmount.toLong())
                    st.bindString(11, ride.fromPlace)
                    st.bindString(12, ride.viaPlace)
                    st.bindString(13, ride.toPlace)
                    st.bindString(14, ride.memo)
                    st.bindString(15, ride.spot)
                    st.bindLong(16, if (ride.settled) 1L else 0L)
                    st.bindLong(17, if (ride.createdAt == 0L) now else ride.createdAt)
                    st.executeInsert()
                    count++
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return count
    }

    private fun rideValues(ride: Ride): ContentValues = ContentValues().apply {
        put(LedgerDb.C_BUSINESS_DAY, BizTime.dayKey(ride.businessDay))
        put(LedgerDb.C_TIME, ride.timeMillis)
        put(LedgerDb.C_PLATFORM, ride.platformId)
        put(LedgerDb.C_SERVICE, ride.service.id)
        put(LedgerDb.C_PAYMENT, ride.payment.id)
        put(LedgerDb.C_GROSS, ride.gross)
        put(LedgerDb.C_FEE_PERCENT, ride.feePercent)
        put(LedgerDb.C_FEE_AMOUNT, ride.feeAmount)
        put(LedgerDb.C_TIP, ride.tip)
        put(LedgerDb.C_INSURANCE, ride.insuranceAmount)
        put(LedgerDb.C_FROM, ride.fromPlace)
        put(LedgerDb.C_VIA, ride.viaPlace)
        put(LedgerDb.C_TO, ride.toPlace)
        put(LedgerDb.C_MEMO, ride.memo)
        put(LedgerDb.C_SPOT, ride.spot)
        put(LedgerDb.C_SETTLED, if (ride.settled) 1 else 0)
        put(LedgerDb.C_CREATED, if (ride.createdAt == 0L) System.currentTimeMillis() else ride.createdAt)
    }

    /** 여러 저장을 한 트랜잭션으로 묶는다 — 중간에 실패하면 전부 되돌린다 (중첩 호출 가능) */
    fun <T> inTransaction(block: () -> T): T {
        val database = db
        database.beginTransaction()
        return try {
            val result = block()
            database.setTransactionSuccessful()
            result
        } finally {
            database.endTransaction()
        }
    }

    /** 운행을 전부 지우고 새 목록으로 바꾼다 — 한 트랜잭션이라 실패해도 원래 기록이 남는다 */
    fun replaceAllRides(rides: List<Ride>): Int = inTransaction {
        clearRides()
        insertRidesBulk(rides)
    }

    fun deleteRide(id: Long) {
        db.delete(LedgerDb.T_RIDES, "id = ?", arrayOf(id.toString()))
    }

    /**
     * 1.0.1 총매출 역산 도입 마이그레이션.
     * 예전에는 카카오T·티맵·핸들에 입력한 금액(이미 수수료가 빠진 실수령액)을 그대로 gross 로 저장했다.
     * 그 금액을 실수령액으로 보고 수수료율로 총매출을 역산해 gross / fee 를 다시 써 넣는다.
     * 순수익은 그대로다: gross + tip − fee = 입력했던 금액.
     */
    fun migrateNetShownGross(netShownRates: Map<String, Double>): Int {
        var changed = 0
        for ((platformId, rate) in netShownRates) {
            if (rate <= 0.0) continue
            val updates = ArrayList<Pair<Long, Pair<Int, Int>>>()
            db.rawQuery(
                "SELECT ${LedgerDb.C_ID}, ${LedgerDb.C_GROSS} FROM ${LedgerDb.T_RIDES}" +
                    " WHERE ${LedgerDb.C_PLATFORM} = ? AND ${LedgerDb.C_FEE_AMOUNT} = 0" +
                    " AND ${LedgerDb.C_FEE_PERCENT} = 0 AND ${LedgerDb.C_GROSS} > 0",
                arrayOf(platformId)
            ).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val oldGross = c.getInt(1)
                    val newGross = Payout.grossFromNet(oldGross, rate)
                    if (newGross > oldGross) {
                        updates.add(id to (newGross to (newGross - oldGross)))
                    }
                }
            }
            for ((id, values) in updates) {
                val (newGross, fee) = values
                val v = ContentValues().apply {
                    put(LedgerDb.C_GROSS, newGross)
                    put(LedgerDb.C_FEE_AMOUNT, fee)
                    put(LedgerDb.C_FEE_PERCENT, rate)
                }
                db.update(LedgerDb.T_RIDES, v, "id = ?", arrayOf(id.toString()))
                changed++
            }
        }
        return changed
    }

    fun setRideSettled(id: Long, settled: Boolean) {
        val v = ContentValues().apply { put(LedgerDb.C_SETTLED, if (settled) 1 else 0) }
        db.update(LedgerDb.T_RIDES, v, "id = ?", arrayOf(id.toString()))
    }

    fun rideById(id: Long): Ride? =
        db.query(LedgerDb.T_RIDES, null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) c.toRide() else null
        }

    /** 그 영업일의 운행 — 기록 순서대로(이른 시간이 위, 늦은 시간이 아래). */
    fun ridesForDay(day: LocalDate): List<Ride> =
        db.query(
            LedgerDb.T_RIDES, null, "${LedgerDb.C_BUSINESS_DAY} = ?",
            arrayOf(BizTime.dayKey(day)), null, null, "${LedgerDb.C_TIME} ASC, ${LedgerDb.C_ID} ASC"
        ).use { c -> c.mapRides() }

    fun ridesBetween(fromDay: LocalDate, toDay: LocalDate): List<Ride> =
        db.query(
            LedgerDb.T_RIDES, null,
            "${LedgerDb.C_BUSINESS_DAY} BETWEEN ? AND ?",
            arrayOf(BizTime.dayKey(fromDay), BizTime.dayKey(toDay)),
            null, null, "${LedgerDb.C_TIME} DESC"
        ).use { c -> c.mapRides() }

    fun allRides(): List<Ride> =
        db.query(LedgerDb.T_RIDES, null, null, null, null, null, "${LedgerDb.C_TIME} DESC")
            .use { c -> c.mapRides() }

    fun unpaidRides(): List<Ride> =
        db.query(
            LedgerDb.T_RIDES, null,
            "${LedgerDb.C_PAYMENT} = ? AND ${LedgerDb.C_SETTLED} = 0",
            arrayOf(PaymentType.UNPAID.id), null, null, "${LedgerDb.C_TIME} DESC"
        ).use { c -> c.mapRides() }

    /** 영업일별 합계를 한 번에 — 달력에서 한 달치를 그릴 때 쓴다. */
    fun daySummaries(fromDay: LocalDate, toDay: LocalDate): Map<LocalDate, DaySummary> {
        val result = LinkedHashMap<LocalDate, DaySummary>()
        val args = arrayOf(BizTime.dayKey(fromDay), BizTime.dayKey(toDay))
        val sql = """
            SELECT ${LedgerDb.C_BUSINESS_DAY},
                   COUNT(*) AS rides,
                   SUM(${LedgerDb.C_GROSS}) AS gross,
                   SUM(${LedgerDb.C_TIP}) AS tips,
                   SUM(${LedgerDb.C_FEE_AMOUNT}) AS fees,
                   SUM(${LedgerDb.C_INSURANCE}) AS insurance,
                   SUM(${LedgerDb.C_GROSS} + ${LedgerDb.C_TIP} - ${LedgerDb.C_FEE_AMOUNT} - ${LedgerDb.C_INSURANCE}) AS net,
                   MIN(${LedgerDb.C_TIME}) AS first_call,
                   MAX(${LedgerDb.C_TIME}) AS last_call,
                   SUM(CASE WHEN ${LedgerDb.C_PAYMENT} = '${PaymentType.UNPAID.id}' AND ${LedgerDb.C_SETTLED} = 0
                            THEN ${LedgerDb.C_GROSS} + ${LedgerDb.C_TIP} - ${LedgerDb.C_FEE_AMOUNT} - ${LedgerDb.C_INSURANCE} ELSE 0 END) AS unpaid_net
            FROM ${LedgerDb.T_RIDES}
            WHERE ${LedgerDb.C_BUSINESS_DAY} BETWEEN ? AND ?
            GROUP BY ${LedgerDb.C_BUSINESS_DAY}
        """.trimIndent()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                val day = BizTime.parseDay(c.getString(0)) ?: continue
                result[day] = DaySummary(
                    businessDay = day,
                    rides = c.getInt(1),
                    gross = c.getInt(2),
                    tips = c.getInt(3),
                    fees = c.getInt(4),
                    insurance = c.getInt(5),
                    net = c.getInt(6),
                    firstCallMillis = c.getLong(7),
                    lastCallMillis = c.getLong(8),
                    unpaidNet = c.getInt(9)
                )
            }
        }
        // expenses by day
        val expenseSql = """
            SELECT ${LedgerDb.C_BUSINESS_DAY}, SUM(${LedgerDb.C_AMOUNT})
            FROM ${LedgerDb.T_EXPENSES}
            WHERE ${LedgerDb.C_BUSINESS_DAY} BETWEEN ? AND ?
            GROUP BY ${LedgerDb.C_BUSINESS_DAY}
        """.trimIndent()
        db.rawQuery(expenseSql, args).use { c ->
            while (c.moveToNext()) {
                val day = BizTime.parseDay(c.getString(0)) ?: continue
                val spent = c.getInt(1)
                result[day] = (result[day] ?: DaySummary(businessDay = day)).copy(expenses = spent)
            }
        }
        return result
    }

    fun summaryForDay(day: LocalDate): DaySummary =
        summaryOf(day, ridesForDay(day), expensesForDay(day))

    /**
     * 이미 읽어 둔 목록으로 하루 합계를 계산한다.
     * 화면에서 운행·지출 목록을 어차피 읽는 경우, 같은 날을 두 번 조회하지 않도록 이걸 쓴다.
     */
    fun summaryOf(day: LocalDate, rides: List<Ride>, expenses: List<Expense>): DaySummary {
        val spent = expenses.sumOf { it.amount }
        if (rides.isEmpty()) return DaySummary(businessDay = day, expenses = spent)
        return DaySummary(
            businessDay = day,
            rides = rides.size,
            gross = rides.sumOf { it.gross },
            tips = rides.sumOf { it.tip },
            fees = rides.sumOf { it.feeAmount },
            insurance = rides.sumOf { it.insuranceAmount },
            net = rides.sumOf { it.net },
            expenses = spent,
            unpaidNet = rides.sumOf { if (it.isUnpaid) it.net else 0 },
            firstCallMillis = rides.minOf { it.timeMillis },
            lastCallMillis = rides.maxOf { it.timeMillis }
        )
    }

    /** 기간 합계 — DB 집계로 한 번에 계산한다 (모든 운행을 객체로 만들지 않는다) */
    fun summaryBetween(fromDay: LocalDate, toDay: LocalDate): DaySummary {
        val net = "(${LedgerDb.C_GROSS} + ${LedgerDb.C_TIP} - ${LedgerDb.C_FEE_AMOUNT} - ${LedgerDb.C_INSURANCE})"
        val rideSql = """
            SELECT COUNT(*),
                   IFNULL(SUM(${LedgerDb.C_GROSS}), 0),
                   IFNULL(SUM(${LedgerDb.C_TIP}), 0),
                   IFNULL(SUM(${LedgerDb.C_FEE_AMOUNT}), 0),
                   IFNULL(SUM(${LedgerDb.C_INSURANCE}), 0),
                   IFNULL(SUM($net), 0),
                   IFNULL(MIN(${LedgerDb.C_TIME}), 0),
                   IFNULL(MAX(${LedgerDb.C_TIME}), 0),
                   IFNULL(SUM(CASE WHEN ${LedgerDb.C_PAYMENT} = '${PaymentType.UNPAID.id}'
                                        AND ${LedgerDb.C_SETTLED} = 0 THEN $net ELSE 0 END), 0)
            FROM ${LedgerDb.T_RIDES}
            WHERE ${LedgerDb.C_BUSINESS_DAY} BETWEEN ? AND ?
        """.trimIndent()
        val args = arrayOf(BizTime.dayKey(fromDay), BizTime.dayKey(toDay))
        var rides = 0
        var gross = 0
        var tips = 0
        var fees = 0
        var insurance = 0
        var netSum = 0
        var firstCall = 0L
        var lastCall = 0L
        var unpaidNet = 0
        db.rawQuery(rideSql, args).use { c ->
            if (c.moveToFirst()) {
                rides = c.getInt(0)
                gross = c.getInt(1)
                tips = c.getInt(2)
                fees = c.getInt(3)
                insurance = c.getInt(4)
                netSum = c.getInt(5)
                firstCall = c.getLong(6)
                lastCall = c.getLong(7)
                unpaidNet = c.getInt(8)
            }
        }
        return DaySummary(
            businessDay = fromDay,
            rides = rides,
            gross = gross,
            tips = tips,
            fees = fees,
            insurance = insurance,
            net = netSum,
            expenses = expenseTotalBetween(fromDay, toDay),
            unpaidNet = unpaidNet,
            firstCallMillis = firstCall,
            lastCallMillis = lastCall
        )
    }

    private fun expenseTotalBetween(fromDay: LocalDate, toDay: LocalDate): Int {
        val sql = """
            SELECT IFNULL(SUM(${LedgerDb.C_AMOUNT}), 0)
            FROM ${LedgerDb.T_EXPENSES}
            WHERE ${LedgerDb.C_BUSINESS_DAY} BETWEEN ? AND ?
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(BizTime.dayKey(fromDay), BizTime.dayKey(toDay))).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** 플랫폼별 집계 — DB 에서 GROUP BY 로 계산한다 */
    fun platformStats(fromDay: LocalDate, toDay: LocalDate): List<PlatformStat> {
        val net = "(${LedgerDb.C_GROSS} + ${LedgerDb.C_TIP} - ${LedgerDb.C_FEE_AMOUNT} - ${LedgerDb.C_INSURANCE})"
        val sql = """
            SELECT ${LedgerDb.C_PLATFORM}, COUNT(*),
                   IFNULL(SUM(${LedgerDb.C_GROSS} + ${LedgerDb.C_TIP}), 0),
                   IFNULL(SUM($net), 0),
                   IFNULL(SUM(${LedgerDb.C_FEE_AMOUNT}), 0)
            FROM ${LedgerDb.T_RIDES}
            WHERE ${LedgerDb.C_BUSINESS_DAY} BETWEEN ? AND ?
            GROUP BY ${LedgerDb.C_PLATFORM}
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(BizTime.dayKey(fromDay), BizTime.dayKey(toDay))).use { c ->
            val out = ArrayList<PlatformStat>(c.count)
            while (c.moveToNext()) {
                val platformId = c.getString(0)
                out.add(
                    PlatformStat(
                        platformId = platformId,
                        label = Platforms.label(platformId),
                        rides = c.getInt(1),
                        gross = c.getInt(2),
                        net = c.getInt(3),
                        fees = c.getInt(4)
                    )
                )
            }
            out.sortedWith(compareByDescending<PlatformStat> { it.net }.thenBy { it.label })
        }
    }

    // ------------------------------------------------------------- expenses

    fun saveExpense(expense: Expense): Long {
        val values = ContentValues().apply {
            put(LedgerDb.C_BUSINESS_DAY, BizTime.dayKey(expense.businessDay))
            put(LedgerDb.C_TIME, expense.timeMillis)
            put(LedgerDb.C_CATEGORY, expense.category)
            put(LedgerDb.C_AMOUNT, expense.amount)
            put(LedgerDb.C_MEMO, expense.memo)
            put(LedgerDb.C_CREATED, if (expense.createdAt == 0L) System.currentTimeMillis() else expense.createdAt)
        }
        return if (expense.id == 0L) {
            db.insert(LedgerDb.T_EXPENSES, null, values)
        } else {
            db.update(LedgerDb.T_EXPENSES, values, "id = ?", arrayOf(expense.id.toString()))
            expense.id
        }
    }

    fun deleteExpense(id: Long) {
        db.delete(LedgerDb.T_EXPENSES, "id = ?", arrayOf(id.toString()))
    }

    /** 지출도 백업 복원에서는 한 트랜잭션으로 몰아서 넣는다 */
    fun insertExpensesBulk(expenses: List<Expense>): Int {
        if (expenses.isEmpty()) return 0
        val sql = """
            INSERT INTO ${LedgerDb.T_EXPENSES} (
                ${LedgerDb.C_BUSINESS_DAY}, ${LedgerDb.C_TIME}, ${LedgerDb.C_CATEGORY},
                ${LedgerDb.C_AMOUNT}, ${LedgerDb.C_MEMO}, ${LedgerDb.C_CREATED}
            ) VALUES (?,?,?,?,?,?)
        """.trimIndent()
        val database = db
        val now = System.currentTimeMillis()
        var count = 0
        database.beginTransaction()
        try {
            database.compileStatement(sql).use { st ->
                expenses.forEach { expense ->
                    st.clearBindings()
                    st.bindString(1, BizTime.dayKey(expense.businessDay))
                    st.bindLong(2, expense.timeMillis)
                    st.bindString(3, expense.category)
                    st.bindLong(4, expense.amount.toLong())
                    st.bindString(5, expense.memo)
                    st.bindLong(6, if (expense.createdAt == 0L) now else expense.createdAt)
                    st.executeInsert()
                    count++
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return count
    }

    fun expenseById(id: Long): Expense? =
        db.query(LedgerDb.T_EXPENSES, null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) c.toExpense() else null
        }

    /** 그 영업일의 지출 — 기록 순서대로(이른 시간이 위). */
    fun expensesForDay(day: LocalDate): List<Expense> =
        db.query(
            LedgerDb.T_EXPENSES, null, "${LedgerDb.C_BUSINESS_DAY} = ?",
            arrayOf(BizTime.dayKey(day)), null, null, "${LedgerDb.C_TIME} ASC, ${LedgerDb.C_ID} ASC"
        ).use { c -> c.mapExpenses() }

    fun expensesBetween(fromDay: LocalDate, toDay: LocalDate): List<Expense> =
        db.query(
            LedgerDb.T_EXPENSES, null,
            "${LedgerDb.C_BUSINESS_DAY} BETWEEN ? AND ?",
            arrayOf(BizTime.dayKey(fromDay), BizTime.dayKey(toDay)),
            null, null, "${LedgerDb.C_TIME} DESC"
        ).use { c -> c.mapExpenses() }

    fun allExpenses(): List<Expense> =
        db.query(LedgerDb.T_EXPENSES, null, null, null, null, null, "${LedgerDb.C_TIME} DESC")
            .use { c -> c.mapExpenses() }

    fun expenseTotalsByCategory(fromDay: LocalDate, toDay: LocalDate): List<Pair<String, Int>> {
        val sql = """
            SELECT ${LedgerDb.C_CATEGORY}, SUM(${LedgerDb.C_AMOUNT}) AS total
            FROM ${LedgerDb.T_EXPENSES}
            WHERE ${LedgerDb.C_BUSINESS_DAY} BETWEEN ? AND ?
            GROUP BY ${LedgerDb.C_CATEGORY}
            ORDER BY total DESC
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(BizTime.dayKey(fromDay), BizTime.dayKey(toDay))).use { c ->
            val out = ArrayList<Pair<String, Int>>()
            while (c.moveToNext()) out.add(c.getString(0) to c.getInt(1))
            out
        }
    }

    fun firstBusinessDay(): LocalDate? {
        db.rawQuery("SELECT MIN(${LedgerDb.C_BUSINESS_DAY}) FROM ${LedgerDb.T_RIDES}", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return BizTime.parseDay(c.getString(0))
        }
        db.rawQuery("SELECT MIN(${LedgerDb.C_BUSINESS_DAY}) FROM ${LedgerDb.T_EXPENSES}", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return BizTime.parseDay(c.getString(0))
        }
        return null
    }

    fun counts(): Pair<Int, Int> {
        val rides = db.rawQuery("SELECT COUNT(*) FROM ${LedgerDb.T_RIDES}", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        val exps = db.rawQuery("SELECT COUNT(*) FROM ${LedgerDb.T_EXPENSES}", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        return rides to exps
    }

    fun clearAll() {
        db.delete(LedgerDb.T_RIDES, null, null)
        db.delete(LedgerDb.T_EXPENSES, null, null)
        db.delete(LedgerDb.T_OFF_DAYS, null, null)
    }

    /** 운행만 지운다 (다른 앱 백업을 '교체'로 가져올 때 — 이 앱에서 적은 지출은 남긴다) */
    fun clearRides() {
        db.delete(LedgerDb.T_RIDES, null, null)
    }

    // -------------------------------------------------------------- off days

    /** 그 날을 휴무로 표시했는지 */
    fun isOffDay(day: LocalDate): Boolean =
        db.rawQuery(
            "SELECT 1 FROM ${LedgerDb.T_OFF_DAYS} WHERE ${LedgerDb.C_BUSINESS_DAY} = ? LIMIT 1",
            arrayOf(BizTime.dayKey(day))
        ).use { it.moveToFirst() }

    /** 휴무 표시를 켜거나 끈다 — 끄면 그 줄을 지운다 */
    fun setOffDay(day: LocalDate, isOff: Boolean) {
        if (isOff) {
            val values = ContentValues().apply {
                put(LedgerDb.C_BUSINESS_DAY, BizTime.dayKey(day))
                put(LedgerDb.C_CREATED, System.currentTimeMillis())
            }
            db.insertWithOnConflict(LedgerDb.T_OFF_DAYS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            db.delete(
                LedgerDb.T_OFF_DAYS, "${LedgerDb.C_BUSINESS_DAY} = ?", arrayOf(BizTime.dayKey(day))
            )
        }
    }

    /** 켜져 있으면 끄고, 꺼져 있으면 켜다. 바뀐 뒤 상태(휴무 = true)를 돌려준다. */
    fun toggleOffDay(day: LocalDate): Boolean {
        val next = !isOffDay(day)
        setOffDay(day, next)
        return next
    }

    fun offDaysBetween(fromDay: LocalDate, toDay: LocalDate): Set<LocalDate> {
        val out = LinkedHashSet<LocalDate>()
        db.query(
            LedgerDb.T_OFF_DAYS, arrayOf(LedgerDb.C_BUSINESS_DAY),
            "${LedgerDb.C_BUSINESS_DAY} BETWEEN ? AND ?",
            arrayOf(BizTime.dayKey(fromDay), BizTime.dayKey(toDay)),
            null, null, "${LedgerDb.C_BUSINESS_DAY} ASC"
        ).use { c ->
            while (c.moveToNext()) {
                BizTime.parseDay(c.getString(0))?.let { out.add(it) }
            }
        }
        return out
    }

    fun allOffDays(): List<LocalDate> =
        db.query(
            LedgerDb.T_OFF_DAYS, arrayOf(LedgerDb.C_BUSINESS_DAY),
            null, null, null, null, "${LedgerDb.C_BUSINESS_DAY} ASC"
        ).use { c ->
            val out = ArrayList<LocalDate>(c.count)
            while (c.moveToNext()) BizTime.parseDay(c.getString(0))?.let { out.add(it) }
            out
        }

    /** 백업 복원·타 앱 가져오기처럼 한꺼번에 넣을 때 — 이미 있는 날짜는 건건이 건너뛰다 */
    fun insertOffDaysBulk(days: Collection<LocalDate>): Int {
        if (days.isEmpty()) return 0
        val sql = "INSERT OR IGNORE INTO ${LedgerDb.T_OFF_DAYS} " +
            "(${LedgerDb.C_BUSINESS_DAY}, ${LedgerDb.C_CREATED}) VALUES (?, ?)"
        val database = db
        val now = System.currentTimeMillis()
        var count = 0
        database.beginTransaction()
        try {
            database.compileStatement(sql).use { st ->
                days.distinct().forEach { day ->
                    st.clearBindings()
                    st.bindString(1, BizTime.dayKey(day))
                    st.bindLong(2, now)
                    count += st.executeInsert().let { if (it == -1L) 0 else 1 }
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return count
    }

    fun clearOffDays() {
        db.delete(LedgerDb.T_OFF_DAYS, null, null)
    }

    // ---------------------------------------------------------------- spots

    fun saveSpot(spot: MySpot): Long {
        val values = ContentValues().apply {
            put("name", spot.name)
            if (spot.lat != null) put(LedgerDb.C_LAT, spot.lat) else putNull(LedgerDb.C_LAT)
            if (spot.lng != null) put(LedgerDb.C_LNG, spot.lng) else putNull(LedgerDb.C_LNG)
            put(LedgerDb.C_PEAK, spot.peak)
            put(LedgerDb.C_MEMO, spot.memo)
            put(LedgerDb.C_SERVICE, spot.service.id)
            put(LedgerDb.C_AREA, spot.area)
            put(LedgerDb.C_CREATED, if (spot.createdAt == 0L) System.currentTimeMillis() else spot.createdAt)
        }
        return if (spot.id == 0L) {
            db.insert(LedgerDb.T_SPOTS, null, values)
        } else {
            db.update(LedgerDb.T_SPOTS, values, "id = ?", arrayOf(spot.id.toString()))
            spot.id
        }
    }

    fun deleteSpot(id: Long) {
        db.delete(LedgerDb.T_SPOTS, "id = ?", arrayOf(id.toString()))
    }

    fun allSpots(): List<MySpot> =
        db.query(LedgerDb.T_SPOTS, null, null, null, null, null, "name ASC").use { c ->
            val out = ArrayList<MySpot>(c.count)
            while (c.moveToNext()) {
                out.add(
                    MySpot(
                        id = c.getLong(c.getColumnIndexOrThrow(LedgerDb.C_ID)),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        lat = if (c.isNull(c.getColumnIndexOrThrow(LedgerDb.C_LAT))) null
                        else c.getDouble(c.getColumnIndexOrThrow(LedgerDb.C_LAT)),
                        lng = if (c.isNull(c.getColumnIndexOrThrow(LedgerDb.C_LNG))) null
                        else c.getDouble(c.getColumnIndexOrThrow(LedgerDb.C_LNG)),
                        peak = c.getString(c.getColumnIndexOrThrow(LedgerDb.C_PEAK)),
                        memo = c.getString(c.getColumnIndexOrThrow(LedgerDb.C_MEMO)),
                        createdAt = c.getLong(c.getColumnIndexOrThrow(LedgerDb.C_CREATED)),
                        service = ServiceType.from(c.getString(c.getColumnIndexOrThrow(LedgerDb.C_SERVICE))),
                        area = c.getString(c.getColumnIndexOrThrow(LedgerDb.C_AREA))
                    )
                )
            }
            out
        }

    /** 기록에 남은 콜밭 태그를 콜 수·순수익 순으로 집계 */
    fun spotStats(fromDay: LocalDate, toDay: LocalDate, limit: Int = 10): List<SpotStat> {
        val sql = """
            SELECT TRIM(${LedgerDb.C_SPOT}) AS spot,
                   COUNT(*) AS rides,
                   SUM(${LedgerDb.C_GROSS} + ${LedgerDb.C_TIP} - ${LedgerDb.C_FEE_AMOUNT} - ${LedgerDb.C_INSURANCE}) AS net,
                   SUM(${LedgerDb.C_GROSS} + ${LedgerDb.C_TIP}) AS fares
            FROM ${LedgerDb.T_RIDES}
            WHERE ${LedgerDb.C_BUSINESS_DAY} BETWEEN ? AND ? AND TRIM(${LedgerDb.C_SPOT}) != ''
            GROUP BY TRIM(${LedgerDb.C_SPOT})
            ORDER BY net DESC, rides DESC
            LIMIT ?
        """.trimIndent()
        return db.rawQuery(
            sql,
            arrayOf(BizTime.dayKey(fromDay), BizTime.dayKey(toDay), limit.toString())
        ).use { c ->
            val out = ArrayList<SpotStat>()
            while (c.moveToNext()) {
                out.add(SpotStat(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3)))
            }
            out
        }
    }

    /** 최근 기록에 쓴 콜밭 태그 (입력 화면 추천 칩) */
    fun recentSpotTags(limit: Int = 8): List<String> {
        val sql = """
            SELECT TRIM(${LedgerDb.C_SPOT}) AS spot, COUNT(*) AS c, MAX(${LedgerDb.C_CREATED}) AS last_used
            FROM ${LedgerDb.T_RIDES}
            WHERE TRIM(${LedgerDb.C_SPOT}) != ''
            GROUP BY TRIM(${LedgerDb.C_SPOT})
            ORDER BY last_used DESC
            LIMIT ?
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(limit.toString())).use { c ->
            val out = ArrayList<String>()
            while (c.moveToNext()) out.add(c.getString(0))
            out
        }
    }

    fun ridesForSpot(spot: String): List<Ride> =
        db.query(
            LedgerDb.T_RIDES, null, "TRIM(${LedgerDb.C_SPOT}) = ?",
            arrayOf(spot.trim()), null, null, "${LedgerDb.C_TIME} DESC"
        ).use { c -> c.mapRides() }

    // -------------------------------------------------------------- mapping

    private fun Cursor.mapRides(): List<Ride> {
        val out = ArrayList<Ride>(count)
        while (moveToNext()) out.add(toRide())
        return out
    }

    private fun Cursor.mapExpenses(): List<Expense> {
        val out = ArrayList<Expense>(count)
        while (moveToNext()) out.add(toExpense())
        return out
    }

    private fun Cursor.toRide(): Ride = Ride(
        id = getLong(getColumnIndexOrThrow(LedgerDb.C_ID)),
        businessDay = BizTime.parseDay(getString(getColumnIndexOrThrow(LedgerDb.C_BUSINESS_DAY)))
            ?: LocalDate.now(),
        timeMillis = getLong(getColumnIndexOrThrow(LedgerDb.C_TIME)),
        platformId = getString(getColumnIndexOrThrow(LedgerDb.C_PLATFORM)),
        service = ServiceType.from(getString(getColumnIndexOrThrow(LedgerDb.C_SERVICE))),
        payment = PaymentType.from(getString(getColumnIndexOrThrow(LedgerDb.C_PAYMENT))),
        gross = getInt(getColumnIndexOrThrow(LedgerDb.C_GROSS)),
        feePercent = getDouble(getColumnIndexOrThrow(LedgerDb.C_FEE_PERCENT)),
        feeAmount = getInt(getColumnIndexOrThrow(LedgerDb.C_FEE_AMOUNT)),
        tip = getInt(getColumnIndexOrThrow(LedgerDb.C_TIP)),
        insuranceAmount = getInt(getColumnIndexOrThrow(LedgerDb.C_INSURANCE)),
        fromPlace = getString(getColumnIndexOrThrow(LedgerDb.C_FROM)),
        viaPlace = getString(getColumnIndexOrThrow(LedgerDb.C_VIA)).orEmpty(),
        toPlace = getString(getColumnIndexOrThrow(LedgerDb.C_TO)),
        memo = getString(getColumnIndexOrThrow(LedgerDb.C_MEMO)),
        spot = getString(getColumnIndexOrThrow(LedgerDb.C_SPOT)),
        settled = getInt(getColumnIndexOrThrow(LedgerDb.C_SETTLED)) == 1,
        createdAt = getLong(getColumnIndexOrThrow(LedgerDb.C_CREATED))
    )

    private fun Cursor.toExpense(): Expense = Expense(
        id = getLong(getColumnIndexOrThrow(LedgerDb.C_ID)),
        businessDay = BizTime.parseDay(getString(getColumnIndexOrThrow(LedgerDb.C_BUSINESS_DAY)))
            ?: LocalDate.now(),
        timeMillis = getLong(getColumnIndexOrThrow(LedgerDb.C_TIME)),
        category = getString(getColumnIndexOrThrow(LedgerDb.C_CATEGORY)),
        amount = getInt(getColumnIndexOrThrow(LedgerDb.C_AMOUNT)),
        memo = getString(getColumnIndexOrThrow(LedgerDb.C_MEMO)),
        createdAt = getLong(getColumnIndexOrThrow(LedgerDb.C_CREATED))
    )
}
