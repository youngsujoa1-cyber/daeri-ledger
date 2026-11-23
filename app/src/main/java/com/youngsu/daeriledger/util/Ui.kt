package com.youngsu.daeriledger.util

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.youngsu.daeriledger.R
import kotlin.math.roundToInt

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

@ColorInt
fun Context.color(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

fun Activity.toast(message: String) {
    android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
}

fun Activity.snackbar(
    anchor: View,
    message: String,
    actionLabel: String? = null,
    action: (() -> Unit)? = null
) {
    val bar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
        .setBackgroundTint(color(R.color.surface_high))
        .setTextColor(color(R.color.text_primary))
        .setActionTextColor(color(R.color.accent))
    if (actionLabel != null && action != null) {
        bar.setAction(actionLabel) { action() }
    }
    bar.show()
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
}

/** ChipGroup 에 칩을 채운다 */
fun ChipGroup.setChips(
    items: List<Pair<String, String>>,
    selectedId: String? = null,
    onSelected: (String) -> Unit
) {
    removeAllViews()
    val inflater = LayoutInflater.from(context)
    items.forEach { (id, label) ->
        val chip = inflater.inflate(R.layout.chip_choice, this, false) as Chip
        chip.id = View.generateViewId()
        chip.text = label
        chip.tag = id
        chip.isChecked = id == selectedId
        chip.setOnClickListener { onSelected(id) }
        addView(chip)
    }
}

fun ChipGroup.setAmountChips(
    items: List<Pair<String, String>>,
    selectedId: String? = null,
    onSelected: (String) -> Unit
) {
    removeAllViews()
    val inflater = LayoutInflater.from(context)
    items.forEach { (id, label) ->
        val chip = inflater.inflate(R.layout.chip_amount, this, false) as Chip
        chip.id = View.generateViewId()
        chip.text = label
        chip.tag = id
        chip.isChecked = id == selectedId
        chip.setOnClickListener { onSelected(id) }
        addView(chip)
    }
}

fun ChipGroup.selectChip(id: String?) {
    for (i in 0 until childCount) {
        val chip = getChildAt(i) as? Chip ?: continue
        chip.isChecked = chip.tag == id
    }
}

fun ChipGroup.selectedTag(): String? {
    for (i in 0 until childCount) {
        val chip = getChildAt(i) as? Chip ?: continue
        if (chip.isChecked) return chip.tag as? String
    }
    return null
}

/** 텍스트 뱃지 색 바꾸기 (결제 수단 등) */
fun TextView.applyBadge(backgroundRes: Int, @ColorRes textColor: Int) {
    setBackgroundResource(backgroundRes)
    setTextColor(ContextCompat.getColor(context, textColor))
}

fun colorStateList(color: Int): ColorStateList = ColorStateList.valueOf(color)

/** 화면 공통 다이얼로그 */
object Dialogs {

    /** 숫자 입력 다이얼로그. 접미사/프리셋 지정 가능 */
    fun amount(
        activity: Activity,
        title: String,
        suffix: String,
        initial: String,
        hint: String? = null,
        presets: List<Pair<String, String>> = emptyList(),
        allowDecimal: Boolean = false,
        onConfirm: (String) -> Unit
    ) {
        val container = LayoutInflater.from(activity).inflate(R.layout.dialog_number_input, null)
        val input = container.findViewById<EditText>(R.id.numberInput)
        val suffixText = container.findViewById<TextView>(R.id.numberSuffix)
        val hintText = container.findViewById<TextView>(R.id.inputHint)

        suffixText.text = suffix
        input.setText(initial)
        input.inputType = if (allowDecimal) {
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        } else {
            android.text.InputType.TYPE_CLASS_NUMBER
        }
        if (hint != null) {
            input.hint = hint
            hintText.text = hint
            hintText.visibility = View.VISIBLE
        }

        val presetGroup = container.findViewById<ChipGroup>(R.id.numberPresets)
        if (presets.isEmpty()) presetGroup.visibility = View.GONE
        else presetGroup.setChips(presets, null) { value -> input.setText(value) }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("취소", null)
            .setPositiveButton("확인") { _, _ -> onConfirm(input.text.toString().trim()) }
            .create()
        dialog.show()
        input.requestFocus()
        input.setSelection(input.text.length)
    }

    fun confirm(
        activity: Activity,
        title: String,
        message: String,
        positive: String = "확인",
        negative: String = "취소",
        destructive: Boolean = false,
        onPositive: () -> Unit
    ): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(negative, null)
            .setPositiveButton(positive) { _, _ -> onPositive() }
            .create()
        dialog.setOnShowListener {
            val color = activity.color(if (destructive) R.color.loss else R.color.accent)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(activity.color(R.color.text_secondary))
        }
        dialog.show()
        return dialog
    }
}
