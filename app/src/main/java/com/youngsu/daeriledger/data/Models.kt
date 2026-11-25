package com.youngsu.daeriledger.data

import java.time.LocalDate

/** 대리(지정운전) / 탁송(차량 운송) 구분 */
enum class ServiceType(val id: String, val label: String) {
    DAERI("daeri", "대리"),
    TAKSONG("taksong", "탁송");

    companion object {
        fun from(id: String?): ServiceType = entries.firstOrNull { it.id == id } ?: DAERI
    }
}

/**
 * 결제 수단. 카드·포인트 결제도 손님에게 나중에 받는 후불이라 화면에는 `후불`로 쓰고,
 * 현장에서 받지 못한 후불·미수는 `미수금`으로 잡힌다.
 * 저장되는 id(cash/card/unpaid)는 그대로라 예전 기록도 그대로 읽힌다.
 */
enum class PaymentType(val id: String, val label: String, val short: String) {
    CASH("cash", "현금", "현금"),
    CARD("card", "후불", "후불"),
    UNPAID("unpaid", "미수금", "미수");

    companion object {
        fun from(id: String?): PaymentType = entries.firstOrNull { it.id == id } ?: CASH
    }
}

/**
 * 플랫폼 정의.
 * netShown = true 는 앱에 이미 "수수료 차감 후 금액"이 표시되는 플랫폼이라는 뜻이다.
 * 이 경우 입력값이 실수령액(수수료 떼고 받은 돈)이고, 총매출은 수수료율로 역산한다.
 * 예) 카카오T 20,000원 입력 → 총매출 25,000원, 수수료 5,000원 (수수료율 20%).
 */
data class PlatformDef(
    val id: String,
    val label: String,
    val defaultFeePercent: Double,
    val defaultNetShown: Boolean,
    val brandColor: Long
)

object Platforms {
    val ALL: List<PlatformDef> = listOf(
        PlatformDef("kakao", "카카오T", 20.0, true, 0xFFF9E000),
        PlatformDef("tmap", "티맵", 20.0, true, 0xFF2E7DF6),
        PlatformDef("handle", "핸들", 20.0, true, 0xFF00C2A8),
        PlatformDef("roji", "로지", 20.0, false, 0xFFFF6B3D),
        PlatformDef("colmaner", "콜마너", 20.0, false, 0xFF9B6BFF),
        PlatformDef("icon", "아이콘", 20.0, false, 0xFF33C56E),
        PlatformDef("personal", "개인콜", 0.0, false, 0xFFB7C4D2)
    )

    fun byId(id: String?): PlatformDef = ALL.firstOrNull { it.id == id } ?: ALL.first()

    fun label(id: String?): String = byId(id).label
}

/**
 * 한 건의 운행 기록.
 * gross = 총매출(수수료를 떼기 전 금액), tip = 팁·경유비 총액(수수료를 떼기 전 금액),
 * feeAmount = 플랫폼 수수료(요금 수수료 + 팁 수수료), insuranceAmount = 건당 보험료 + 고용·산재 보험료 합계.
 * net = gross + tip - feeAmount - insuranceAmount
 * (카카오T·티맵·핸들처럼 수수료 차감 후 금액을 입력하는 플랫폼은 입력한 "받은 금액"에서 gross 와 tip 을
 *  수수료율로 역산해 총액 기준으로 저장한다. 예: 요금 20,000 · 팁 8,000 입력 → gross 25,000 · tip 10,000 · 수수료 7,000)
 */
data class Ride(
    val id: Long = 0L,
    val businessDay: LocalDate,
    val timeMillis: Long,
    val platformId: String,
    val service: ServiceType,
    val payment: PaymentType,
    val gross: Int,
    val feePercent: Double,
    val feeAmount: Int,
    val tip: Int,
    val insuranceAmount: Int,
    val fromPlace: String = "",
    val viaPlace: String = "",
    val toPlace: String = "",
    val memo: String = "",
    val spot: String = "",
    val settled: Boolean = true,
    val createdAt: Long = 0L
) {
    val total: Int get() = gross + tip
    val net: Int get() = total - feeAmount - insuranceAmount
    val platformLabel: String get() = Platforms.label(platformId)
    val isUnpaid: Boolean get() = payment == PaymentType.UNPAID && !settled
    /** 경로 — 적어 둔 곳만 순서대로. 경유가 있으면 출발 → 경유 → 도착 */
    val routeLabel: String
        get() = listOf(fromPlace, viaPlace, toPlace)
            .filter { it.isNotBlank() }
            .joinToString(" → ")
}

data class Expense(
    val id: Long = 0L,
    val businessDay: LocalDate,
    val timeMillis: Long,
    val category: String,
    val amount: Int,
    val memo: String = "",
    val createdAt: Long = 0L
)

/** 하루(영업일) 단위 집계 */
data class DaySummary(
    val businessDay: LocalDate,
    val rides: Int = 0,
    val gross: Int = 0,
    val tips: Int = 0,
    val fees: Int = 0,
    val insurance: Int = 0,
    val net: Int = 0,
    val expenses: Int = 0,
    val unpaidNet: Int = 0,
    val firstCallMillis: Long = 0L,
    val lastCallMillis: Long = 0L
) {
    /** 지출까지 뺀 진짜 손에 쥐는 돈 */
    /** 총수입 = 총매출 + 팁·경유비 (수수료·보험·지출 빼기 전 금액) */
    val income: Int get() = gross + tips
    val takeHome: Int get() = net - expenses
    val avgPerCall: Int get() = if (rides == 0) 0 else net / rides
    val spanMinutes: Long
        get() = if (firstCallMillis == 0L) 0L else ((lastCallMillis - firstCallMillis) / 60000L).coerceAtLeast(0L)
    /** 첫 콜~마지막 콜 사이 시간으로 계산한 시간당 순수익(최소 1시간) */
    val hourlyNet: Int
        get() {
            if (rides == 0) return 0
            val hours = (spanMinutes / 60.0).coerceAtLeast(1.0)
            return (net / hours).toInt()
        }
}

data class PlatformStat(
    val platformId: String,
    val label: String,
    val rides: Int,
    val gross: Int,
    val net: Int,
    val fees: Int
) {
    val avgNet: Int get() = if (rides == 0) 0 else net / rides
    val effectiveFeeRate: Double get() = if (gross == 0) 0.0 else fees * 100.0 / gross
}

/** 사용자가 직접 추가한 콜밭. lat/lng 가 없으면(위치 권한 거부 등) 이름만 저장된다. */
data class MySpot(
    val id: Long = 0L,
    val name: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val peak: String = "",
    val memo: String = "",
    val createdAt: Long = 0L,
    /** 구분 — 대리 / 탁송 (콜밭 목록에서 어느 출처에 함께 보일지 정해진다) */
    val service: ServiceType = ServiceType.DAERI,
    /** 시·도/지역 또는 주소 (예: `서울 강남구 역삼동`) */
    val area: String = ""
) {
    val hasLocation: Boolean get() = lat != null && lng != null
}

/** 기록에서 뽑아낸 내 실전 꿀콜밭 통계 */
data class SpotStat(
    val spot: String,
    val rides: Int,
    val net: Int,
    val fares: Int
) {
    val avgFare: Int get() = if (rides == 0) 0 else fares / rides
    val avgNet: Int get() = if (rides == 0) 0 else net / rides
}
