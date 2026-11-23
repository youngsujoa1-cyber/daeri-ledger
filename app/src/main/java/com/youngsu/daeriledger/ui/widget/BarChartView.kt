package com.youngsu.daeriledger.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.util.Money

/**
 * 요일·시간대별 순수익 막대 그래프.
 * 값이 가장 큰 막대는 앰버로 강조한다.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** value 는 Long — 전체 기간처럼 합계가 커져도 넘치지 않는다 */
    data class Bar(val label: String, val value: Long, val caption: String? = null)

    var bars: List<Bar> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val labelHeight = dp(30f)
    private val valueHeight = dp(16f)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(9.5f)
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_secondary)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10.5f)
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_secondary)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(8.5f)
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_tertiary)
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider)
        strokeWidth = dp(1f)
    }

    private val colorBar = ContextCompat.getColor(context, R.color.chart_bar)
    private val colorBarDim = ContextCompat.getColor(context, R.color.chart_bar_dim)
    private val colorAccent = ContextCompat.getColor(context, R.color.accent)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = (dp(150f) + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val left = paddingLeft.toFloat()
        val right = width - paddingRight.toFloat()
        val top = paddingTop + valueHeight
        val bottom = height - paddingBottom - labelHeight

        val maxValue = bars.maxOfOrNull { it.value }?.coerceAtLeast(0L) ?: 0L
        val slot = (right - left) / bars.size
        val barWidth = minOf(slot * 0.52f, dp(34f))
        val maxIndex = bars.indexOfFirst { it.value == maxValue && maxValue > 0L }

        canvas.drawLine(left, bottom + dp(2f), right, bottom + dp(2f), baselinePaint)

        bars.forEachIndexed { index, bar ->
            val centerX = left + slot * (index + 0.5f)
            val fraction = if (maxValue <= 0L) 0f else bar.value.toFloat() / maxValue.toFloat()
            val barHeight = ((bottom - top) * fraction).coerceAtLeast(if (bar.value > 0L) dp(3f) else 0f)
            val rect = RectF(
                centerX - barWidth / 2f,
                bottom - barHeight,
                centerX + barWidth / 2f,
                bottom
            )
            barPaint.color = if (bar.value == 0L) colorBarDim else if (index == maxIndex) colorAccent else colorBar
            canvas.drawRoundRect(rect, dp(7f), dp(7f), barPaint)

            if (bar.value > 0L) {
                valuePaint.color = if (index == maxIndex) colorAccent else ContextCompat.getColor(context, R.color.text_secondary)
                canvas.drawText(Money.compact(bar.value), centerX, rect.top - dp(4f), valuePaint)
            }
            labelPaint.color = if (index == maxIndex && bar.value > 0L) colorAccent
            else ContextCompat.getColor(context, R.color.text_secondary)
            canvas.drawText(bar.label, centerX, bottom + dp(15f), labelPaint)
            bar.caption?.let {
                canvas.drawText(it, centerX, bottom + dp(26f), captionPaint)
            }
        }
    }
}
