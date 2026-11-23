package com.youngsu.daeriledger.ui.today

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.youngsu.daeriledger.databinding.ScreenTodayBinding
import com.youngsu.daeriledger.ui.MainActivity
import com.youngsu.daeriledger.ui.Screen
import com.youngsu.daeriledger.ui.dialog.ExpenseDialog
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.toast

/** 오늘(영업일) 요약 화면 */
class TodayScreen(private val activity: MainActivity) : Screen {

    private val binding = ScreenTodayBinding.inflate(activity.layoutInflater)
    override val view: View get() = binding.root

    private val adapter = TodayAdapter(
        onRideClick = { ride -> activity.openAddRide(rideId = ride.id) },
        onExpenseClick = { expense ->
            ExpenseDialog.show(activity, activity.repo, expense.businessDay, expense) { activity.refreshAll() }
        },
        onUnpaidClick = { activity.openUnpaid() },
        onAddExpense = { showExpenseDialog() },
        onToggleOffDay = { toggleTheOffDay() }
    )

    init {
        binding.recyclerToday.layoutManager = LinearLayoutManager(activity)
        binding.recyclerToday.adapter = adapter
        binding.fabAddRide.setOnClickListener { activity.openAddRide() }
    }

    override fun refresh() {
        val cutoff = activity.settings.cutoffHour
        val day = BizTime.businessDayOfNow(cutoff)
        // 목록을 한 번만 읽고 합계도 그 목록에서 계산한다
        val rides = activity.repo.ridesForDay(day)
        val expenses = activity.repo.expensesForDay(day)
        val summary = activity.repo.summaryOf(day, rides, expenses)
        val offDay = activity.repo.isOffDay(day)
        adapter.submit(day, summary, rides, expenses, activity.settings.dailyGoal, cutoff, offDay)
    }

    /** 오늘(영업일)을 휴무로 표시하거나 해제한다 */
    private fun toggleTheOffDay() {
        val day = BizTime.businessDayOfNow(activity.settings.cutoffHour)
        val nowOff = activity.repo.toggleOffDay(day)
        activity.refreshAll()
        if (nowOff) activity.toast("오늘을 휴무로 표시했어요") else activity.toast("휴무를 해제했어요")
    }

    private fun showExpenseDialog() {
        val day = BizTime.businessDayOfNow(activity.settings.cutoffHour)
        ExpenseDialog.show(activity, activity.repo, day) { activity.refreshAll() }
    }
}
