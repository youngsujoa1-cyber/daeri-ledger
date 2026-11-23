package com.youngsu.daeriledger.ui.widget

import kotlin.math.abs

/**
 * 터치 제스처 판정 — '칸을 누름'과 '스크롤'을 가르는 순수 상태 기계.
 *
 * CalendarMonthView 는 ACTION_DOWN 에서 **반드시 true** 를 돌려줘야 한다. false 를 돌려주면
 * 부모(NestedScrollView)가 제스처를 가져가서 ACTION_UP 이 그리드까지 오지 않고,
 * 그래서 날짜를 눌러도 아무 일이 일어나지 않는다.
 * 그 판단(소비/취소/탭)을 여기에 모아 두어 안드로이드 없이도 검증한다. (tools/logic-check)
 */
class PressTracker(private val touchSlop: Float) {

    private var downX = 0f
    private var downY = 0f
    private var active = false

    /** 지금 손가락이 닿아 있는 칸 번호 (없으면 -1) — 눌린 표시를 그릴 때 쓴다 */
    var pressedIndex: Int = -1
        private set

    val isTracking: Boolean get() = active

    /** ACTION_DOWN — 그리드 안이면 true(이 제스처를 소비한다). 밖(요일 헤더 등)이면 false */
    fun down(x: Float, y: Float, index: Int?): Boolean {
        if (index == null || index < 0) {
            cancel()
            return false
        }
        active = true
        downX = x
        downY = y
        pressedIndex = index
        return true
    }

    /** ACTION_MOVE — 손가락이 슬롭 넘게 움직였으면 true(날짜 선택 취소, 스크롤에 넘김) */
    fun move(x: Float, y: Float): Boolean {
        if (!active) return false
        if (abs(x - downX) > touchSlop || abs(y - downY) > touchSlop) {
            cancel()
            return true
        }
        return false
    }

    /** ACTION_UP — 제자리에서 뗀 탭이면 true(그 자리의 날짜를 선택) */
    fun up(x: Float, y: Float): Boolean {
        if (!active) {
            pressedIndex = -1
            return false
        }
        active = false
        pressedIndex = -1
        return abs(x - downX) <= touchSlop && abs(y - downY) <= touchSlop
    }

    fun cancel() {
        active = false
        pressedIndex = -1
    }
}
