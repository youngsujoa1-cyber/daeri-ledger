package com.youngsu.daeriledger.data

/**
 * 운행 기록 저장 시 보내는 **익명 GPS 콜밭 집계**의 몰아 올리기 제외 판정 — 순수 로직.
 *
 * 사용자가 지적한 조건: `"가끔 한 자리에서 밀린거 싹다 올리는 사람도 있거든"` → `"그런 사람은 빼야지"`.
 * 밀린 기록을 한꺼번에 올리면 한 자리에서 좌표가 여러 번 찍혀 콜밭 통계가 부풀려지므로,
 * 아래 두 가지를 걸러 **배치의 첫 건만** 집계에 보탠다.
 *
 *  1. **시간차**: 운행 시각과 저장 시각이 [MAX_RIDE_AGE_MS]보다 벌어지면(과거 기록 입력) 보내지 않는다.
 *     앞으로 찍힌 시각도 [FUTURE_TOLERANCE_MS]를 넘으면 보내지 않는다.
 *  2. **몰아 올리기**: 같은 기기가 [BURST_WINDOW_MS] 안에 다시 저장하면 두 번째부터 보내지 않는다.
 *     또 [SAME_PLACE_WINDOW_MS] 안에 같은 자리([SAME_PLACE_RADIUS_M]m)에서 다시 저장해도 보내지 않는다
 *     (한 자리에서 콜을 여러 번 잡아도 집계는 한 번).
 *
 * Android 와 무관한 순수 로직이라 `tools/logic-check` 에서 그대로 검사한다.
 */
object RideSpotGate {

    /** 운행 시각과 저장 시각의 허용 차이 — 이보다 오래된 기록은 밀린 입력으로 본다 */
    const val MAX_RIDE_AGE_MS = 20 * 60 * 1000L

    /** 미래로 찍힌 운행 시각 허용치 (시계 오차) */
    const val FUTURE_TOLERANCE_MS = 2 * 60 * 1000L

    /** 같은 기기의 연속 저장 간격 — 이 안의 두 번째부터는 한 배치로 본다 */
    const val BURST_WINDOW_MS = 15 * 60 * 1000L

    /** 같은 자리 반복 저장을 한 배치로 보는 시간 */
    const val SAME_PLACE_WINDOW_MS = 60 * 60 * 1000L

    /** 같은 자리로 보는 반경(m) — 공용 콜밭 묶음 반경과 같다 */
    const val SAME_PLACE_RADIUS_M = 50.0

    /** 서버에 실어 보내는 출발지 이름 최대 길이 */
    const val NAME_MAX = 40

    /** 판정 결과 */
    enum class Verdict {
        /** 집계에 보낸다 */
        REPORT,

        /** 운행 시각이 너무 오래됐다 (밀린 기록) */
        RIDE_TOO_OLD,

        /** 운행 시각이 미래다 */
        RIDE_IN_FUTURE,

        /** 같은 배치의 두 번째 이후 입력 */
        BURST_INPUT,

        /** 한 시간 안에 같은 자리에서 다시 입력 */
        SAME_PLACE_AGAIN
    }

    /** 마지막으로 집계에 보낸 자리 */
    data class LastPing(val at: Long, val lat: Double, val lng: Double)

    data class Decision(val verdict: Verdict) {
        val report: Boolean get() = verdict == Verdict.REPORT
    }

    /**
     * 출발지 칸 → 집계에 쓸 짧은 이름. 여러 칸 공백을 하나로 줄이고 [NAME_MAX]자로 자른다.
     * 출발지를 적지 않았으면 null — 이름 없는 좌표만 올리면 목록에 `기사님 제보 콜밭` 만 늘어난다.
     */
    fun departureName(fromPlace: String): String? {
        val trimmed = fromPlace.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return null
        return if (trimmed.length <= NAME_MAX) trimmed else trimmed.take(NAME_MAX).trim()
    }

    /**
     * @param rideTimeMillis 운행 시각(기록에 적힌 시각)
     * @param nowMillis 저장(입력) 시각
     * @param lastAttemptAt 이 기기가 마지막으로 집계를 시도한 시각 — 0 이면 처음
     * @param lastPing 마지막으로 실제 보낸 자리 — null 이면 없음
     */
    fun decide(
        rideTimeMillis: Long,
        nowMillis: Long,
        lat: Double,
        lng: Double,
        lastAttemptAt: Long,
        lastPing: LastPing?
    ): Decision {
        if (rideTimeMillis <= 0L) return Decision(Verdict.RIDE_TOO_OLD)
        val age = nowMillis - rideTimeMillis
        if (age > MAX_RIDE_AGE_MS) return Decision(Verdict.RIDE_TOO_OLD)
        if (-age > FUTURE_TOLERANCE_MS) return Decision(Verdict.RIDE_IN_FUTURE)
        if (lastAttemptAt in 1..nowMillis && nowMillis - lastAttemptAt <= BURST_WINDOW_MS) {
            return Decision(Verdict.BURST_INPUT)
        }
        if (lastPing != null && lastPing.at in 1..nowMillis &&
            nowMillis - lastPing.at <= SAME_PLACE_WINDOW_MS &&
            SpotAggregator.distanceM(lat, lng, lastPing.lat, lastPing.lng) <= SAME_PLACE_RADIUS_M
        ) {
            return Decision(Verdict.SAME_PLACE_AGAIN)
        }
        return Decision(Verdict.REPORT)
    }
}
