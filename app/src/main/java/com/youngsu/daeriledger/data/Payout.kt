package com.youngsu.daeriledger.data

import kotlin.math.roundToInt

/**
 * 순수익 계산기 — 앱의 핵심 규칙을 한 곳에 모아 둔 순수 함수.
 *
 * 입력 금액의 뜻은 플랫폼에 따라 두 가지다.
 * - 총매출 모드(로지·콜마너·아이콘·개인콜): 입력값이 수수료 떼기 전 금액.
 *   수수료 = 총매출 × 수수료율.
 * - 차감 후 금액 모드(카카오T·티맵·핸들): 앱에 이미 수수료가 빠진 금액이 보이므로 입력값이 실수령액.
 *   총매출 = 입력값 ÷ (1 − 수수료율), 수수료 = 총매출 − 입력값. (20,000원 · 20% → 총매출 25,000원, 수수료 5,000원)
 *
 * 두 모드 모두 순수익 = 총매출 + 팁 − 수수료 − (건당 보험료 + 고용·산재 보험료).
 */
object Payout {

    data class Breakdown(
        /** 총매출 — 수수료를 떼기 전 금액 */
        val gross: Int,
        /** 총매출 + 팁 */
        val total: Int,
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

    /** 입력값이 총매출인 플랫폼 */
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
            total = total,
            fee = fee,
            insurance = insurance,
            net = total - fee - insurance
        )
    }

    /** 입력값이 수수료 차감 후 금액(실수령액)인 플랫폼 */
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
        val fee = (gross - safeNet).coerceAtLeast(0)
        val insurance = insuranceOf(gross, insurancePerCall, employmentInsurancePercent)
        return Breakdown(
            gross = gross,
            total = gross + safeTip,
            fee = fee,
            insurance = insurance,
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

    private fun insuranceOf(gross: Int, insurancePerCall: Int, employmentPercent: Double): Int {
        val employment = (gross * employmentPercent.coerceAtLeast(0.0) / 100.0).roundToInt()
        return insurancePerCall.coerceAtLeast(0) + employment
    }

    private const val MAX_FEE_PERCENT = 90.0
}
