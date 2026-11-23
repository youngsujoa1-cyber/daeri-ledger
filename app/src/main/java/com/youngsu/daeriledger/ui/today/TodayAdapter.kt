package com.youngsu.daeriledger.ui.today

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.DaySummary
import com.youngsu.daeriledger.data.Expense
import com.youngsu.daeriledger.data.Ride
import com.youngsu.daeriledger.databinding.ItemEmptyBinding
import com.youngsu.daeriledger.databinding.ItemExpenseBinding
import com.youngsu.daeriledger.databinding.ItemRideBinding
import com.youngsu.daeriledger.databinding.ItemSectionHeaderBinding
import com.youngsu.daeriledger.databinding.ItemTodayHeaderBinding
import com.youngsu.daeriledger.databinding.ItemUnpaidBannerBinding
import com.youngsu.daeriledger.ui.widget.RideCardBinder
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.Money
import java.time.LocalDate

class TodayAdapter(
    private val onRideClick: (Ride) -> Unit,
    private val onExpenseClick: (Expense) -> Unit,
    private val onUnpaidClick: () -> Unit,
    private val onAddExpense: () -> Unit,
    private val onToggleOffDay: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Row {
        data object Header : Row
        data class Unpaid(val amount: Int, val count: Int) : Row
        data class Section(val title: String, val action: String?) : Row
        data class RideRow(val ride: Ride) : Row
        data class ExpenseRow(val expense: Expense) : Row
        data class Empty(val title: String, val message: String, val icon: Int) : Row
    }

    private var rows: List<Row> = emptyList()
    private var day: LocalDate = LocalDate.now()
    private var summary: DaySummary = DaySummary(LocalDate.now())
    private var goal: Int = 150_000
    private var cutoffHour: Int = 12
    private var offDay: Boolean = false

    fun submit(
        day: LocalDate,
        summary: DaySummary,
        rides: List<Ride>,
        expenses: List<Expense>,
        goal: Int,
        cutoffHour: Int,
        offDay: Boolean = false
    ) {
        this.day = day
        this.summary = summary
        this.goal = goal
        this.cutoffHour = cutoffHour
        this.offDay = offDay

        val list = ArrayList<Row>()
        list.add(Row.Header)
        if (summary.unpaidNet > 0) {
            val unpaidCount = rides.count { it.isUnpaid }
            list.add(Row.Unpaid(summary.unpaidNet, unpaidCount))
        }
        list.add(Row.Section("오늘 운행 ${rides.size}건", "지출 추가"))
        if (rides.isEmpty()) {
            list.add(
                if (offDay) {
                    Row.Empty(
                        "오늘은 휴무일로 표시했어요",
                        "일하지 않은 날은 이렇게 두면 통계의 ‘일한 날’에 들어가지 않아요",
                        R.drawable.ic_clock
                    )
                } else {
                    Row.Empty(
                        "아직 오늘 기록이 없어요",
                        "오른쪽 아래 ‘운행 기록’으로 첫 콜을 3초 만에 남겨보세요",
                        R.drawable.ic_wallet
                    )
                }
            )
        } else {
            rides.forEach { list.add(Row.RideRow(it)) }
        }
        if (expenses.isNotEmpty()) {
            val total = expenses.sumOf { it.amount }
            list.add(Row.Section("지출 ${expenses.size}건 · ${Money.won(total)}", null))
            expenses.forEach { list.add(Row.ExpenseRow(it)) }
        }
        rows = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        Row.Header -> TYPE_HEADER
        is Row.Unpaid -> TYPE_UNPAID
        is Row.Section -> TYPE_SECTION
        is Row.RideRow -> TYPE_RIDE
        is Row.ExpenseRow -> TYPE_EXPENSE
        is Row.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemTodayHeaderBinding.inflate(inflater, parent, false))
            TYPE_UNPAID -> UnpaidHolder(ItemUnpaidBannerBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false))
            TYPE_RIDE -> RideHolder(ItemRideBinding.inflate(inflater, parent, false))
            TYPE_EXPENSE -> ExpenseHolder(ItemExpenseBinding.inflate(inflater, parent, false))
            else -> EmptyHolder(ItemEmptyBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            Row.Header -> (holder as HeaderHolder).bind()
            is Row.Unpaid -> (holder as UnpaidHolder).bind(row)
            is Row.Section -> (holder as SectionHolder).bind(row)
            is Row.RideRow -> (holder as RideHolder).bind(row.ride)
            is Row.ExpenseRow -> (holder as ExpenseHolder).bind(row.expense)
            is Row.Empty -> (holder as EmptyHolder).bind(row)
        }
    }

    private inner class HeaderHolder(private val b: ItemTodayHeaderBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind() {
            val ctx = b.root.context
            val isToday = day == BizTime.businessDayOfNow(cutoffHour)
            b.dateText.text = BizTime.dayTitle(day)
            b.businessDayText.text =
                "영업일 기준 ${BizTime.cutOffLabel(cutoffHour)}" + if (isToday) " · 지금 영업일" else " · 지난 영업일"
            b.livePill.text = if (isToday) "오늘" else "기록"

            // 휴무 토글 — 지금 상태를 그대로 보여준다 (휴무면 ‘휴무 해제’)
            b.offDayToggle.text = if (offDay) "휴무 해제" else "휴무"
            b.offDayToggle.setTextColor(
                ContextCompat.getColor(ctx, if (offDay) R.color.off_day else R.color.text_secondary)
            )
            b.offDayToggle.setBackgroundResource(
                if (offDay) R.drawable.bg_pill_off else R.drawable.bg_pill
            )
            b.offDayToggle.setOnClickListener { onToggleOffDay() }

            // 총수입 = 총매출 + 팁·경유비 (수수료·보험·지출 빼기 전, 손에 들어온 전체 금액)
            val income = summary.income
            val net = summary.net
            b.incomeAmountText.text = Money.won(income)
            b.incomeAmountText.setTextColor(
                ContextCompat.getColor(ctx, if (income < 0) R.color.loss else R.color.profit)
            )
            b.callCountText.text = "콜 ${summary.rides}건"

            b.netText.text = Money.won(net)
            b.expenseText.text = Money.won(summary.expenses)
            b.costText.text = Money.won(summary.fees + summary.insurance)

            if (summary.unpaidNet > 0) {
                b.unpaidInlineText.visibility = View.VISIBLE
                b.unpaidInlineText.text = "그중 미수 ${Money.won(summary.unpaidNet)}"
            } else {
                b.unpaidInlineText.visibility = View.GONE
            }

            // 목표도 총수입 기준으로 계산한다
            val rate = if (goal <= 0) 0 else (income.toLong() * 100 / goal).toInt()
            b.goalRateText.text = "$rate%"
            b.goalBar.setProgressCompat(rate.coerceIn(0, 100), false)
            b.goalBar.setIndicatorColor(
                ContextCompat.getColor(ctx, if (income >= goal && goal > 0) R.color.profit else R.color.accent)
            )
            b.goalHintText.text = when {
                goal <= 0 -> "설정에서 일일 목표를 정해보세요"
                income >= goal -> "목표 달성! 목표보다 ${Money.won(income - goal)} 더 벌었어요"
                else -> "목표 ${Money.won(goal)}까지 ${Money.won(goal - income)} 남았어요"
            }

            val pace = StringBuilder()
            if (summary.rides > 0) {
                pace.append("시간당 순수익 ${Money.won(summary.hourlyNet)}")
                pace.append(" · 콜당 순수익 ${Money.won(summary.avgPerCall)}")
                if (summary.spanMinutes > 0) {
                    pace.append(" · 운행 ${BizTime.formatDuration(summary.spanMinutes)}")
                }
            } else {
                pace.append(if (offDay) "휴무일 · 일하지 않은 날" else "오늘 첫 콜을 기다리는 중")
            }
            b.paceText.text = pace.toString()
        }
    }

    private inner class UnpaidHolder(private val b: ItemUnpaidBannerBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row.Unpaid) {
            b.unpaidAmountText.text = "미수금 ${Money.won(row.amount)}"
            b.unpaidCountText.text = "${row.count}건 · 입금 확인하고 정리하세요"
            b.unpaidBannerRoot.setOnClickListener { onUnpaidClick() }
        }
    }

    private inner class SectionHolder(private val b: ItemSectionHeaderBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row.Section) {
            b.sectionTitle.text = row.title
            if (row.action != null) {
                b.sectionAction.visibility = View.VISIBLE
                b.sectionAction.text = row.action
                b.sectionAction.setOnClickListener { onAddExpense() }
            } else {
                b.sectionAction.visibility = View.GONE
            }
        }
    }

    private inner class RideHolder(private val b: ItemRideBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(ride: Ride) {
            RideCardBinder.bind(b, ride) { onRideClick(it) }
        }
    }

    private inner class ExpenseHolder(private val b: ItemExpenseBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(expense: Expense) {
            b.categoryText.text = expense.category
            if (expense.memo.isBlank()) {
                b.memoText.visibility = View.GONE
            } else {
                b.memoText.visibility = View.VISIBLE
                b.memoText.text = expense.memo
            }
            b.amountText.text = "-${Money.won(expense.amount)}"
            b.root.setOnClickListener { onExpenseClick(expense) }
        }
    }

    private inner class EmptyHolder(private val b: ItemEmptyBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row.Empty) {
            b.emptyTitle.text = row.title
            b.emptyMessage.text = row.message
            (b.root as ViewGroup).getChildAt(0)?.let { icon ->
                (icon as? android.widget.ImageView)?.setImageResource(row.icon)
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_UNPAID = 1
        private const val TYPE_SECTION = 2
        private const val TYPE_RIDE = 3
        private const val TYPE_EXPENSE = 4
        private const val TYPE_EMPTY = 5
    }
}
