package com.youngsu.daeriledger.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 영업일 계산 유틸 — 자정이 아니라 설정한 기준시각(기본 낮 12시)에서 날짜가 바뀐다. */
object BizTime {

    private val ZONE: ZoneId = ZoneId.systemDefault()
    private val dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val KOREAN_WEEKDAYS = arrayOf("월", "화", "수", "목", "금", "토", "일")

    fun dateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate()

    fun timeOf(millis: Long): LocalTime =
        Instant.ofEpochMilli(millis).atZone(ZONE).toLocalTime()

    fun millisOf(date: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(date, time).atZone(ZONE).toInstant().toEpochMilli()

    fun now(): Long = System.currentTimeMillis()

    /** 해당 시각이 속하는 영업일. 기준시각 이전이면 전날 영업일. */
    fun businessDayOf(millis: Long, cutoffHour: Int): LocalDate {
        val d = dateOf(millis)
        return if (timeOf(millis).hour < cutoffHour) d.minusDays(1) else d
    }

    fun businessDayOfNow(cutoffHour: Int): LocalDate = businessDayOf(now(), cutoffHour)

    /** 영업일의 시작(기준시각)과 끝(다음날 기준시각 직전) */
    fun businessDayRange(day: LocalDate, cutoffHour: Int): Pair<Long, Long> {
        val start = millisOf(day, LocalTime.of(cutoffHour, 0))
        val end = millisOf(day.plusDays(1), LocalTime.of(cutoffHour, 0)) - 1
        return start to end
    }

    fun dayKey(date: LocalDate): String = date.format(dayFormatter)

    fun parseDay(key: String): LocalDate? = runCatching { LocalDate.parse(key, dayFormatter) }.getOrNull()

    fun weekdayLabel(date: LocalDate): String {
        val idx = date.dayOfWeek.value - 1
        return KOREAN_WEEKDAYS[idx]
    }

    fun monthTitle(month: LocalDate): String =
        String.format(Locale.KOREA, "%d년 %d월", month.year, month.monthValue)

    fun dayTitle(date: LocalDate): String =
        String.format(Locale.KOREA, "%d월 %d일 (%s)", date.monthValue, date.dayOfMonth, weekdayLabel(date))

    fun fullDayTitle(date: LocalDate): String =
        String.format(Locale.KOREA, "%d년 %d월 %d일 (%s)", date.year, date.monthValue, date.dayOfMonth, weekdayLabel(date))

    fun clockLabel(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZONE).toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))

    /** 새벽 콜에 '새벽' 딱지 */
    fun partOfDayLabel(millis: Long): String =
        when (timeOf(millis).hour) {
            in 0..4 -> "새벽"
            in 5..11 -> "오전"
            in 12..17 -> "오후"
            in 18..21 -> "저녁"
            else -> "심야"
        }

    fun isWeekend(date: LocalDate): Boolean {
        val d: DayOfWeek = date.dayOfWeek
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY
    }

    /** 380 → "6시간 20분" */
    fun formatDuration(minutes: Long): String {
        if (minutes <= 0) return "0분"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "${m}분"
            m == 0L -> "${h}시간"
            else -> "${h}시간 ${m}분"
        }
    }

    fun cutOffLabel(hour: Int): String = when {
        hour == 0 -> "밤 12시"
        hour < 12 -> "오전 $hour 시"
        hour == 12 -> "낮 12시"
        else -> "오후 ${hour - 12} 시"
    }
}

/** 금액 표기 — 123,000원 / 12.3만 / 1.2억 */
object Money {
    fun won(amount: Int): String = String.format(Locale.KOREA, "%,d원", amount)
    fun wonPlain(amount: Int): String = String.format(Locale.KOREA, "%,d", amount)

    fun signed(amount: Int): String =
        if (amount < 0) "-" + won(-amount) else won(amount)

    /** 달력 셀처럼 좁은 곳에 쓰는 축약 표기 */
    fun compact(amount: Int): String = compact(amount.toLong())

    fun compact(amount: Long): String {
        val abs = kotlin.math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs == 0L -> "0"
            abs < 10_000L -> String.format(Locale.KOREA, "%s%,d", sign, abs)
            abs < 100_000_000L -> {
                val man = abs / 10_000.0
                if (man >= 100) String.format(Locale.KOREA, "%s%.0f만", sign, man)
                else String.format(Locale.KOREA, "%s%.1f만", sign, man).replace(".0만", "만")
            }
            else -> String.format(Locale.KOREA, "%s%.1f억", sign, abs / 100_000_000.0)
        }
    }

    fun parseAmount(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val digits = text.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    // ---- 큰 금액(Long) — 통계처럼 기간 합계가 커질 수 있는 곳에서 쓴다 (Int 넘침 방지)

    fun won(amount: Long): String = String.format(Locale.KOREA, "%,d원", amount)

    fun wonPlain(amount: Long): String = String.format(Locale.KOREA, "%,d", amount)

    fun signed(amount: Long): String =
        if (amount < 0) "-" + won(-amount) else won(amount)
}
