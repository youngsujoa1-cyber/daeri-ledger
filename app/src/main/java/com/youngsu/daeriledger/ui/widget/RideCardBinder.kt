package com.youngsu.daeriledger.ui.widget

import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.PaymentType
import com.youngsu.daeriledger.data.Ride
import com.youngsu.daeriledger.databinding.ItemRideBinding
import com.youngsu.daeriledger.databinding.ItemUnpaidRideBinding
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.Money
import com.youngsu.daeriledger.util.applyBadge
import com.youngsu.daeriledger.util.dp

/**
 * 운행 카드 한 장의 글자를 채운다.
 * 카드의 첫 줄은 "어디서 어디로 갔는지"(경로), 아랫줄은 "어느 프로그램으로 잡았는지"(플랫폼·구분·결제).
 * 오늘·달력·미수 화면이 같은 규칙을 쓰도록 한 곳에 모아 둔다.
 */
object RideCardBinder {

    /** 카드 첫 줄 — 경로. 경로를 안 적은 기록은 플랫폼 이름으로 대신해 빈 줄이 생기지 않게 한다. */
    fun topLine(ride: Ride): String = ride.routeLabel.ifBlank { ride.platformLabel }

    /**
     * 카드 아랫줄 — 플랫폼 · 구분 · (시각) · (특이사항).
     * 경로가 없어 첫 줄이 이미 플랫폼 이름이면 플랫폼은 빼고 쓴다.
     */
    fun metaParts(ride: Ride, withTime: Boolean): List<String> = buildList {
        if (ride.routeLabel.isNotBlank()) add(ride.platformLabel)
        add(ride.service.label)
        if (withTime) add(BizTime.clockLabel(ride.timeMillis))
        if (ride.spot.isNotBlank()) add(ride.spot)
    }

    fun metaLine(ride: Ride, withTime: Boolean): String = metaParts(ride, withTime).joinToString(" · ")

    /** 오늘·달력 공통 운행 카드 */
    fun bind(b: ItemRideBinding, ride: Ride, onClick: (Ride) -> Unit) {
        val ctx = b.root.context
        b.timeText.text = BizTime.clockLabel(ride.timeMillis)
        b.partText.text = BizTime.partOfDayLabel(ride.timeMillis)

        val route = ride.routeLabel
        b.routeText.text = route.ifBlank { ride.platformLabel }
        // 첫 줄이 플랫폼 이름으로 대신 채워졌으면 아랫줄에서는 플랫폼을 또 쓰지 않는다
        b.platformText.text = ride.platformLabel
        b.platformText.visibility = if (route.isBlank()) View.GONE else View.VISIBLE
        // 플랫폼이 빠진 줄은 뱃지가 경로 첫 글자와 같은 자리에서 시작하게
        (b.serviceBadge.layoutParams as ViewGroup.MarginLayoutParams).marginStart =
            if (route.isBlank()) 0 else ctx.dp(6f)
        b.serviceBadge.requestLayout()

        b.serviceBadge.text = ride.service.label
        b.paymentBadge.text = ride.payment.short
        when (ride.payment) {
            PaymentType.UNPAID -> {
                b.paymentBadge.applyBadge(R.drawable.bg_badge_unpaid, R.color.unpaid)
                b.paymentBadge.text = if (ride.isUnpaid) "미수" else "입금"
            }
            else -> b.paymentBadge.applyBadge(R.drawable.bg_badge, R.color.text_secondary)
        }

        if (ride.spot.isBlank()) {
            b.spotText.visibility = View.GONE
        } else {
            b.spotText.visibility = View.VISIBLE
            b.spotText.text = ride.spot
        }

        // 큰 녹색 숫자 = 총수입(총매출 + 팁·경유비), 아래 회색 줄 = 실수익(수수료·보험 뺀 금액)
        b.incomeText.text = Money.won(ride.total)
        b.incomeText.setTextColor(
            ContextCompat.getColor(ctx, if (ride.total < 0) R.color.loss else R.color.profit)
        )
        b.netText.text = "실수익 ${Money.won(ride.net)}"
        b.root.setOnClickListener { onClick(ride) }
    }

    /** 미수금 화면 카드의 글자 (버튼·클릭은 화면 쪽에서 붙인다) */
    fun bindUnpaidLines(b: ItemUnpaidRideBinding, ride: Ride) {
        b.dayText.text = BizTime.dayTitle(ride.businessDay)
        b.titleText.text = topLine(ride)
        b.subText.text = metaLine(ride, withTime = true)
        b.incomeText.text = Money.won(ride.total)
        b.netText.text = "실수익 ${Money.won(ride.net)}"
    }
}
