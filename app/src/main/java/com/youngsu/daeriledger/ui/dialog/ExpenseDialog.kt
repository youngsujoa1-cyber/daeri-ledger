package com.youngsu.daeriledger.ui.dialog

import android.app.Activity
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.app.DatePickerDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.Expense
import com.youngsu.daeriledger.data.LedgerRepository
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.Dialogs
import com.youngsu.daeriledger.util.Money
import com.youngsu.daeriledger.util.selectedTag
import com.youngsu.daeriledger.util.setChips
import com.youngsu.daeriledger.util.toast
import java.time.LocalDate

/** 지출 빠른 입력 다이얼로그 (셔틀비·식대·보험료 등) */
object ExpenseDialog {

    val CATEGORIES = listOf("셔틀비", "대중교통", "식대", "프로그램비", "월보험료", "기타")

    fun show(
        activity: Activity,
        repo: LedgerRepository,
        defaultDay: LocalDate,
        existing: Expense? = null,
        onSaved: () -> Unit
    ) {
        val ctx = activity
        val container = LayoutInflater.from(ctx).inflate(R.layout.dialog_expense, null)
        val dayText = container.findViewById<TextView>(R.id.expenseDayText)
        val chips = container.findViewById<ChipGroup>(R.id.expenseChips)
        val amountInput = container.findViewById<EditText>(R.id.expenseAmountInput)
        val memoInput = container.findViewById<EditText>(R.id.expenseMemoInput)

        var day = existing?.businessDay ?: defaultDay
        val initialCategory = existing?.category ?: "식대"
        chips.setChips(CATEGORIES.map { it to it }, initialCategory) { }
        amountInput.setText(if (existing != null) existing.amount.toString() else "")
        memoInput.setText(existing?.memo ?: "")
        dayText.text = "${BizTime.dayTitle(day)} 영업일"

        dayText.setOnClickListener {
            DatePickerDialog(
                ctx,
                { _, year, month, dayOfMonth ->
                    day = LocalDate.of(year, month + 1, dayOfMonth)
                    dayText.text = "${BizTime.dayTitle(day)} 영업일"
                },
                day.year, day.monthValue - 1, day.dayOfMonth
            ).show()
        }

        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existing == null) "지출 추가" else "지출 수정")
            .setView(container)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장", null)
        if (existing != null) builder.setNeutralButton("삭제", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ctx.getColor(R.color.accent))
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ctx.getColor(R.color.text_secondary))
            if (existing != null) {
                dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
                    .setTextColor(ctx.getColor(R.color.loss))
                dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    Dialogs.confirm(
                        ctx,
                        "지출 삭제",
                        "${existing.category} ${Money.won(existing.amount)} 기록을 지울까요?",
                        positive = "삭제",
                        destructive = true
                    ) {
                        repo.deleteExpense(existing.id)
                        dialog.dismiss()
                        ctx.toast("지출을 지웠어요")
                        onSaved()
                    }
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = Money.parseAmount(amountInput.text.toString())
                if (amount <= 0) {
                    ctx.toast("금액을 입력해 주세요")
                    return@setOnClickListener
                }
                val category = chips.selectedTag() ?: "기타"
                val memo = memoInput.text.toString().trim()
                repo.saveExpense(
                    Expense(
                        id = existing?.id ?: 0L,
                        businessDay = day,
                        timeMillis = existing?.timeMillis ?: System.currentTimeMillis(),
                        category = category,
                        amount = amount,
                        memo = memo,
                        createdAt = existing?.createdAt ?: 0L
                    )
                )
                dialog.dismiss()
                ctx.toast(if (existing == null) "지출 ${Money.won(amount)}을 기록했어요" else "지출을 수정했어요")
                onSaved()
            }
        }
        dialog.show()
    }
}
