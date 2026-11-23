package com.youngsu.daeriledger.ui.stats

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.DaySummary
import com.youngsu.daeriledger.data.PlatformStat
import com.youngsu.daeriledger.data.Ride
import com.youngsu.daeriledger.databinding.ItemPlatformRowBinding
import com.youngsu.daeriledger.databinding.ScreenStatsBinding
import com.youngsu.daeriledger.ui.MainActivity
import com.youngsu.daeriledger.ui.Screen
import com.youngsu.daeriledger.ui.widget.BarChartView
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.Money
import com.youngsu.daeriledger.util.color
import com.youngsu.daeriledger.util.dp
import com.youngsu.daeriledger.util.setChips
import java.time.LocalDate
import java.time.YearMonth

/**
 * 기간별 통계 — 플랫폼 비교, 요일·시간대 패턴, 지출 분석, 인사이트.
 *
 * 금액은 모두 Long 으로 계산한다. 전체 기간처럼 합계가 4~5억을 넘으면
 * Int 곱셈(`net * 100`)이 넘쳐 비중이 -3% 처럼 뒤집혀 보이기 때문이다.
 */
class StatsScreen(private val activity: MainActivity) : Screen {

    private val binding = ScreenStatsBinding.inflate(activity.layoutInflater)
    override val view: View get() = binding.root

    private var period = 0

    init {
        binding.periodChips.setChips(
            listOf("0" to "이번 달", "1" to "지난 달", "2" to "최근 3개월", "3" to "전체"),
            "0"
        ) { id ->
            period = id.toIntOrNull() ?: 0
            refresh()
        }
    }

    override fun refresh() {
        val today = BizTime.businessDayOfNow(activity.settings.cutoffHour)
        val (from, to) = range(today)
        // 요약과 플랫폼 집계는 DB 집계로 바로 받고(수만 건도 빠르다),
        // 운행 목록은 요일·시간대·최고의 하루 패턴을 그릴 때 한 번만 읽는다.
        val summary = activity.repo.summaryBetween(from, to)
        val platformStats = activity.repo.platformStats(from, to)
        val expenseTotals = activity.repo.expenseTotalsByCategory(from, to)

        binding.periodRangeText.text = "${from.monthValue}/${from.dayOfMonth} ~ ${to.monthValue}/${to.dayOfMonth}"

        binding.statsColumn.removeAllViews()

        if (summary.rides == 0 && summary.expenses == 0) {
            addEmptyCard()
            return
        }

        val rides = activity.repo.ridesBetween(from, to)
        // 그날 콜이 있는데 동시에 휴무로 표시된 경우는 ‘일한 날’로 본다 (겁쳐 세지 않는다)
        val workedDays = rides.map { it.businessDay }.toHashSet()
        val offDayCount = activity.repo.offDaysBetween(from, to).count { it !in workedDays }

        addSummaryCard(summary, rides, offDayCount)
        if (platformStats.isNotEmpty()) addPlatformCard(platformStats)
        addWeekdayCard(rides)
        addHourCard(rides)
        if (expenseTotals.isNotEmpty()) addExpenseCard(expenseTotals)
        addInsightCard(summary, platformStats, rides)
    }

    private fun range(today: LocalDate): Pair<LocalDate, LocalDate> {
        return when (period) {
            1 -> {
                val last = YearMonth.from(today).minusMonths(1)
                last.atDay(1) to last.atEndOfMonth()
            }
            2 -> today.minusMonths(2).withDayOfMonth(1) to today
            3 -> (activity.repo.firstBusinessDay() ?: today) to today
            else -> {
                val thisMonth = YearMonth.from(today)
                thisMonth.atDay(1) to thisMonth.atEndOfMonth()
            }
        }
    }

    // ------------------------------------------------------------- 카드 생성

    private fun newCard(title: String?, subtitle: String? = null): LinearLayout {
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(activity.color(R.color.surface_alt))
            strokeColor = activity.color(R.color.outline)
            strokeWidth = activity.dp(1f)
            radius = activity.dp(20f).toFloat()
            cardElevation = 0f
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16f), activity.dp(14f), activity.dp(16f), activity.dp(14f))
        }
        card.addView(column)
        if (title != null) {
            column.addView(
                TextView(activity).apply {
                    text = title
                    setTextColor(activity.color(R.color.text_primary))
                    textSize = 15f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
            )
        }
        if (subtitle != null) {
            column.addView(
                TextView(activity).apply {
                    text = subtitle
                    setTextColor(activity.color(R.color.text_tertiary))
                    textSize = 11f
                    setPadding(0, activity.dp(2f), 0, 0)
                }
            )
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = activity.dp(10f) }
        binding.statsColumn.addView(card, params)
        return column
    }

    private fun addEmptyCard() {
        val column = newCard("아직 통계가 없어요")
        column.addView(
            TextView(activity).apply {
                text = "운행을 기록하면 플랫폼별 수익 비교, 요일·시간대 패턴이 자동으로 쌓여요."
                setTextColor(activity.color(R.color.text_secondary))
                textSize = 12f
                setPadding(0, activity.dp(6f), 0, 0)
                setLineSpacing(activity.dp(3f).toFloat(), 1f)
            }
        )
    }

    /**
     * 라벨 · 값 한 줄. 라벨은 필요한 만큼만 쓰고, 값이 길면 두 줄까지 오른쪽 정렬로 접힌다
     * (금액이 커졌을 때 라벨이 잘리거나 값이 화면 밖으로 밀리지 않게).
     */
    private fun addKeyValue(
        column: LinearLayout,
        label: String,
        value: String,
        colorRes: Int = R.color.text_primary,
        bold: Boolean = true
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(7f), 0, 0)
        }
        row.addView(
            TextView(activity).apply {
                text = label
                setTextColor(activity.color(R.color.text_secondary))
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = activity.dp(12f) }
        )
        row.addView(
            TextView(activity).apply {
                text = value
                setTextColor(activity.color(colorRes))
                textSize = 14f
                if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
                fontFeatureSettings = "tnum"
                gravity = Gravity.END
                maxLines = 2
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        column.addView(row)
    }

    private fun addSummaryCard(summary: DaySummary, rides: List<Ride>, offDayCount: Int) {
        val column = newCard("기간 요약")

        // 큰 숫자는 총수입(총매출 + 팁) — 오늘·달력 탭과 같은 기준
        val income = summary.income.toLong()
        val net = summary.net.toLong()
        val cost = summary.fees.toLong() + summary.insurance.toLong()
        val expense = summary.expenses.toLong()
        val takeHome = net - expense

        column.addView(
            TextView(activity).apply {
                text = Money.won(income)
                setTextColor(activity.color(if (income < 0) R.color.loss else R.color.profit))
                textSize = 28f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                fontFeatureSettings = "tnum"
                maxLines = 1
                setPadding(0, activity.dp(4f), 0, 0)
            }
        )
        column.addView(
            TextView(activity).apply {
                text = "총매출 + 팁 (수수료·보험 빼기 전)"
                setTextColor(activity.color(R.color.text_tertiary))
                textSize = 11f
                setPadding(0, activity.dp(1f), 0, activity.dp(2f))
            }
        )

        addKeyValue(
            column, "실수익", Money.won(net),
            if (net < 0) R.color.loss else R.color.profit
        )
        addKeyValue(column, "수수료·보험", "-" + Money.won(cost), R.color.text_secondary)
        addKeyValue(column, "지출", "-" + Money.won(expense), R.color.loss)
        addKeyValue(
            column, "손에 남은 돈", Money.won(takeHome),
            if (takeHome < 0) R.color.loss else R.color.profit
        )

        val calls = summary.rides.toLong()
        addKeyValue(column, "콜 수", "${summary.rides}건")
        if (calls > 0) {
            addKeyValue(column, "콜당 평균 실수익", Money.won(net / calls))
            addKeyValue(column, "콜당 평균 총수입", Money.won(income / calls))
        }

        val activeDays = rides.map { it.businessDay }.distinct().size
        if (activeDays > 0) {
            addKeyValue(column, "일한 날", "${activeDays}일")
            addKeyValue(column, "하루 평균 실수익", Money.won(net / activeDays))
        }
        // 휴무로 표시한 날은 ‘일한 날’에 넣지 않는다 — 따로 몇 일 쉬었는지만 보여준다
        if (offDayCount > 0) {
            addKeyValue(column, "휴무한 날", "${offDayCount}일", R.color.off_day)
        }

        val bestDay = rides.groupBy { it.businessDay }
            .maxByOrNull { (_, list) -> list.sumOf { it.net.toLong() } }
        if (bestDay != null) {
            val bestNet = bestDay.value.sumOf { it.net.toLong() }
            addKeyValue(
                column,
                "최고의 하루",
                "${BizTime.dayTitle(bestDay.key)} · ${Money.won(bestNet)}",
                R.color.accent
            )
        }
    }

    private fun addPlatformCard(stats: List<PlatformStat>) {
        val totalNet = stats.sumOf { it.net.toLong() }.coerceAtLeast(1L)
        val column = newCard(
            "플랫폼별 수익 비교",
            "기존 앱들이 안 보여주던 ‘어디서 돈이 남는지’ — 콜당 실수익까지 비교"
        )
        val maxNet = stats.maxOf { it.net.toLong() }.coerceAtLeast(1L)
        val topNet = stats.maxOf { it.net.toLong() }
        stats.forEach { stat ->
            val net = stat.net.toLong()
            val b = ItemPlatformRowBinding.inflate(activity.layoutInflater, column, false)
            b.rowName.text = stat.label
            b.rowMeta.text = "${stat.rides}콜 · 콜당 ${Money.won(if (stat.rides == 0) 0L else net / stat.rides)}" +
                if (stat.effectiveFeeRate > 0.05) " · 수수료 ${String.format("%.1f", stat.effectiveFeeRate)}%" else ""
            b.rowValue.text = Money.won(net)
            b.rowValue.setTextColor(activity.color(if (net < 0) R.color.loss else R.color.text_primary))
            b.rowBar.setProgressCompat((net * 100 / maxNet).toInt().coerceIn(2, 100), false)
            column.addView(b.root)
        }
        if (topNet > 0) {
            val share = topNet * 100 / totalNet
            column.addView(
                TextView(activity).apply {
                    text = "실수익 기준 · 1위 플랫폼 비중 ${share}%"
                    setTextColor(activity.color(R.color.text_tertiary))
                    textSize = 11f
                    setPadding(0, activity.dp(8f), 0, 0)
                }
            )
        }
    }

    private fun addWeekdayCard(rides: List<Ride>) {
        if (rides.isEmpty()) return
        val labels = arrayOf("월", "화", "수", "목", "금", "토", "일")
        val nets = LongArray(7)
        val counts = IntArray(7)
        rides.forEach { ride ->
            val idx = ride.businessDay.dayOfWeek.value - 1
            nets[idx] += ride.net.toLong()
            counts[idx] += 1
        }
        val column = newCard("요일별 실수익", "영업일 기준 · 막대 위 숫자는 실수익")
        val chart = BarChartView(activity).apply {
            bars = labels.indices.map { i ->
                BarChartView.Bar(labels[i], nets[i], "${counts[i]}콜")
            }
        }
        column.addView(
            chart,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(160f))
        )
        val best = nets.indices.maxByOrNull { nets[it] }
        if (best != null && nets[best] > 0L) {
            column.addView(
                TextView(activity).apply {
                    text = "가장 잘 벌린 요일은 ${labels[best]}요일 (${Money.won(nets[best])})"
                    setTextColor(activity.color(R.color.accent))
                    textSize = 12f
                    setPadding(0, activity.dp(6f), 0, 0)
                }
            )
        }
    }

    private fun addHourCard(rides: List<Ride>) {
        if (rides.isEmpty()) return
        val nets = LongArray(8)
        val counts = IntArray(8)
        rides.forEach { ride ->
            val hour = BizTime.timeOf(ride.timeMillis).hour
            val slot = ((hour + 6) % 24) / 3
            nets[slot] += ride.net.toLong()
            counts[slot] += 1
        }
        val labels = (0 until 8).map { i ->
            val start = (i * 3 + 18) % 24
            val end = (start + 3) % 24
            String.format("%02d-%02d", start, end)
        }
        val column = newCard("시간대별 실수익", "저녁 18시부터 3시간 단위")
        val chart = BarChartView(activity).apply {
            bars = labels.indices.map { i -> BarChartView.Bar(labels[i], nets[i], "${counts[i]}콜") }
        }
        column.addView(
            chart,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(160f))
        )
        val best = nets.indices.maxByOrNull { nets[it] }
        if (best != null && nets[best] > 0L) {
            column.addView(
                TextView(activity).apply {
                    text = "가장 수익이 좋은 시간대는 ${labels[best]} (${Money.won(nets[best])})"
                    setTextColor(activity.color(R.color.accent))
                    textSize = 12f
                    setPadding(0, activity.dp(6f), 0, 0)
                }
            )
        }
    }

    private fun addExpenseCard(totals: List<Pair<String, Int>>) {
        val total = totals.sumOf { it.second.toLong() }.coerceAtLeast(1L)
        val column = newCard("지출 분석", "이번 기간 지출 ${Money.won(total)}")
        val max = totals.maxOf { it.second.toLong() }.coerceAtLeast(1L)
        totals.forEach { (category, amount) ->
            val amountLong = amount.toLong()
            val b = ItemPlatformRowBinding.inflate(activity.layoutInflater, column, false)
            b.rowName.text = category
            b.rowMeta.text = "${amountLong * 100 / total}%"
            b.rowValue.text = Money.won(amountLong)
            b.rowValue.setTextColor(activity.color(R.color.loss))
            b.rowBar.setProgressCompat((amountLong * 100 / max).toInt().coerceIn(2, 100), false)
            b.rowBar.setIndicatorColor(activity.color(R.color.loss))
            column.addView(b.root)
        }
    }

    private fun addInsightCard(summary: DaySummary, stats: List<PlatformStat>, rides: List<Ride>) {
        val lines = ArrayList<String>()

        val topByNet = stats.maxByOrNull { it.net.toLong() }
        if (topByNet != null) {
            val net = topByNet.net.toLong()
            val avg = if (topByNet.rides == 0) 0L else net / topByNet.rides
            lines.add("실수익 1위 플랫폼은 ${topByNet.label} — ${topByNet.rides}콜 · ${Money.won(net)} (콜당 ${Money.won(avg)})")
        }
        val topByCalls = stats.maxByOrNull { it.rides }
        if (topByCalls != null && topByCalls.platformId != topByNet?.platformId) {
            val avg = if (topByCalls.rides == 0) 0L else topByCalls.net.toLong() / topByCalls.rides
            lines.add("콜 수가 가장 많은 곳은 ${topByCalls.label} (${topByCalls.rides}콜) — 콜당 ${Money.won(avg)}")
        }
        val revenue = summary.income.toLong()
        if (revenue > 0L) {
            val cost = summary.fees.toLong() + summary.insurance.toLong()
            lines.add("수수료·보험으로 나간 돈 ${Money.won(cost)} (총수입의 ${String.format("%.1f", cost * 100.0 / revenue)}%)")
        }
        if (rides.isNotEmpty()) {
            val topSpot = activity.repo
                .spotStats(rides.minOf { it.businessDay }, rides.maxOf { it.businessDay }, 1)
                .firstOrNull()
            if (topSpot != null) {
                lines.add("기록 기준 최고 콜밭은 ‘${topSpot.spot}’ (${topSpot.rides}콜 · ${Money.won(topSpot.net.toLong())})")
            }
        }
        if (lines.isEmpty()) lines.add("기록이 쌓이면 더 정확한 조언을 드릴 수 있어요.")

        val column = newCard("한눈에 보는 인사이트")
        lines.forEach { line ->
            column.addView(
                TextView(activity).apply {
                    text = "· $line"
                    setTextColor(activity.color(R.color.text_secondary))
                    textSize = 12.5f
                    setLineSpacing(activity.dp(3f).toFloat(), 1f)
                    setPadding(0, activity.dp(5f), 0, 0)
                }
            )
        }
    }

    private fun addProgress(color: Int): LinearProgressIndicator = LinearProgressIndicator(activity).apply {
        trackThickness = activity.dp(6f)
        setIndicatorColor(color)
    }
}
