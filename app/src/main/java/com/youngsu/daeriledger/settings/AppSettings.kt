package com.youngsu.daeriledger.settings

import android.content.Context
import android.content.SharedPreferences
import com.youngsu.daeriledger.data.Platforms
import com.youngsu.daeriledger.data.ServiceType

/**
 * 앱 설정. 플랫폼별 수수료율·건당 보험료·영업일 기준시각·목표액·테마를 담는다.
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("daeri_settings", Context.MODE_PRIVATE)

    companion object {
        const val THEME_DARK = 0
        const val THEME_LIGHT = 1
        const val THEME_SYSTEM = 2

        private const val KEY_CUTOFF = "cutoff_hour"
        private const val KEY_GOAL = "daily_goal"
        private const val KEY_INS_DAERI = "insurance_daeri"
        private const val KEY_INS_TAKSONG = "insurance_taksong"
        private const val KEY_EMP_PCT = "employment_insurance_pct"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LAST_PLATFORM = "last_platform"
        private const val KEY_LAST_SERVICE = "last_service"
        private const val KEY_TIP_PCT = "fee_pct_tip"
        private const val KEY_HIDDEN_SPOTS = "hidden_spot_ids"
        private const val KEY_ADMIN_MODE = "admin_mode"
        private const val KEY_SHARE_RIDE_STATS = "share_ride_stats"
        private const val KEY_ENABLED_PLATFORMS = "enabled_platforms"
        private const val KEY_PING_ATTEMPT = "spot_ping_attempt_at"
        private const val KEY_PING_AT = "spot_ping_at"
        private const val KEY_PING_LAT = "spot_ping_lat"
        private const val KEY_PING_LNG = "spot_ping_lng"

        @Volatile private var instance: AppSettings? = null
        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }

    /** 영업일이 바뀌는 시각(0~23). 기본 낮 12시 → 새벽 2시 콜도 전날 영업일로 잡힌다. */
    var cutoffHour: Int
        get() = prefs.getInt(KEY_CUTOFF, 12).coerceIn(0, 23)
        set(value) = prefs.edit().putInt(KEY_CUTOFF, value.coerceIn(0, 23)).apply()

    /** 하루 목표 순수익 */
    var dailyGoal: Int
        get() = prefs.getInt(KEY_GOAL, 150_000)
        set(value) = prefs.edit().putInt(KEY_GOAL, value.coerceAtLeast(0)).apply()

    /** 대리 건당 보험료 기본값 */
    var insuranceDaeri: Int
        get() = prefs.getInt(KEY_INS_DAERI, 1_500)
        set(value) = prefs.edit().putInt(KEY_INS_DAERI, value.coerceAtLeast(0)).apply()

    /** 탁송 건당 보험료 기본값 */
    var insuranceTaksong: Int
        get() = prefs.getInt(KEY_INS_TAKSONG, 2_500)
        set(value) = prefs.edit().putInt(KEY_INS_TAKSONG, value.coerceAtLeast(0)).apply()

    /** 고용·산재보험료율(%) — 매출 기준 */
    var employmentInsurancePercent: Double
        get() = prefs.getFloat(KEY_EMP_PCT, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_EMP_PCT, value.coerceIn(0.0, 20.0).toFloat()).apply()

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, THEME_DARK)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    /** 마지막으로 쓴 플랫폼 — 입력 화면에서 기본 선택 */
    var lastPlatformId: String
        get() = prefs.getString(KEY_LAST_PLATFORM, "kakao") ?: "kakao"
        set(value) = prefs.edit().putString(KEY_LAST_PLATFORM, value).apply()

    var lastServiceType: String
        get() = prefs.getString(KEY_LAST_SERVICE, ServiceType.DAERI.id) ?: ServiceType.DAERI.id
        set(value) = prefs.edit().putString(KEY_LAST_SERVICE, value).apply()

    /**
     * 팁·경유비 수수료율(%). 플랫폼과 상관없이 받은 팁을 이 율로 역산한다.
     * 기본 20% — 0% 로 두면 팁에는 수수료를 떼지 않는다.
     */
    var tipFeePercent: Double
        get() = prefs.getFloat(KEY_TIP_PCT, 20f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_TIP_PCT, value.coerceIn(0.0, 60.0).toFloat()).apply()

    fun platformFeePercent(platformId: String): Double {
        val def = Platforms.byId(platformId)
        return prefs.getFloat("fee_pct_$platformId", def.defaultFeePercent.toFloat()).toDouble()
    }

    fun setPlatformFeePercent(platformId: String, percent: Double) =
        prefs.edit().putFloat("fee_pct_$platformId", percent.coerceIn(0.0, 60.0).toFloat()).apply()

    /** true = 이 플랫폼은 이미 수수료가 차감된 금액이 보이는 곳 → 입력값이 실수령액, 총매출은 역산 */
    fun platformNetShown(platformId: String): Boolean {
        val def = Platforms.byId(platformId)
        return prefs.getBoolean("fee_net_$platformId", def.defaultNetShown)
    }

    fun setPlatformNetShown(platformId: String, netShown: Boolean) =
        prefs.edit().putBoolean("fee_net_$platformId", netShown).apply()

    fun insuranceFor(service: ServiceType): Int =
        if (service == ServiceType.DAERI) insuranceDaeri else insuranceTaksong

    /**
     * `설정 → 사용할 프로그램 선택` 에서 체크한 프로그램 (v1.0.32).
     *
     * 체크한 프로그램만 설정의 수수료 목록과 운행 기록 화면의 플랫폼 칩에 보인다.
     * 저장값이 없는 기기는 처음부터 있던 7종(`카카오T`·`티맵`·`핸들`·`로지`·`콜마너`·`아이콘`·`개인콜`)이
     * 체크된 상태로 시작하고, 새로 넣은 7종(`대구사랑`~`별대리`)은 미체크다.
     * 체크를 꺼도 과거 운행 기록·통계·달력 집계는 그대로 남는다 — 보이는 곳만 달라진다.
     */
    var enabledPlatformIds: Set<String>
        get() = Platforms.normalizeEnabled(prefs.getStringSet(KEY_ENABLED_PLATFORMS, null))
        set(value) = prefs.edit()
            .putStringSet(
                KEY_ENABLED_PLATFORMS,
                HashSet(Platforms.normalizeEnabled(value))
            )
            .apply()

    /** 이 프로그램을 체크했는지 — 운행 기록 칩·설정 목록이 이 값으로 걸러진다 */
    fun isPlatformEnabled(platformId: String): Boolean = platformId in enabledPlatformIds

    /**
     * 이 플랫폼에 적용하는 수수료율(%).
     * 총매출 모드에서는 총매출 × 율, 차감 후 금액 모드에서는 총매출 = 입력값 ÷ (1 − 율) 로 쓴다.
     */
    fun effectiveFeePercent(platformId: String): Double = platformFeePercent(platformId)

    /** 1.0.1 총매출 역산 도입 때 기존 기록을 한 번만 고쳤는지 */
    var netGrossMigrationDone: Boolean
        get() = prefs.getBoolean("migrated_net_gross_v2", false)
        set(value) = prefs.edit().putBoolean("migrated_net_gross_v2", value).apply()

    /**
     * 콜밭 화면에서 숨긴 콜밭 id — 현장에 가 보니 없어진 곳을 길게 눌러 숨기면 여기에 남는다.
     * 되돌리기로 언제든 싹 비울 수 있다.
     */
    var hiddenSpotIds: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_SPOTS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN_SPOTS, HashSet(value)).apply()

    /**
     * 운영자(관리자) 모드 — 설정 화면 맨 아래 버전을 5번 누르고 암호를 맞히면 켜진다(v1.0.23).
     *
     * 켜져 있으면 `+ 콜밭 추가` 로 올린 콜밭이 3명 대기 없이 모든 기사님 앱에 바로 공개되고,
     * 콜밭 카드를 길게 눌러 `전체에서 바로 삭제` 를 할 수 있다. 다시 5번 누르거나 설정의
     * `운영자 모드` 줄에서 끕니다. 암호(`BuildConfig.ADMIN_PASS`)는 local.properties 에만 있다.
     */
    var isAdminMode: Boolean
        get() = prefs.getBoolean(KEY_ADMIN_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_ADMIN_MODE, value).apply()

    /**
     * **운행 기록 저장 시 현재 위치를 익명 콜밭 통계에 보태는 토글 (v1.0.26)** — 기본 켜짐.
     *
     * 켜져 있으면 새 운행을 저장할 때 현재 GPS 좌표·출발지 요약명·운행 시간대만 공용 집계로 보낸다.
     * 도착지·요금·플랫폼·메모는 보내지 않는다. 위치 권한이 없으면 아무것도 보내지 않는다.
     */
    var shareRideStats: Boolean
        get() = prefs.getBoolean(KEY_SHARE_RIDE_STATS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHARE_RIDE_STATS, value).apply()

    /** 마지막으로 익명 GPS 집계를 **시도**한 시각 — 몰아 올리기(연속 입력) 판정에 쓴다 */
    var spotPingLastAttemptAt: Long
        get() = prefs.getLong(KEY_PING_ATTEMPT, 0L)
        set(value) = prefs.edit().putLong(KEY_PING_ATTEMPT, value).apply()

    /** 마지막으로 익명 GPS 집계를 **보낸** 자리 — 같은 자리 반복 입력 판정에 쓴다 */
    var spotPingLastPing: com.youngsu.daeriledger.data.RideSpotGate.LastPing?
        get() {
            val at = prefs.getLong(KEY_PING_AT, 0L)
            if (at <= 0L) return null
            return com.youngsu.daeriledger.data.RideSpotGate.LastPing(
                at = at,
                lat = prefs.getFloat(KEY_PING_LAT, 0f).toDouble(),
                lng = prefs.getFloat(KEY_PING_LNG, 0f).toDouble()
            )
        }
        set(value) = prefs.edit().apply {
            putLong(KEY_PING_AT, value?.at ?: 0L)
            putFloat(KEY_PING_LAT, (value?.lat ?: 0.0).toFloat())
            putFloat(KEY_PING_LNG, (value?.lng ?: 0.0).toFloat())
        }.apply()
}
