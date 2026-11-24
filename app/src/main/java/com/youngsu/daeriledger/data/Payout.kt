package com.youngsu.daeriledger.data

import kotlin.math.roundToInt

/**
 * 순수익 계산기 — 앱의 핵심 규칙을 한 곳에 모아 둔 순수 함수.
 *
 * 요금 입력 금액의 뜻은 플랫폼에 따라 두 가지다.
 * - 총매출 모드(로지·콜마너·아이콘·개인콜): 입력값이 수수료 떼기 전 금액.
 *   수수료 = 총매출 × 수수료율.
 * - 차감 후 금액 모드(카카오T·티맵·핸들): 앱에 이미 수수료가 빠진 금액이 보이므로 입력값이 실수령액.
 *   총매출 = 입력값 ÷ (1 − 수수료율), 수수료 = 총매출 − 입력값. (20,000원 · 20% → 총매출 25,000원, 수수료 5,000원)
 *
 * 팁·경유비도 요금과 똑같이 다룬다.
 * - 차감 후 금액 모드: 팁·경유비 칸에도 앱에 찍힌(수수료 떼고 받은) 금액을 적는다.
 *   팁 총액 = 입력값 ÷ (1 − 수수료율), 그 차액이 팁 수수료. (8,000원 · 20% → 10,000원, 수수료 2,000원, 손에 쥐는 돈 8,000원)
 * - 총매출 모드: 입력값이 곧 팁·경유비 총액이고 팁에는 수수료가 붙지 않는다.
 *
 * 두 모드 모두 순수익 = (요금 총매출 + 팁 총액) − (요금 수수료 + 팁 수수료) − (건당 보험료 + 고용·산재 보험료).
 */
object Payout {

    data class Breakdown(
        /** 요금 총매출 — 수수료를 떼기 전 금액 */
        val gross: Int,
        /** 팁·경유비의 총액(수수료를 떼기 전) */
        val tipGross: Int,
        /** 총매출 + 팁 총액 */
        val total: Int,
        /** 요금 수수료 + 팁 수수료 */
        val fee: Int,
        val insurance: Int,
        val net: Int
    )

    /** 수수료가 이미 빠진 금액(실수령액)에서 총매출을 역산한다. 20,000원 · 20% → 25,000원 */
    fun grossFromNet(net: Int, feePercent: Double): Int {
        val safeNet = net.coerceAtLeast(0)
        val rate = feePercent.coerceIn(0.0, MAX_FEE_PERCENT) / 100.0
        if (rate <= 0.0) return safeNet
        return (safeNet / (1.0 - rate)).roundToInt()
    }

    /** 입력값이 총매출인 플랫폼. 팁·경유비는 입력값 그대로 총액에 더한다. */
    fun fromGross(
        gross: Int,
        tip: Int,
        feePercent: Double,
        insurancePerCall: Int,
        employmentInsurancePercent: Double
    ): Breakdown {
        val safeGross = gross.coerceAtLeast(0)
        val safeTip = tip.coerceAtLeast(0)
        val fee = (safeGross * feePercent.coerceIn(0.0, MAX_FEE_PERCENT) / 100.0).roundToInt()
        val insurance = insuranceOf(safeGross, insurancePerCall, employmentInsurancePercent)
        val total = safeGross + safeTip
        return Breakdown(
            gross = safeGross,
            tipGross = safeTip,
            total = total,
            fee = fee,
            insurance = insurance,
            net = total - fee - insurance
        )
    }

    /**
     * 입력값이 수수료 차감 후 금액(실수령액)인 플랫폼.
     * 요금·팁·경유비 모두 "받은 금액"으로 보고 각각 총액을 역산한다. (8,000원 · 20% → 팁 총액 10,000원, 팁 수수료 2,000원)
     */
    fun fromNet(
        net: Int,
        tip: Int,
        feePercent: Double,
        insurancePerCall: Int,
        employmentInsurancePercent: Double
    ): Breakdown {
        val safeNet = net.coerceAtLeast(0)
        val safeTip = tip.coerceAtLeast(0)
        val rate = feePercent.coerceIn(0.0, MAX_FEE_PERCENT)
        val gross = grossFromNet(safeNet, rate)
        val tipGross = grossFromNet(safeTip, rate)
        // 요금에서 뗀 수수료 + 팁에서 뗀 수수료
        val fee = (gross - safeNet).coerceAtLeast(0) + (tipGross - safeTip).coerceAtLeast(0)
        val insurance = insuranceOf(gross, insurancePerCall, employmentInsurancePercent)
        return Breakdown(
            gross = gross,
            tipGross = tipGross,
            total = gross + tipGross,
            fee = fee,
            insurance = insurance,
            // 손에 쥐는 돈 = 받은 요금 + 받은 팁 − 보험료
            net = safeNet + safeTip - insurance
        )
    }

    /** 입력값이 총매출인지 실수령액인지 netShown 으로 골라 계산한다. */
    fun calculate(
        input: Int,
        tip: Int,
        feePercent: Double,
        insurancePerCall: Int,
        employmentInsurancePercent: Double,
        netShown: Boolean = false
    ): Breakdown =
        if (netShown) {
            fromNet(input, tip, feePercent, insurancePerCall, employmentInsurancePercent)
        } else {
            fromGross(input, tip, feePercent, insurancePerCall, employmentInsurancePercent)
        }

    /**
     * 저장된 기록의 팁·경유비를 수정 화면 입력칸에 되돌린다.
     * 차감 후 금액 모드에서 저장된 tip 은 총액 기준이므로, 저장된 수수료에서 요금 몫을 빼 팁 수수료를 구해
     * `팁 총액 − 팁 수수료` = 손님이 보낸(수수료 떼고 받은) 금액으로 돌려놓는다. 두 번 역산되지 않는다.
     * 예) 요금 20,000 · 팁 총액 10,000 · 수수료 7,000(요금 5,000 + 팁 2,000) → 입력칸 8,000
     * v1.0.11 이전 기록처럼 팁에 수수료가 없던 행은 그대로 팁 총액이 입력칸에 들어간다.
     */
    fun tipInputFrom(
        gross: Int,
        tip: Int,
        feeAmount: Int,
        feePercent: Double,
        netShown: Boolean
    ): Int {
        if (!netShown || tip <= 0) return tip
        val grossFee = (gross.coerceAtLeast(0) * feePercent.coerceIn(0.0, MAX_FEE_PERCENT) / 100.0).roundToInt()
        val tipFee = (feeAmount - grossFee).coerceAtLeast(0)
        return (tip - tipFee).coerceAtLeast(0)
    }

    /** 수정 화면에 되돌릴 값 — 요금 입력칸과 팁·경유비 입력칸 */
    data class Inputs(val amount: Int, val tip: Int)

    /**
     * 저장된 기록을 수정 화면 입력칸으로 되돌린다. 저장된 수수료에는 팁 몫이 섮여 있으므로,
     * 팁에 붙은 수수료를 덜고 요금에서 뗀 수수료만 빼서 원래 적었던 금액을 찾는다.
     * 예) 저장: 요금 25,000 · 팁 10,000 · 수수료 7,000(요금 5,000 + 팁 2,000) → 입력칸 20,000 · 8,000
     */
    fun inputsFrom(
        gross: Int,
        tip: Int,
        feeAmount: Int,
        feePercent: Double,
        netShown: Boolean
    ): Inputs {
        val tipInput = tipInputFrom(gross, tip, feeAmount, feePercent, netShown)
        if (!netShown) return Inputs(gross, tipInput)
        val tipFee = (tip - tipInput).coerceAtLeast(0)
        val amount = (gross - (feeAmount - tipFee)).coerceAtLeast(0)
        return Inputs(amount, tipInput)
    }

    private fun insuranceOf(gross: Int, insurancePerCall: Int, employmentPercent: Double): Int {
        val employment = (gross * employmentPercent.coerceAtLeast(0.0) / 100.0).roundToInt()
        return insurancePerCall.coerceAtLeast(0) + employment
    }

    private const val MAX_FEE_PERCENT = 90.0
}
