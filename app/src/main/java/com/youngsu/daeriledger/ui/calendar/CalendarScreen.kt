package com.youngsu.daeriledger.ui.calendar

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.Expense
import com.youngsu.daeriledger.data.Ride
import com.youngsu.daeriledger.databinding.ItemExpenseBinding
import com.youngsu.daeriledger.databinding.ItemRideBinding
import com.youngsu.daeriledger.databinding.ScreenCalendarBinding
import com.youngsu.daeriledger.ui.MainActivity
import com.youngsu.daeriledger.ui.Screen
import com.youngsu.daeriledger.ui.dialog.ExpenseDialog
import com.youngsu.daeriledger.ui.widget.RideCardBinder
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.Money
import com.youngsu.daeriledger.util.color
import com.youngsu.daeriledger.util.dp
import com.youngsu.daeriledger.util.toast
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** 월간 달력 — 영업일마다 순수익·콜 수를 보여주고, 고른 날의 상세를 아래에 붙인다 */
class CalendarScreen(private val activity: MainActivity) : Screen {

    private val binding = ScreenCalendarBinding.inflate(activity.layoutInflater)
    override val view: View get() = binding.root

    private val today: LocalDate get() = BizTime.businessDayOfNow(activity.settings.cutoffHour)
    private var month: YearMonth = YearMonth.from(today)
    private var selectedDay: LocalDate = today

    init {
        binding.calendarView.today = today
        binding.calendarView.onDayClick = { date -> onDayTapped(date) }
        binding.prevMonth.setOnClickListener {
            month = month.minusMonths(1)
            selectedDay = defaultDayFor(month)
            refresh()
            scrollToCalendar()
        }
        binding.nextMonth.setOnClickListener {
            month = month.plusMonths(1)
            selectedDay = defaultDayFor(month)
            refresh()
            scrollToCalendar()
        }
        binding.thisMonthButton.setOnClickListener {
            month = YearMonth.from(today)
            selectedDay = today
            refresh()
            scrollToCalendar()
        }
        binding.addForDayButton.setOnClickListener { activity.openAddRide(selectedDay) }
        binding.offDayToggle.setOnClickListener {
            val nowOff = activity.repo.toggleOffDay(selectedDay)
            refresh()
            if (nowOff) activity.toast("휴무로 표시했어요") else activity.toast("휴무를 해제했어요")
        }
    }

    override fun refresh() {
        val todayNow = today
        binding.calendarView.today = todayNow
        binding.calendarView.month = month
        binding.monthTitle.text = BizTime.monthTitle(month.atDay(1))

        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        // 6주 그리드가 앞뒤 달 날짜까지 그리므로 조회 범위를 일주일씩 넓힌다
        val summaries = activity.repo.daySummaries(monthStart.minusDays(7), monthEnd.plusDays(7))
        binding.calendarView.summaries = summaries
        binding.calendarView.offDays = activity.repo.offDaysBetween(monthStart.minusDays(7), monthEnd.plusDays(7))

        // 이번 달 합계는 그리드용 집계에서 그대로 더한다 (같은 달을 두 번 조회하지 않는다)
        var monthIncome = 0
        var monthNet = 0
        var monthCalls = 0
        var monthExpense = 0
        summaries.forEach { (day, daySummary) ->
            if (YearMonth.from(day) != month) return@forEach
            monthIncome += daySummary.income
            monthNet += daySummary.net
            monthCalls += daySummary.rides
            monthExpense += daySummary.expenses
        }
        // 녹색 숫자는 총수입(총매출 + 팁), 바로 아래 작은 줄은 실제수익(수수료·보험 차감 후)
        binding.monthIncomeText.text = Money.won(monthIncome)
        binding.monthIncomeText.setTextColor(
            activity.color(if (monthIncome < 0) R.color.loss else R.color.profit)
        )
        binding.monthProfitText.text = "실제수익 ${Money.won(monthNet)}"
        binding.monthCallsText.text = "${monthCalls}건"
        binding.monthExpenseText.text = Money.won(monthExpense)

        if (selectedDay.month != month.month || selectedDay.year != month.year) {
            selectedDay = defaultDayFor(month)
        }
        binding.calendarView.selectedDay = selectedDay
        renderDay()
    }

    /** 달력 칸을 눌렀을 때 — 그날 상세를 바로 보여준다 */
    private fun onDayTapped(date: LocalDate) {
        val tappedMonth = YearMonth.from(date)
        if (tappedMonth != month) {
            // 그리드 끝에 붙은 옆 달 날짜 — 그 달로 넘어가서 보여준다
            month = tappedMonth
            selectedDay = date
            refresh()
        } else {
            selectedDay = date
            binding.calendarView.selectedDay = date
            renderDay()
        }
        revealSelectedDay()
    }

    private fun defaultDayFor(target: YearMonth): LocalDate {
        val todayNow = today
        return if (YearMonth.from(todayNow) == target) todayNow else target.atDay(1)
    }

    /** 고른 날의 내역이 화면 아래에 가려져 있으면 부드럽게 내려서 바로 보이게 한다 */
    private fun revealSelectedDay() {
        val scroll = binding.calendarScroll
        scroll.post {
            val header = binding.dayHeader
            if (header.height == 0) return@post
            val top = header.top
            val bottom = top + header.height
            val visibleTop = scroll.scrollY
            val visibleBottom = visibleTop + scroll.height - scroll.paddingBottom
            val alreadyVisible = top >= visibleTop && bottom + activity.dp(180f) <= visibleBottom
            if (alreadyVisible) return@post
            // 달력 마지막 줄이 조금 남아 보이도록 여유를 두고 내린다
            val target = (top - activity.dp(48f)).coerceAtLeast(0)
            if (abs(target - scroll.scrollY) < activity.dp(2f)) return@post
            scroll.smoothScrollTo(0, target)
        }
    }

    private fun scrollToCalendar() {
        binding.calendarScroll.post { binding.calendarScroll.smoothScrollTo(0, 0) }
    }

    private fun renderDay() {
        val day = selectedDay
        // 한 번만 읽고, 합계도 그 목록에서 계산한다 (같은 날을 두 번 조회하지 않는다)
        val rides = activity.repo.ridesForDay(day)
        val expenses = activity.repo.expensesForDay(day)
        val summary = activity.repo.summaryOf(day, rides, expenses)
        val off = activity.repo.isOffDay(day)

        // 휴무 버튼은 지금 상태를 그대로 보여준다: 휴무로 표시된 날이면 ‘휴무 해제’
        binding.offDayToggle.text = if (off) "휴무 해제" else "휴무"
        binding.offDayToggle.setTextColor(
            activity.color(if (off) R.color.off_day else R.color.text_secondary)
        )
        binding.offDayToggle.setBackgroundResource(
            if (off) R.drawable.bg_pill_off else R.drawable.bg_pill
        )

        binding.selectedDayTitle.text = BizTime.dayTitle(day)
        binding.selectedDaySummary.text = when {
            // 일하지 않은 날: 휴무로 표시돼 있고 기록이 없으면 문구를 바꿔 둔다
            off && summary.rides == 0 -> if (summary.expenses == 0) {
                "휴무일 · 일하지 않은 날"
            } else {
                "휴무일 · 일하지 않은 날 · 지출 ${Money.won(summary.expenses)}"
            }
            summary.rides == 0 && summary.expenses == 0 -> "기록 없음"
            else -> {
                val income = summary.income
                val first = "총수입 ${Money.won(income)} · 실제수익 ${Money.won(summary.net)}"
                val second = StringBuilder("콜 ${summary.rides}건 · 지출 ${Money.won(summary.expenses)}")
                if (summary.expenses > 0) second.append(" · 남은 돈 ${Money.won(summary.takeHome)}")
                "$first\n$second"
            }
        }

        val list = binding.dayList
        list.removeAllViews()

        if (rides.isEmpty() && expenses.isEmpty()) {
            list.addView(
                TextView(activity).apply {
                    text = if (off) {
                        "휴무일로 표시한 날이에요. 그날 콜을 기록하면 기록이 먼저 보여요."
                    } else {
                        "이 날은 기록이 없어요. ‘+ 기록’으로 남기거나 ‘휴무’로 표시해 두세요."
                    }
                    setTextColor(activity.color(R.color.text_secondary))
                    textSize = 12f
                    setPadding(activity.dp(18f), activity.dp(14f), activity.dp(18f), activity.dp(14f))
                }
            )
            return
        }

        // 기록 순서대로: 이른 시간이 위, 늦은 시간이 아래
        val entries: List<Pair<Long, Any>> =
            (rides.map { it.timeMillis to (it as Any) } + expenses.map { it.timeMillis to (it as Any) })
                .sortedBy { it.first }

        val inflater = LayoutInflater.from(activity)
        entries.forEach { (_, item) ->
            when (item) {
                is Ride -> {
                    val b = ItemRideBinding.inflate(inflater, list, false)
                    bindRide(b, item)
                    list.addView(b.root)
                }
                is Expense -> {
                    val b = ItemExpenseBinding.inflate(inflater, list, false)
                    bindExpense(b, item)
                    list.addView(b.root)
                }
            }
        }
    }

    private fun bindRide(b: ItemRideBinding, ride: Ride) {
        RideCardBinder.bind(b, ride) { activity.openAddRide(rideId = it.id) }
    }

    private fun bindExpense(b: ItemExpenseBinding, expense: Expense) {
        b.categoryText.text = expense.category
        if (expense.memo.isBlank()) b.memoText.visibility = View.GONE else {
            b.memoText.visibility = View.VISIBLE
            b.memoText.text = expense.memo
        }
        b.amountText.text = "-${Money.won(expense.amount)}"
        b.root.setOnClickListener {
            ExpenseDialog.show(activity, activity.repo, expense.businessDay, expense) {
                activity.refreshAll()
            }
        }
    }
}
