package com.youngsu.daeriledger.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * **위치 갱신 판단 (v1.0.31)** — 안드로이드에 기대지 않는 순수 계산만 모아 둔다.
 *
 * 사용자가 실기기에서 겪은 `"대리장부도 안움직이네"`, `"내위치를 눌러야바껴"`, `"난지금동탄인데 정자로나옴"` 은
 * ① 오래된 마지막 좌표(정자동)를 나이 제한 없이 그대로 믿고 ② 화면이 켜져 있는 동안 새 GPS 를 따라가지
 * 않아서 생겼다. 그래서 여기서 정하는 것은 세 가지다.
 *
 *  - **캐시를 언제 믿나** ([isFresh]) — 기본 [DEFAULT_MAX_CACHE_AGE_MS] 20초. 그보다 오래된 좌표는
 *    '지금 위치'로 쓰지 않고 새로 잰다. (`0` 이면 캐시를 아예 안 믿는다 — `새로고침`)
 *  - **새 측정을 받을까** ([acceptFix]) — 늦게 도착한 옛 측정은 버리고, 같은 순간이면 정확한 쪽을 쓴다.
 *    아주 부정확한(네트워크) 좌표가 정확한(GPS) 좌표를 덮어써서 몇 km 튀는 일도 막는다.
 *  - **언제 화면을 다시 그리나** ([shouldRerender]) — 30m 넘게 움직였거나 30초가 지났을 때만.
 *    매 초 목록 1,500곳을 다시 그리면 화면이 버벅이므로, 거리 정렬은 이만큼 움직였을 때만 새로 한다.
 *
 * `tools/logic-check` 가 이 파일만 따로 컴파일해 검사한다(안드로이드 클래스 없음).
 */
object LocationPolicy {

    /** 마지막 좌표를 '지금 위치'로 믿어도 되는 나이 — 20초 (예전에는 제한 없이 믿었다) */
    const val DEFAULT_MAX_CACHE_AGE_MS = 20_000L

    /** 한 번 측정에 기다리는 최대 시간 */
    const val FIX_TIMEOUT_MS = 8_000L

    /** 이만큼 새 측정이 없으면 강제로 다시 잰다 (실내·차 안에서 제공자 등록만으로는 안 올 때) */
    const val WATCHDOG_SILENCE_MS = 20_000L

    /** 강제 재측정 점검 주기 */
    const val WATCHDOG_INTERVAL_MS = 15_000L

    /** 연속 갱신 최소 간격·최소 이동 — 배터리를 아끼면서 파란 점은 따라오게 */
    const val UPDATE_INTERVAL_MS = 2_000L
    const val UPDATE_DISTANCE_M = 5f

    /** 목록(거리 정렬)을 다시 그릴 만큼 움직였는가 */
    const val RERENDER_DISTANCE_M = 30.0

    /** 움직이지 않아도 이만큼 지나면 거리·오차 표시를 한 번 새로 그린다 */
    const val RERENDER_AGE_MS = 30_000L

    /** 지도 중심이 내 위치를 따라갈 만큼 움직였는가 (손으로 지도를 옮기면 따라가기를 멈춘다) */
    const val MAP_FOLLOW_DISTANCE_M = 120.0

    /** 이보다 오래된 좌표는 `N분 전 위치` 로 알려 준다 */
    const val STALE_LABEL_MS = 90_000L

    /** 새 측정이 이만큼(미터) 더 부정확해도 새 좌표를 쓴다 */
    const val WORSE_ACCURACY_TOLERANCE_M = 50f

    /** 기기 시계가 앞선 좌표를 걸러 내는 여유 */
    const val FUTURE_SLACK_MS = 60_000L

    // 제공자 이름 — 안드로이드 LocationManager 의 문자열과 같다
    const val FUSED = "fused"
    const val GPS = "gps"
    const val NETWORK = "network"

    /** 좌표 나이(ms) — 0 아래로는 내려가지 않는다 */
    fun ageMs(fixTimeMs: Long, nowMs: Long): Long = (nowMs - fixTimeMs).coerceAtLeast(0L)

    /**
     * 캐시 좌표를 지금 위치로 써도 되는가.
     *  - [maxAgeMs] 가 0보다 작으면 예전 방식(나이 무시) — `📍 현위치` 처럼 오래된 좌표를 쓰면 안 되는 곳은 짧게 준다.
     *  - 시각이 없는 좌표(0)와 기기 시계보다 1분 넘게 앞선 좌표는 믿지 않는다.
     */
    fun isFresh(fixTimeMs: Long, nowMs: Long, maxAgeMs: Long): Boolean {
        if (fixTimeMs <= 0L) return false
        if (fixTimeMs > nowMs + FUTURE_SLACK_MS) return false
        if (maxAgeMs < 0L) return true
        return ageMs(fixTimeMs, nowMs) <= maxAgeMs
    }

    /**
     * 함께 물어볼 제공자 — **하나만 고르지 않는다.**
     * GPS 칩이 측정을 안 주는 실내·차 안·터널에서도 네트워크(융합) 좌표가 오도록 켜진 것을 모두 쓴다.
     * 융합(안드로이드 12+)이 있으면 맨 앞에 둔다.
     */
    fun providers(gps: Boolean, network: Boolean, fused: Boolean = false): List<String> {
        val list = ArrayList<String>(3)
        if (fused) list.add(FUSED)
        if (gps) list.add(GPS)
        if (network) list.add(NETWORK)
        return list
    }

    /**
     * 새 측정 [newTimeMs]/[newAccuracyM] 을 기존 [oldTimeMs]/[oldAccuracyM] 자리에 써도 되는가.
     *
     *  1. 기존이 없으면 새 것을 쓴다.
     *  2. 기존보다 1분 넘게 **오래된** 측정(늦게 도착한 옛 좌표)은 버린다.
     *  3. 새 측정이 더 새롭고 정확도가 [WORSE_ACCURACY_TOLERANCE_M] 안에서 비슷하면 쓴다.
     *  4. 정확도가 많이 나빠도(네트워크 좌표) **30초 넘게 새 측정이 없었다면** 쓴다 —
     *     정확한 옛 좌표에 머무는 것보다 지금 자리 근처로 옮기는 편이 낫다.
     */
    fun acceptFix(
        newTimeMs: Long,
        newAccuracyM: Float,
        oldTimeMs: Long,
        oldAccuracyM: Float
    ): Boolean {
        if (newTimeMs <= 0L) return false
        if (oldTimeMs <= 0L) return true
        if (newTimeMs + FUTURE_SLACK_MS < oldTimeMs) return false
        val newAcc = accuracyOf(newAccuracyM)
        val oldAcc = accuracyOf(oldAccuracyM)
        if (newTimeMs >= oldTimeMs && newAcc <= oldAcc + WORSE_ACCURACY_TOLERANCE_M) return true
        if (newTimeMs >= oldTimeMs + RERENDER_AGE_MS && newAcc <= oldAcc * 4f + 200f) return true
        return false
    }

    /** 정확도를 비교 가능한 값으로 — 모르는 값(0 이하·무한대)은 아주 나쁜 값으로 본다 */
    fun accuracyOf(accuracyM: Float): Float {
        if (accuracyM.isNaN() || accuracyM <= 0f) return 999f
        if (accuracyM.isInfinite()) return 999f
        return accuracyM.coerceAtMost(999f)
    }

    /**
     * 목록을 다시 그릴 만큼 움직였는가 — 처음 좌표면 그리고, [RERENDER_DISTANCE_M] 넘게 움직였거나
     * [RERENDER_AGE_MS] 가 지났으면 그린다. (매 측정마다 1,500곳 목록을 다시 그리지 않기 위한 기준)
     */
    fun shouldRerender(
        oldLat: Double,
        oldLng: Double,
        oldTimeMs: Long,
        newLat: Double,
        newLng: Double,
        newTimeMs: Long
    ): Boolean {
        if (oldTimeMs <= 0L) return true
        if (distanceM(oldLat, oldLng, newLat, newLng) >= RERENDER_DISTANCE_M) return true
        return newTimeMs - oldTimeMs >= RERENDER_AGE_MS
    }

    /** 지도 중심을 새 좌표로 옮길 만큼 움직였는가 */
    fun shouldFollowMap(centerLat: Double, centerLng: Double, lat: Double, lng: Double): Boolean =
        distanceM(centerLat, centerLng, lat, lng) >= MAP_FOLLOW_DISTANCE_M

    /**
     * 감시자가 제공자를 다시 등록하고 강제 측정을 해야 하는가 —
     * 등록된 제공자가 하나도 없거나, [WATCHDOG_SILENCE_MS] 넘게 새 측정이 없을 때.
     */
    fun shouldRestartWatchdog(registeredProviders: Int, lastFixAtMs: Long, nowMs: Long): Boolean {
        if (registeredProviders <= 0) return true
        if (lastFixAtMs <= 0L) return true
        return ageMs(lastFixAtMs, nowMs) >= WATCHDOG_SILENCE_MS
    }

    /** 오래된 좌표를 쓰고 있을 때 보여 줄 문구 — 최신이면 null, `"5분 전 위치"` */
    fun staleLabel(ageMs: Long): String? {
        if (ageMs <= STALE_LABEL_MS) return null
        val minutes = (ageMs / 60_000L).coerceAtLeast(2L)
        return when {
            minutes >= 24 * 60L -> "하루 넘게 전 위치"
            minutes >= 60L -> "${minutes / 60}시간 전 위치"
            else -> "${minutes}분 전 위치"
        }
    }

    /** 두 좌표 사이 거리(m) — 하버사인 */
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(min(1.0, sqrt(a)))
    }
}
