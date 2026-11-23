package com.youngsu.daeriledger.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.youngsu.daeriledger.settings.AppSettings
import com.youngsu.daeriledger.util.BizTime
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/**
 * CSV 내보내기 / JSON 백업·복원 (Android SAF URI 사용).
 */
class BackupManager(
    private val context: Context,
    private val repo: LedgerRepository,
    private val settings: AppSettings
) {

    data class RestoreResult(
        val rides: Int,
        val expenses: Int,
        val skipped: Int,
        val offDays: Int = 0
    )

    /** 다른 앱(대리 가계부) 백업에서 가져온 결과 */
    data class ImportResult(val rides: Int, val skipped: Int, val offDays: Int = 0)

    // ------------------------------------------------------------------ CSV

    fun exportCsv(uri: Uri): Int {
        val rides = repo.allRides()
        val expenses = repo.allExpenses()
        val lineBreak = "\r\n"
        val sb = StringBuilder()
        // 엑셀이 한글을 바로 읽도록 BOM
        sb.append('\uFEFF')
        sb.append(
            listOf(
                "종류", "영업일", "시각", "플랫폼", "구분", "결제", "총매출", "팁·경유비",
                "수수료(%)", "수수료", "보험료", "순수익", "출발", "도착", "콜밭", "내용", "정산"
            ).joinToString(",")
        ).append(lineBreak)

        rides.forEach { r ->
            sb.append(
                listOf(
                    "운행",
                    BizTime.dayKey(r.businessDay),
                    BizTime.clockLabel(r.timeMillis),
                    r.platformLabel,
                    r.service.label,
                    r.payment.label,
                    r.gross.toString(),
                    r.tip.toString(),
                    String.format(java.util.Locale.KOREA, "%.1f", r.feePercent),
                    r.feeAmount.toString(),
                    r.insuranceAmount.toString(),
                    r.net.toString(),
                    r.fromPlace,
                    r.toPlace,
                    r.spot,
                    r.memo,
                    if (r.isUnpaid) "미수" else "완료"
                ).joinToString(",") { escape(it) }
            ).append(lineBreak)
        }
        expenses.forEach { e ->
            sb.append(
                listOf(
                    "지출",
                    BizTime.dayKey(e.businessDay),
                    BizTime.clockLabel(e.timeMillis),
                    e.category,
                    "-",
                    "-",
                    e.amount.toString(),
                    "0",
                    "0",
                    "0",
                    "0",
                    (-e.amount).toString(),
                    "",
                    "",
                    "",
                    e.memo,
                    "-"
                ).joinToString(",") { escape(it) }
            ).append(lineBreak)
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            OutputStreamWriter(out, Charsets.UTF_8).use { it.write(sb.toString()) }
        } ?: throw IllegalStateException("파일을 열 수 없습니다")

        return rides.size + expenses.size
    }

    private fun escape(value: String): String {
        val clean = value.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(',') || clean.contains('"')) {
            "\"" + clean.replace("\"", "\"\"") + "\""
        } else clean
    }

    // ----------------------------------------------------------------- JSON

    fun backupJson(uri: Uri): Pair<Int, Int> {
        val root = JSONObject()
        root.put("app", "대리장부")
        root.put("schema", 1)
        root.put("exportedAt", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            java.time.OffsetDateTime.now()
        ))

        val s = JSONObject()
        s.put("cutoffHour", settings.cutoffHour)
        s.put("dailyGoal", settings.dailyGoal)
        s.put("insuranceDaeri", settings.insuranceDaeri)
        s.put("insuranceTaksong", settings.insuranceTaksong)
        s.put("employmentInsurancePercent", settings.employmentInsurancePercent)
        val fees = JSONObject()
        Platforms.ALL.forEach { p ->
            fees.put(p.id, JSONObject().apply {
                put("percent", settings.platformFeePercent(p.id))
                put("netShown", settings.platformNetShown(p.id))
            })
        }
        s.put("platformFees", fees)
        root.put("settings", s)

        val rides = repo.allRides()
        val rideArr = JSONArray()
        rides.forEach { r ->
            rideArr.put(JSONObject().apply {
                put("businessDay", BizTime.dayKey(r.businessDay))
                put("timeMillis", r.timeMillis)
                put("platform", r.platformId)
                put("service", r.service.id)
                put("payment", r.payment.id)
                put("gross", r.gross)
                put("feePercent", r.feePercent)
                put("feeAmount", r.feeAmount)
                put("tip", r.tip)
                put("insuranceAmount", r.insuranceAmount)
                put("fromPlace", r.fromPlace)
                put("toPlace", r.toPlace)
                put("memo", r.memo)
                put("spot", r.spot)
                put("settled", r.settled)
                put("createdAt", r.createdAt)
            })
        }
        root.put("rides", rideArr)

        val expenses = repo.allExpenses()
        val expArr = JSONArray()
        expenses.forEach { e ->
            expArr.put(JSONObject().apply {
                put("businessDay", BizTime.dayKey(e.businessDay))
                put("timeMillis", e.timeMillis)
                put("category", e.category)
                put("amount", e.amount)
                put("memo", e.memo)
                put("createdAt", e.createdAt)
            })
        }
        root.put("expenses", expArr)

        val spots = repo.allSpots()
        val spotArr = JSONArray()
        spots.forEach { sp ->
            spotArr.put(JSONObject().apply {
                put("name", sp.name)
                sp.lat?.let { put("lat", it) }
                sp.lng?.let { put("lng", it) }
                put("peak", sp.peak)
                put("memo", sp.memo)
                put("createdAt", sp.createdAt)
            })
        }
        root.put("mySpots", spotArr)

        // 휴무일 — 운행 기록이 없는 ‘일하지 않은 날’ 표시
        val offArr = JSONArray()
        repo.allOffDays().forEach { day -> offArr.put(BizTime.dayKey(day)) }
        root.put("offDays", offArr)

        context.contentResolver.openOutputStream(uri)?.use { out ->
            OutputStreamWriter(out, Charsets.UTF_8).use { it.write(root.toString(2)) }
        } ?: throw IllegalStateException("파일을 열 수 없습니다")

        return rides.size to expenses.size
    }

    fun restoreJson(uri: Uri, replace: Boolean): RestoreResult {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(input.reader(Charsets.UTF_8)).readText()
        } ?: throw IllegalStateException("파일을 열 수 없습니다")

        val root = JSONObject(text)
        if (!root.has("rides")) throw IllegalArgumentException("대리장부 백업 파일이 아닙니다")

        val rideKeys = HashSet<String>()
        val expenseKeys = HashSet<String>()
        if (!replace) {
            repo.allRides().forEach { rideKeys.add(rideKey(it)) }
            repo.allExpenses().forEach { expenseKeys.add(expenseKey(it)) }
        }

        var skipped = 0

        root.optJSONObject("settings")?.let { readSettings(it) }

        val pendingRides = ArrayList<Ride>()
        val rides = root.optJSONArray("rides") ?: JSONArray()
        for (i in 0 until rides.length()) {
            val o = rides.optJSONObject(i) ?: continue
            val day = BizTime.parseDay(o.optString("businessDay")) ?: continue
            val ride = Ride(
                businessDay = day,
                timeMillis = o.optLong("timeMillis"),
                platformId = o.optString("platform", "personal"),
                service = ServiceType.from(o.optString("service")),
                payment = PaymentType.from(o.optString("payment")),
                gross = o.optInt("gross"),
                tip = o.optInt("tip"),
                feePercent = o.optDouble("feePercent", 0.0),
                feeAmount = o.optInt("feeAmount"),
                insuranceAmount = o.optInt("insuranceAmount"),
                fromPlace = o.optString("fromPlace"),
                toPlace = o.optString("toPlace"),
                memo = o.optString("memo"),
                spot = o.optString("spot"),
                settled = o.optBoolean("settled", true),
                createdAt = o.optLong("createdAt")
            )
            val key = rideKey(ride)
            if (!replace && key in rideKeys) { skipped++; continue }
            rideKeys.add(key)
            pendingRides.add(ride)
        }
        val pendingExpenses = ArrayList<Expense>()
        val expenses = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until expenses.length()) {
            val o = expenses.optJSONObject(i) ?: continue
            val day = BizTime.parseDay(o.optString("businessDay")) ?: continue
            val expense = Expense(
                businessDay = day,
                timeMillis = o.optLong("timeMillis"),
                category = o.optString("category", "기타"),
                amount = o.optInt("amount"),
                memo = o.optString("memo"),
                createdAt = o.optLong("createdAt")
            )
            val key = expenseKey(expense)
            if (!replace && key in expenseKeys) { skipped++; continue }
            expenseKeys.add(key)
            pendingExpenses.add(expense)
        }

        // 휴무일 — 예전 백업에는 없으므로 없으면 그냥 건너뛴다
        val pendingOffDays = LinkedHashSet<LocalDate>()
        val existingOffDays = if (replace) emptySet<LocalDate>() else repo.allOffDays().toHashSet()
        root.optJSONArray("offDays")?.let { arr ->
            for (i in 0 until arr.length()) {
                val day = BizTime.parseDay(arr.optString(i)) ?: continue
                if (day in existingOffDays) continue
                pendingOffDays.add(day)
            }
        }

        // 교체일 때는 '지우기 + 넣기' 를 한 트랜잭션으로 — 중간에 실패해도 원래 기록이 남는다
        val (addedRides, addedExpenses) = repo.inTransaction {
            if (replace) repo.clearAll()
            repo.insertRidesBulk(pendingRides) to repo.insertExpensesBulk(pendingExpenses)
        }
        val addedOffDays = repo.insertOffDaysBulk(pendingOffDays)

        // 내 콜밭 저장 목록
        val existingSpotNames = repo.allSpots().map { it.name.trim() }.toHashSet()
        root.optJSONArray("mySpots")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isEmpty() || name in existingSpotNames) continue
                repo.saveSpot(
                    MySpot(
                        name = name,
                        lat = if (o.has("lat")) o.optDouble("lat") else null,
                        lng = if (o.has("lng")) o.optDouble("lng") else null,
                        peak = o.optString("peak"),
                        memo = o.optString("memo"),
                        createdAt = o.optLong("createdAt")
                    )
                )
                existingSpotNames.add(name)
            }
        }

        return RestoreResult(addedRides, addedExpenses, skipped, addedOffDays)
    }

    // ------------------------------------------ 다른 앱(대리 가계부) 백업 가져오기

    /**
     * 대리 가계부(dcb_teras) 백업 가져오기.
     *
     * .zip 안의 dcb_teras.db 를 꺼내거나, .db 파일을 그대로 골라도 된다.
     * T_CALL = 운행 한 건, T_CASH = 그 운행의 금액 항목이다.
     *  - ITEM_ID 1        → 총매출(gross)
     *  - ITEM_ID 2, 3     → 팁 · 경유비(tip)
     *  - ITEM_ID 4        → 수수료(fee)
     *  - ITEM_ID 25       → 보험료
     *  - ITEM_ID 5, 26~28 → 기타 공제 (순수익이 그대로 유지되게 수수료에 합산)
     * CALL_OFF_CHK = 1 은 그날 일하지 않았다는 ‘휴무’ 표시다 — 운행으로 넣지 않고,
     * 그 날짜를 이 앱의 휴무일(off_days)로 함께 가져온다.
     * CALL_WORK_DATE 를 그대로 영업일로 쓴다
     * (원본도 새벽 콜을 전날 영업일로 묶어 둔다).
     * 1만 건이 넘어도 한 트랜잭션으로 넣어 1~2초 안에 끝난다.
     */
    fun importDcbBackup(uri: Uri, replace: Boolean): ImportResult {
        val tempDb = File(context.cacheDir, "dcb_import_${System.currentTimeMillis()}.db")
        var database: SQLiteDatabase? = null
        try {
            copyBackupDatabase(uri, tempDb)
            database = try {
                SQLiteDatabase.openDatabase(tempDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: Exception) {
                throw IllegalArgumentException("대리 가계부 백업 파일을 열 수 없어요", e)
            }

            val rides = readDcbRides(database)
            val offDays = readDcbOffDays(database)
            if (rides.isEmpty() && offDays.isEmpty()) {
                throw IllegalArgumentException("가져올 운행 기록이 없어요")
            }

            // 같은 기록이 두 번 들어가지 않게: 파일 안 중복 + 이미 있는 기록 둘 다 거른다
            // (플랫폼 이름은 원본에 없어서 늘 개인콜로 들어오므로, 중복 판정에서는 뺀다)
            val unique = LinkedHashMap<String, Ride>(rides.size * 2)
            rides.forEach { ride -> unique.putIfAbsent(dcbKey(ride), ride) }

            val existingKeys = HashSet<String>()
            if (!replace) {
                repo.allRides().forEach { existingKeys.add(dcbKey(it)) }
            }

            val toInsert = ArrayList<Ride>(unique.size)
            var skipped = 0
            for ((key, ride) in unique) {
                if (key in existingKeys) skipped++ else toInsert.add(ride)
            }

            val inserted = if (replace) repo.replaceAllRides(toInsert) else repo.insertRidesBulk(toInsert)
            // 휴무일은 ‘추가’ — 교체로 가져와도 이 앱에서 직접 표시해 둔 휴무는 지우지 않는다
            val addedOffDays = repo.insertOffDaysBulk(offDays)
            return ImportResult(inserted, skipped, addedOffDays)
        } finally {
            runCatching { database?.close() }
            runCatching { if (tempDb.exists()) tempDb.delete() }
        }
    }

    /** 고른 파일이 zip 이면 안의 .db 를, 아니면 파일 자체를 임시 파일로 꺼낸다 */
    private fun copyBackupDatabase(uri: Uri, out: File) {
        val raw = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("파일을 열 수 없어요")
        raw.use {
            val input = BufferedInputStream(raw)
            input.mark(4)
            val head = ByteArray(4)
            val read = input.read(head)
            input.reset()
            val isZip = read >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
            if (isZip) extractDbFromZip(input, out) else out.outputStream().use { input.copyTo(it) }
        }
    }

    private fun extractDbFromZip(zipStream: InputStream, out: File) {
        ZipInputStream(zipStream).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory && name.endsWith(".db")) {
                    out.outputStream().use { fileOut -> zin.copyTo(fileOut) }
                    return
                }
                entry = zin.nextEntry
            }
        }
        throw IllegalArgumentException("zip 안에서 dcb_teras.db 를 찾지 못했어요")
    }

    private fun readDcbRides(db: SQLiteDatabase): List<Ride> {
        val sql = """
            SELECT c._id,
                   c.CALL_WORK_DATE, c.CALL_APP, c.CALL_ORDER_DATETIME,
                   c.CALL_START, c.CALL_END, c.CALL_VISIT, c.CALL_REMARK,
                   IFNULL(SUM(CASE WHEN t.ITEM_ID = 1 THEN t.CASH_MONEY ELSE 0 END), 0) AS gross,
                   IFNULL(SUM(CASE WHEN t.ITEM_ID IN (2, 3) THEN t.CASH_MONEY ELSE 0 END), 0) AS tip,
                   IFNULL(SUM(CASE WHEN t.ITEM_ID = 4 THEN t.CASH_MONEY ELSE 0 END), 0) AS fee,
                   IFNULL(SUM(CASE WHEN t.ITEM_ID = 25 THEN t.CASH_MONEY ELSE 0 END), 0) AS insurance,
                   IFNULL(SUM(CASE WHEN t.ITEM_ID IN (5, 26, 27, 28) THEN t.CASH_MONEY ELSE 0 END), 0) AS etc_fee
            FROM T_CALL c
            LEFT JOIN T_CASH t ON t.CALL_ID = c._id
            WHERE IFNULL(c.CALL_OFF_CHK, 0) = 0
            GROUP BY c._id
            ORDER BY c._id
        """.trimIndent()
        val out = ArrayList<Ride>(12_000)
        try {
            db.rawQuery(sql, null).use { c ->
                while (c.moveToNext()) {
                    val orderText = c.getString(3)
                    val day = DcbBackup.businessDay(c.getString(1), orderText) ?: continue
                    val gross = c.getInt(8)
                    val tip = c.getInt(9)
                    val fee = c.getInt(10) + c.getInt(12)
                    val insurance = c.getInt(11)
                    out.add(
                        Ride(
                            businessDay = day,
                            timeMillis = DcbBackup.timeMillis(orderText, day),
                            platformId = DcbBackup.platformId(c.getString(2)),
                            service = ServiceType.DAERI,
                            payment = PaymentType.CASH,
                            gross = gross,
                            feePercent = DcbBackup.feePercent(gross, fee),
                            feeAmount = fee,
                            tip = tip,
                            insuranceAmount = insurance,
                            fromPlace = c.getString(4)?.trim().orEmpty(),
                            toPlace = c.getString(5)?.trim().orEmpty(),
                            memo = DcbBackup.memo(c.getString(6), c.getString(7)),
                            spot = "",
                            settled = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("대리 가계부 백업 형식이 아니에요", e)
        }
        return out
    }

    /**
     * 휴무일 — CALL_OFF_CHK = 1 인 행의 작업일. 원본은 범위가 비어 있는 행으로만 남긴다.
     * (예전에 ‘지운 콜’로 읽었지만, 실제로는 그날 일하지 않았다는 표시다)
     */
    private fun readDcbOffDays(db: SQLiteDatabase): List<LocalDate> {
        val sql = """
            SELECT DISTINCT CALL_WORK_DATE
            FROM T_CALL
            WHERE IFNULL(CALL_OFF_CHK, 0) = 1
            ORDER BY CALL_WORK_DATE
        """.trimIndent()
        val out = LinkedHashSet<LocalDate>()
        runCatching {
            db.rawQuery(sql, null).use { c ->
                while (c.moveToNext()) {
                    DcbBackup.businessDay(c.getString(0), null)?.let { out.add(it) }
                }
            }
        }
        return out.toList()
    }

    private fun readSettings(o: JSONObject) {
        if (o.has("cutoffHour")) settings.cutoffHour = o.optInt("cutoffHour", 12)
        if (o.has("dailyGoal")) settings.dailyGoal = o.optInt("dailyGoal", 150_000)
        if (o.has("insuranceDaeri")) settings.insuranceDaeri = o.optInt("insuranceDaeri")
        if (o.has("insuranceTaksong")) settings.insuranceTaksong = o.optInt("insuranceTaksong")
        if (o.has("employmentInsurancePercent")) {
            settings.employmentInsurancePercent = o.optDouble("employmentInsurancePercent", 0.0)
        }
        o.optJSONObject("platformFees")?.let { fees ->
            Platforms.ALL.forEach { p ->
                fees.optJSONObject(p.id)?.let { entry ->
                    if (entry.has("percent")) settings.setPlatformFeePercent(p.id, entry.optDouble("percent"))
                    if (entry.has("netShown")) settings.setPlatformNetShown(p.id, entry.optBoolean("netShown"))
                }
            }
        }
    }

    private fun rideKey(ride: Ride): String =
        listOf(
            BizTime.dayKey(ride.businessDay), ride.timeMillis, ride.platformId,
            ride.gross, ride.tip, ride.payment.id
        ).joinToString("|")

    /** 타 앱에서 가져온 기록의 중복 판정 키 — 영업일 · 시각 · 요금 · 팁 · 수수료 */
    private fun dcbKey(ride: Ride): String =
        listOf(
            BizTime.dayKey(ride.businessDay), ride.timeMillis,
            ride.gross, ride.tip, ride.feeAmount
        ).joinToString("|")

    private fun expenseKey(expense: Expense): String =
        listOf(
            BizTime.dayKey(expense.businessDay), expense.timeMillis,
            expense.category, expense.amount
        ).joinToString("|")

    companion object {
        fun csvFileName(): String {
            val now = LocalDate.now()
            return "대리장부_${now.year}${"%02d".format(now.monthValue)}${"%02d".format(now.dayOfMonth)}.csv"
        }

        fun jsonFileName(): String {
            val now = LocalDate.now()
            return "대리장부_백업_${now.year}${"%02d".format(now.monthValue)}${"%02d".format(now.dayOfMonth)}.json"
        }
    }
}
