package com.youngsu.daeriledger.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.DaySummary
import com.youngsu.daeriledger.util.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * 영업일 기준 월간 달력.
 * 칸마다 날짜 · 총수입(축약) · 콜 수를 그리고, 총수입 크기에 따라 배경 농도를 바꾼다.
 * (녹색 숫자는 오늘 탭과 같이 총수입 = 총매출 + 팁)
 * 휴무로 표시한 날(offDays)은 운행이 없을 때 칸 안에 회색빛 '휴무' 표를 그린다.
 *
 * 터치: 칸을 누르면 그 날짜가 선택되고 [onDayClick] 이 불린다.
 *  - ACTION_DOWN 을 반드시 true 로 돌려줘야 한다. false 를 돌려주면 부모(NestedScrollView)가
 *    제스처를 가져가서 ACTION_UP 이 여기까지 오지 않고, "눌러도 아무 반응 없음" 이 된다.
 *  - 누른 채로 손가락이 슬롭 이상 움직이면 날짜 선택을 취소하고 스크롤에 넘긴다.
 */
class CalendarMonthView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var month: YearMonth = YearMonth.now()
        set(value) {
            field = value
            invalidate()
        }

    var summaries: Map<LocalDate, DaySummary> = emptyMap()
        set(value) {
            field = value
            invalidate()
        }

    var selectedDay: LocalDate? = null
        set(value) {
            field = value
            invalidate()
        }

    /** 휴무로 표시한 영업일 — 운행이 없는 날은 칸 안에 '휴무' 표를 그린다 */
    var offDays: Set<LocalDate> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    var today: LocalDate = LocalDate.now()
        set(value) {
            field = value
            invalidate()
        }

    var onDayClick: ((LocalDate) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val headerHeight = dp(26f)
    private val cellHeight = dp(54f)
    private val rows = CalendarGrid.ROWS

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val press = PressTracker(touchSlop.toFloat())

    private val weekdayLabels = arrayOf("일", "월", "화", "수", "목", "금", "토")

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_tertiary)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12.5f)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(9f)
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_tertiary)
    }
    private val offDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(9f)
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.off_day)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }

    private val colorTextPrimary = ContextCompat.getColor(context, R.color.text_primary)
    private val colorTextSecondary = ContextCompat.getColor(context, R.color.text_secondary)
    private val colorTextTertiary = ContextCompat.getColor(context, R.color.text_tertiary)
    private val colorProfit = ContextCompat.getColor(context, R.color.profit)
    private val colorLoss = ContextCompat.getColor(context, R.color.loss)
    private val colorAccent = ContextCompat.getColor(context, R.color.accent)
    private val colorAccentDim = ContextCompat.getColor(context, R.color.accent_dim)
    private val colorChartBar = ContextCompat.getColor(context, R.color.chart_bar)
    private val colorProfitDim = ContextCompat.getColor(context, R.color.profit_dim)
    private val colorOffDay = ContextCompat.getColor(context, R.color.off_day)
    private val colorOffDayDim = ContextCompat.getColor(context, R.color.off_day_dim)

    init {
        // 터치를 직접 받는 커스텀 뷰 — 접근성(performClick)과 기본 터치 처리를 위해 clickable 로 둔다.
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (headerHeight + cellHeight * rows).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    private val contentWidth: Int get() = width - paddingLeft - paddingRight

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gridWidth = contentWidth
        val cellWidth = gridWidth / CalendarGrid.COLS.toFloat()
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()

        // 요일 헤더
        for (i in 0 until CalendarGrid.COLS) {
            val x = left + cellWidth * (i + 0.5f)
            headerPaint.color = when (i) {
                0 -> colorLoss
                CalendarGrid.COLS - 1 -> colorChartBar
                else -> colorTextTertiary
            }
            canvas.drawText(weekdayLabels[i], x, top + headerHeight * 0.7f, headerPaint)
        }

        val maxIncome = summaries.values.maxOfOrNull { it.income } ?: 0
        val inset = dp(2f)

        for (index in 0 until CalendarGrid.CELLS) {
            val date = CalendarGrid.dateAt(index, month)
            val col = CalendarGrid.colOf(index)
            val bounds = CalendarGrid.cellBounds(
                index, gridWidth, paddingLeft, paddingTop, headerHeight, cellHeight, inset
            )
            val rect = RectF(bounds[0], bounds[1], bounds[2], bounds[3])
            val inMonth = CalendarGrid.isInMonth(date, month)
            val summary = if (inMonth) summaries[date] else null
            // 휴무는 운행이 없는 날에만 보여준다 — 휴무로 표시해 둔 날에 콜을 적으면 기록이 우선이다
            val isOffDay = inMonth && date in offDays && (summary == null || summary.rides == 0)

            val isSelected = date == selectedDay
            val isToday = date == today
            val isPressed = inMonth && index == press.pressedIndex

            if (isSelected) {
                cellPaint.color = colorAccentDim
                canvas.drawRoundRect(rect, dp(12f), dp(12f), cellPaint)
                strokePaint.color = colorAccent
                strokePaint.strokeWidth = dp(1.4f)
                canvas.drawRoundRect(rect, dp(12f), dp(12f), strokePaint)
            } else if (summary != null && summary.rides > 0 && maxIncome > 0) {
                // 총수입 비중에 따라 배경 농도
                val ratio = (summary.income.toFloat() / maxIncome).coerceIn(0.12f, 1f)
                val alpha = (40 + 90 * ratio).toInt()
                cellPaint.color = withAlpha(colorProfitDim, alpha)
                canvas.drawRoundRect(rect, dp(12f), dp(12f), cellPaint)
            } else if (isToday) {
                strokePaint.color = colorAccent
                strokePaint.strokeWidth = dp(1.2f)
                strokePaint.alpha = 140
                canvas.drawRoundRect(rect, dp(12f), dp(12f), strokePaint)
                strokePaint.alpha = 255
            } else if (isOffDay) {
                // 휴무 칸은 배경도 살짝 죽여 둔다 — 한 달을 훑을 때 일한 날과 바로 갈라진다
                cellPaint.color = withAlpha(colorOffDayDim, 150)
                canvas.drawRoundRect(rect, dp(12f), dp(12f), cellPaint)
            }

            // 손가락이 닿아 있는 동안 눌린 느낌 — 눌러도 되는 칸이라는 걸 바로 알 수 있게
            if (isPressed) {
                cellPaint.color = withAlpha(colorAccent, 46)
                canvas.drawRoundRect(rect, dp(12f), dp(12f), cellPaint)
            }

            if (!inMonth) {
                dayPaint.color = withAlpha(colorTextTertiary, 70)
                dayPaint.textSize = dp(12f)
                canvas.drawText(date.dayOfMonth.toString(), rect.centerX(), rect.top + dp(17f), dayPaint)
                continue
            }

            dayPaint.color = when {
                isSelected -> colorAccent
                isToday -> colorAccent
                col == 0 -> colorLoss
                col == CalendarGrid.COLS - 1 -> colorChartBar
                else -> colorTextPrimary
            }
            dayPaint.textSize = dp(12.5f)
            canvas.drawText(date.dayOfMonth.toString(), rect.centerX(), rect.top + dp(17f), dayPaint)

            if (summary != null && summary.rides > 0) {
                amountPaint.color = if (summary.income >= 0) colorProfit else colorLoss
                canvas.drawText(
                    Money.compact(summary.income),
                    rect.centerX(),
                    rect.top + dp(33f),
                    amountPaint
                )
                countPaint.color = if (isSelected) colorAccent else colorTextTertiary
                canvas.drawText("${summary.rides}콜", rect.centerX(), rect.top + dp(45f), countPaint)
            } else if (isOffDay) {
                drawOffDayLabel(canvas, rect)
                if (summary != null && summary.expenses > 0) {
                    amountPaint.color = colorTextSecondary
                    amountPaint.textSize = dp(8.5f)
                    canvas.drawText(
                        "-${Money.compact(summary.expenses)}",
                        rect.centerX(),
                        rect.top + dp(48f),
                        amountPaint
                    )
                    amountPaint.textSize = dp(10f)
                }
            } else if (summary != null && summary.expenses > 0) {
                amountPaint.color = colorTextSecondary
                canvas.drawText("-${Money.compact(summary.expenses)}", rect.centerX(), rect.top + dp(33f), amountPaint)
            }
        }
    }

    /** 달력 칸 가운데의 작은 '휴무' 표 — 칸 너비에 맞춰 글자 폭을 재고 둥근 배경을 깐다 */
    private fun drawOffDayLabel(canvas: Canvas, rect: RectF) {
        val label = "휴무"
        val padding = dp(7f)
        val pillWidth = (offDayPaint.measureText(label) + padding * 2f).coerceAtMost(rect.width() - dp(4f))
        val pillHeight = dp(16f)
        val top = rect.top + dp(22f)
        val pill = RectF(
            rect.centerX() - pillWidth / 2f, top,
            rect.centerX() + pillWidth / 2f, top + pillHeight
        )
        cellPaint.color = colorOffDayDim
        canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, cellPaint)
        canvas.drawText(
            label,
            rect.centerX(),
            pill.centerY() - (offDayPaint.descent() + offDayPaint.ascent()) / 2f,
            offDayPaint
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun indexAtPoint(x: Float, y: Float): Int? = CalendarGrid.indexAt(
        x = x,
        y = y,
        contentWidth = contentWidth,
        paddingLeft = paddingLeft,
        paddingTop = paddingTop,
        headerHeight = headerHeight,
        cellHeight = cellHeight
    )

    private fun dateAtPoint(x: Float, y: Float): LocalDate? =
        indexAtPoint(x, y)?.let { CalendarGrid.dateAt(it, month) }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = indexAtPoint(event.x, event.y)
                val consumed = press.down(event.x, event.y, index)
                invalidate()
                if (!consumed) {
                    // 요일 헤더 등 그리드 바깥 — 스크롤이 처리하도록 넘긴다.
                    return false
                }
                // 누르는 동안에는 부모 스크롤이 가로체지 않게 해서 ACTION_UP 을 받는다.
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!press.isTracking) return true
                if (press.move(event.x, event.y)) {
                    // 스크롤하려는 손짓 — 날짜 선택을 취소하고 스크롤에 넘긴다.
                    invalidate()
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val isTap = press.up(event.x, event.y)
                invalidate()
                if (!isTap) return true
                val date = dateAtPoint(event.x, event.y) ?: return true
                performClick()
                if (isHapticFeedbackEnabled) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                selectedDay = date
                onDayClick?.invoke(date)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                press.cancel()
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
