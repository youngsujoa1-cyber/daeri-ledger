package com.youngsu.daeriledger.ui.widget

import java.time.LocalDate
import java.time.YearMonth

/**
 * 달력 그리드 좌표 계산 — 그리기와 터치 판정이 **같은 식**을 쓰도록 한 곳에 모아 둔 순수 함수.
 *
 * 칸 배치는 7열 × 6행, 일요일 시작이고 항상 6주를 그린다.
 * 모든 좌표는 View 의 패딩을 포함한 로컬 좌표(px) 기준이다.
 *
 * (좌표 계산이 여기저기 흩어져 있으면 "그려진 칸"과 "눌리는 칸"이 어긋나므로,
 *  onDraw 와 onTouchEvent 가 모두 이 객체만 쓴다. tools/logic-check 에서 검증한다.)
 */
object CalendarGrid {

    const val COLS = 7
    const val ROWS = 6
    const val CELLS = COLS * ROWS

    /** 1일 앞에 붙는 이전 달 날짜 수 (일요일 시작이라 일요일=0) */
    fun leadingDays(month: YearMonth): Int = month.atDay(1).dayOfWeek.value % 7

    /** 그리드 0번 칸의 날짜 */
    fun startDate(month: YearMonth): LocalDate =
        month.atDay(1).minusDays(leadingDays(month).toLong())

    /** 칸 인덱스(0..41)의 날짜 */
    fun dateAt(index: Int, month: YearMonth): LocalDate =
        startDate(month).plusDays(index.toLong())

    /** 그 달에 속한 날짜인지 (앞뒤로 붙는 옆 달 날짜와 구분) */
    fun isInMonth(date: LocalDate, month: YearMonth): Boolean =
        date.year == month.year && date.monthValue == month.monthValue

    fun rowOf(index: Int): Int = index / COLS

    fun colOf(index: Int): Int = index % COLS

    /**
     * 터치 좌표 → 칸 인덱스(0..41).
     * 요일 헤더 줄이거나 그리드 바깥이면 null (그때는 날짜가 선택되지 않는다).
     */
    fun indexAt(
        x: Float,
        y: Float,
        contentWidth: Int,
        paddingLeft: Int,
        paddingTop: Int,
        headerHeight: Float,
        cellHeight: Float
    ): Int? {
        if (contentWidth <= 0 || cellHeight <= 0f) return null
        val cellWidth = contentWidth / COLS.toFloat()
        if (cellWidth <= 0f) return null
        val localX = x - paddingLeft
        val localY = y - paddingTop - headerHeight
        if (localX < 0f || localY < 0f) return null
        val col = (localX / cellWidth).toInt()
        val row = (localY / cellHeight).toInt()
        if (col !in 0 until COLS || row !in 0 until ROWS) return null
        return row * COLS + col
    }

    /** 터치 좌표가 속한 날짜 (그리드 바깥이면 null) */
    fun dateAt(
        x: Float,
        y: Float,
        month: YearMonth,
        contentWidth: Int,
        paddingLeft: Int,
        paddingTop: Int,
        headerHeight: Float,
        cellHeight: Float
    ): LocalDate? = indexAt(x, y, contentWidth, paddingLeft, paddingTop, headerHeight, cellHeight)
        ?.let { dateAt(it, month) }

    /**
     * 칸의 사각형 — left, top, right, bottom 순서.
     * inset 은 칸 안쪽 여백(칸 사이를 띄우는 값)이다.
     */
    fun cellBounds(
        index: Int,
        contentWidth: Int,
        paddingLeft: Int,
        paddingTop: Int,
        headerHeight: Float,
        cellHeight: Float,
        inset: Float
    ): FloatArray {
        val cellWidth = contentWidth / COLS.toFloat()
        val left = paddingLeft + cellWidth * colOf(index) + inset
        val top = paddingTop + headerHeight + cellHeight * rowOf(index) + inset
        return floatArrayOf(left, top, left + cellWidth - inset * 2f, top + cellHeight - inset * 2f)
    }
}
