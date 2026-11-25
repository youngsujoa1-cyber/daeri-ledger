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
 * 팁·경유비는 플랫폼과 상관없이 **받은 금액**을 적는다(v1.0.13).
 * 설정의 `팁 · 경유비` 수수료율(기본 20%)로 총액을 역산한다.
 *   팁 총액 = 받은 팁 ÷ (1 − 팁 수수료율), 팁 수수료 = 팁 총액 − 받은 팁.
 *   (받은 10,000원 · 20% → 총액 12,500원, 수수료 2,500원, 손에 쥐는 돈 10,000원)
 * 설정에서 `0%`로 두면 팁에 수수료를 떼지 않는다(입력값 그대로 +팁, 수수료 0원).
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
        /** 요금에서 뗀 수수료 */
        val fareFee: Int,
        /** 팁·경유비에서 뗀 수수료 */
        val tipFee: Int,
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

    /** 입력값이 총매출인 플랫폼. 팁·경유비는 받은 금액을 그대로 역산해 더한다. */
    fun fromGross(
        gross: Int,
        tip: Int,
        feePercent: Double,
        tipFeePercent: Double,
        insurancePerCall: Int,
        employmentInsurancePercent: Double
    ): Breakdown {
        val safeGross = gross.coerceAtLeast(0)
        val safeTip = tip.coerceAtLeast(0)
        val fareFee = feeOf(safeGross, feePercent)
        val tipGross = grossFromNet(safeTip, tipFeePercent)
        val tipFee = tipGross - safeTip
        val fee = fareFee + tipFee
        val insurance = insuranceOf(safeGross, insurancePerCall, employmentInsurancePercent)
        val total = safeGross + tipGross
        return Breakdown(
            gross = safeGross,
            tipGross = tipGross,
            total = total,
            fareFee = fareFee,
            tipFee = tipFee,
            fee = fee,
            insurance = insurance,
            // 저장된 값 그대로 성립하게: 총액 − 수수료 − 보험료
            net = total - fee - insurance
        )
    }

    /**
     * 입력값이 수수료 차감 후 금액(실수령액)인 플랫폼.
     * 요금은 총매출로 역산하고, 팁·경유비도 받은 금액을 팁 수수료율로 역산한다. (8,000원 · 20% → 팁 총액 10,000원)
     */
    fun fromNet(
        net: Int,
        tip: Int,
        feePercent: Double,
        tipFeePercent: Double,
        insurancePerCall: Int,
        employmentInsurancePercent: Double
    ): Breakdown {
        val safeNet = net.coerceAtLeast(0)
        val safeTip = tip.coerceAtLeast(0)
        val gross = grossFromNet(safeNet, feePercent)
        val fareFee = (gross - safeNet).coerceAtLeast(0)
        val tipGross = grossFromNet(safeTip, tipFeePercent)
        val tipFee = tipGross - safeTip
        val fee = fareFee + tipFee
        val insurance = insuranceOf(gross, insurancePerCall, employmentInsurancePercent)
        val total = gross + tipGross
        return Breakdown(
            gross = gross,
            tipGross = tipGross,
            total = total,
            fareFee = fareFee,
            tipFee = tipFee,
            fee = fee,
            insurance = insurance,
            // 손에 쥐는 돈 = 받은 요금 + 받은 팁 − 보험료
            net = total - fee - insurance
        )
    }

    /** 입력값이 총매출인지 실수령액인지 netShown 으로 골라 계산한다. */
    fun calculate(
        input: Int,
        tip: Int,
        feePercent: Double,
        tipFeePercent: Double,
        insurancePerCall: Int,
        employmentInsurancePercent: Double,
        netShown: Boolean = false
    ): Breakdown =
        if (netShown) {
            fromNet(input, tip, feePercent, tipFeePercent, insurancePerCall, employmentInsurancePercent)
        } else {
            fromGross(input, tip, feePercent, tipFeePercent, insurancePerCall, employmentInsurancePercent)
        }

    /**
     * 저장된 기록의 팁·경유비를 수정 화면 입력칸에 되돌린다 — 적었던 `받은 팁`으로.
     * 저장된 수수료는 요금 수수료 + 팁 수수료라서, 요금 몫을 빼면 팁 몫이 남는다.
     * 예) 저장: 요금 45,000 · 팁 총액 12,500 · 수수료 11,500(요금 9,000 + 팁 2,500) → 입력칸 10,000
     * 팁에 수수료가 없던 옛 기록(v1.0.12 이전)은 요금 수수료만 저장돼 있어 그대로 팁 총액이 입력칸에 들어간다.
     */
    fun tipInputFrom(
        gross: Int,
        tip: Int,
        feeAmount: Int,
        feePercent: Double
    ): Int {
        if (tip <= 0) return tip
        val fareFee = feeOf(gross, feePercent)
        val tipFee = (feeAmount - fareFee).coerceIn(0, tip)
        return (tip - tipFee).coerceIn(0, tip)
    }

    /** 수정 화면에 되돌릴 값 — 요금 입력칸과 팁·경유비 입력칸 */
    data class Inputs(val amount: Int, val tip: Int)

    /**
     * 저장된 기록을 수정 화면 입력칸으로 되돌린다.
     * 예) 저장: 요금 25,000 · 팁 10,000 · 수수료 7,000(요금 5,000 + 팁 2,000) → 입력칸 20,000 · 8,000
     */
    fun inputsFrom(
        gross: Int,
        tip: Int,
        feeAmount: Int,
        feePercent: Double,
        netShown: Boolean
    ): Inputs {
        val tipInput = tipInputFrom(gross, tip, feeAmount, feePercent)
        if (!netShown) return Inputs(gross, tipInput)
        val tipFee = (tip - tipInput).coerceAtLeast(0)
        val amount = (gross - (feeAmount - tipFee)).coerceAtLeast(0)
        return Inputs(amount, tipInput)
    }

    private fun feeOf(gross: Int, feePercent: Double): Int =
        (gross.coerceAtLeast(0) * feePercent.coerceIn(0.0, MAX_FEE_PERCENT) / 100.0).roundToInt()

    private fun insuranceOf(gross: Int, insurancePerCall: Int, employmentPercent: Double): Int {
        val employment = (gross * employmentPercent.coerceAtLeast(0.0) / 100.0).roundToInt()
        return insurancePerCall.coerceAtLeast(0) + employment
    }

    private const val MAX_FEE_PERCENT = 90.0
}
