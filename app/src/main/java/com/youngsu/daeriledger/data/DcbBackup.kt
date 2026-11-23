package com.youngsu.daeriledger.data

import com.youngsu.daeriledger.util.BizTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 대리 가계부(dcb_teras) 백업의 값 → 대리장부 값 변환 규칙.
 *
 * DB 를 읽는 코드와 섞지 않고 순수 함수로 모아 두어, 안드로이드 없이 검증한다. (tools/logic-check)
 * 원본 구조:
 *  - T_CALL  : 운행 한 건 (CALL_WORK_DATE 영업일, CALL_ORDER_DATETIME 주문시각,
 *              CALL_START/END 출발·도착, CALL_VISIT/REMARK 메모, CALL_OFF_CHK 1 = 그날 휴무)
 *  - T_CASH  : 그 운행의 금액 항목 (ITEM_ID 1 요금 / 2 경유 / 3 팁 / 4 수수료 / 25 보험료 …)
 */
object DcbBackup {

    private val ORDER_FORMATS = listOf(
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSS"
    )

    /**
     * 영업일 — 원본의 CALL_WORK_DATE 를 그대로 쓴다.
     * (원본도 00:02 새벽 콜을 전날 영업일로 묶어 두기 때문에 다시 계산하지 않는다)
     */
    fun businessDay(workDate: String?, orderDateTime: String?): LocalDate? {
        BizTime.parseDay(workDate?.trim().orEmpty())?.let { return it }
        val order = orderDateTime?.trim().orEmpty()
        if (order.length >= 10) return BizTime.parseDay(order.substring(0, 10))
        return null
    }

    /** 주문시각 → millis. 시각이 비었으면 그 영업일 저녁 8시로 둔다. */
    fun timeMillis(orderDateTime: String?, day: LocalDate): Long {
        val cleaned = orderDateTime?.trim().orEmpty()
        if (cleaned.isNotEmpty()) {
            for (pattern in ORDER_FORMATS) {
                val dt = runCatching {
                    LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern(pattern))
                }.getOrNull()
                if (dt != null) return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return LocalDateTime.of(day, LocalTime.of(20, 0))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** 수수료율(%) = 수수료 / 총매출 × 100 — 소수 첫째 자리까지 */
    fun feePercent(gross: Int, fee: Int): Double =
        if (gross <= 0 || fee == 0) 0.0 else Math.round(fee * 1000.0 / gross) / 10.0

    /**
     * CALL_OFF_CHK 값이 그날 일하지 않았다는 ‘휴무’ 표시인지.
     * 원본은 휴무일을 날짜만 있고 출발·도착·금액이 빈 행(CALL_OFF_CHK = 1)으로 남긴다.
     */
    fun isOffDay(offChk: Int?): Boolean = offChk == 1

    /** 출발·도착과 별개로 남는 메모(방문지 + 비고) */
    fun memo(visit: String?, remark: String?): String =
        listOf(visit, remark)
            .map { it?.trim().orEmpty() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")

    /** CALL_APP 이름 → 플랫폼 id. 이름이 없거나 모르는 앱이면 개인콜. */
    fun platformId(app: String?): String {
        val name = app?.trim().orEmpty()
        if (name.isEmpty()) return "personal"
        return when {
            name.contains("카카오") -> "kakao"
            name.contains("티맵") || name.contains("tmap", true) || name.contains("t map", true) -> "tmap"
            name.contains("핸들") -> "handle"
            name.contains("로지") -> "roji"
            name.contains("콜마너") -> "colmaner"
            name.contains("아이콘") -> "icon"
            else -> "personal"
        }
    }
}
