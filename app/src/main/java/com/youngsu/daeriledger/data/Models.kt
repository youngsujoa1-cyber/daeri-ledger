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
 * chargesPerCallInsurance = false 는 건당 보험료가 자동으로 빠지지 않는 플랫폼이다(v1.0.20).
 * chargesEmploymentTax = false 는 매출의 고용·산재·세금(3.3%)도 붙지 않는 플랫폼이다(v1.0.33).
 * 카카오T·티맵은 건당 보험료 0원이고 매출의 3.3% 세금만 빠지고, 개인콜은 건당 보험료도 세금도 붙지 않는다.
 */
data class PlatformDef(
    val id: String,
    val label: String,
    val defaultFeePercent: Double,
    val defaultNetShown: Boolean,
    val brandColor: Long,
    val chargesPerCallInsurance: Boolean = true,
    val chargesEmploymentTax: Boolean = true
)

object Platforms {
    /**
     * 프로그램 순서 — 설정 화면과 운행 기록 칩이 모두 이 순서를 따른다.
     * `아이콘` 과 `개인콜` 사이에 v1.0.32 에서 넣은 7종이 들어간다.
     */
    val ALL: List<PlatformDef> = listOf(
        PlatformDef("kakao", "카카오T", 20.0, true, 0xFFF9E000, chargesPerCallInsurance = false),
        PlatformDef("tmap", "티맵", 20.0, true, 0xFF2E7DF6, chargesPerCallInsurance = false),
        PlatformDef("handle", "핸들", 20.0, true, 0xFF00C2A8),
        PlatformDef("roji", "로지", 20.0, false, 0xFFFF6B3D),
        PlatformDef("colmaner", "콜마너", 20.0, false, 0xFF9B6BFF),
        PlatformDef("icon", "아이콘", 20.0, false, 0xFF33C56E),
        // ---- v1.0.32 신규 7종 — 기본 수수료는 로지·콜마너·아이콘처럼 총매출에서 20% 차감
        PlatformDef("daegusarang", "대구사랑", 20.0, false, 0xFFFF5A5F),
        PlatformDef("daegucitizen", "대구시민", 20.0, false, 0xFF3D8BFD),
        PlatformDef("daegusejong", "대구세종", 20.0, false, 0xFF6F42C1),
        PlatformDef("trincall", "트리콜", 20.0, false, 0xFF00B894),
        PlatformDef("superdrive", "수퍼드라이버", 20.0, false, 0xFFF5A623),
        PlatformDef("openmile", "오픈마일", 20.0, false, 0xFF20C4C4),
        PlatformDef("byuldaeri", "별대리", 20.0, false, 0xFFE0559B),
        // 개인콜은 플랫폼이 아니라 손님과 직접 한 콜 — 건당 보험료도 고용·산재·세금(3.3%)도 붙지 않는다 (v1.0.33)
        PlatformDef(
            "personal", "개인콜", 0.0, false, 0xFFB7C4D2,
            chargesPerCallInsurance = false, chargesEmploymentTax = false
        )
    )

    val ALL_IDS: List<String> = ALL.map { it.id }

    /**
     * 처음부터 있던 7종 — 설정의 `사용할 프로그램 선택` 에서 기본으로 체크돼 있다.
     * 새로 넣은 7종은 기본 미체크라서, 체크하면 `아이콘` 과 `개인콜` 사이에 바로 나타난다.
     */
    val DEFAULT_ENABLED_IDS: List<String> =
        listOf("kakao", "tmap", "handle", "roji", "colmaner", "icon", "personal")

    fun byId(id: String?): PlatformDef = ALL.firstOrNull { it.id == id } ?: ALL.first()

    fun label(id: String?): String = byId(id).label

    /** 이 플랫폼이 건당 보험료를 떼는지 — 카카오T·티맵·개인콜만 안 떼고, 나머지는 설정값대로 뗀다. */
    fun chargesPerCallInsurance(id: String?): Boolean = byId(id).chargesPerCallInsurance

    /** 이 플랫폼이 고용·산재·세금(3.3%)을 떼는지 — 개인콜만 안 떼고, 나머지는 설정값대로 뗀다(v1.0.33). */
    fun chargesEmploymentTax(id: String?): Boolean = byId(id).chargesEmploymentTax

    /** 저장된 체크 상태 정리 — 모르는 id 는 버리고, 모두 꺼져 있으면 기본 7종으로 되돌린다. */
    fun normalizeEnabled(ids: Set<String>?): Set<String> {
        val known = ids.orEmpty().filter { it in ALL_IDS }.toSet()
        return if (known.isEmpty()) DEFAULT_ENABLED_IDS.toSet() else known
    }

    /** 체크한 프로그램만, 프로그램 순서(`카카오T` → … → `개인콜`) 그대로. */
    fun visible(enabledIds: Set<String>): List<PlatformDef> = ALL.filter { it.id in enabledIds }

    /**
     * 운행 기록 화면의 플랫폼 칩 — 체크한 프로그램만 보여 준다.
     * 수정 중인 기록의 플랫폼이 체크에서 빠져 있으면, 그 기록을 고칠 수 있게 그 한 종만 임시로 함께 보여 준다.
     */
    fun visibleWith(includeId: String?, enabledIds: Set<String>): List<PlatformDef> {
        val base = visible(enabledIds)
        if (includeId == null || base.any { it.id == includeId }) return base
        val extra = ALL.firstOrNull { it.id == includeId } ?: return base
        return ALL.filter { it.id in enabledIds || it.id == extra.id }
    }

    /**
     * 체크 토글 결과. 하나도 안 남게 되는 해제는 허용하지 않고 `null` 을 돌려준다
     * (`사용할 프로그램 선택` 에서 `프로그램은 최소 1개는 남겨 두세요` 로 안내).
     */
    fun toggleEnabled(enabledIds: Set<String>, id: String, checked: Boolean): Set<String>? {
        if (id !in ALL_IDS) return enabledIds
        val next = if (checked) enabledIds + id else enabledIds - id
        return if (next.isEmpty()) null else next
    }
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
