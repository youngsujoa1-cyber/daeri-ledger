package com.youngsu.daeriledger.data

import android.content.Context
import com.youngsu.daeriledger.location.LocationHelper
import com.youngsu.daeriledger.settings.AppSettings

/**
 * **운행 기록 저장 시 현재 GPS 좌표를 익명 콜밭 집계에 보태는 기능 (v1.0.26)** — Android 연결부.
 *
 * 보내는 것: 익명 기기 해시 + 현재 좌표 + 출발지 요약명 + 운행 시간대 — **이 네 가지뿐**이다.
 * 도착지·요금·플랫폼·결제 방식·메모·특이사항은 절대 보내지 않는다.
 *
 * 보내지 않는 경우:
 *  - 설정에서 `운행 기록 위치 통계` 를 꺼 둔 경우 (기본 켜짐)
 *  - 위치 권한이 없는 경우 (권한을 새로 요구하지도 않는다)
 *  - 기록 수정(`edit`) 화면에서 고친 경우 — [onRideSaved] 는 새 기록 저장에서만 부른다
 *  - 출발지를 적지 않은 기록
 *  - [RideSpotGate] 가 몰아 올리기(밀린 기록·연속 입력)로 판정한 경우
 *
 * `📍 현위치` 로 이미 좌표를 잡아 두었으면([Fix]) 그 자리를 그대로 쓴다 — 화면에 채운 출발지와
 * 집계에 보태는 자리가 같아진다. 30분 넘게 지난 좌표는 [RideSpotGate.fixUsable] 이 걸러 내고 새로 잡는다.
 *
 * 실패해도 조용히 넘어간다 — 장부 저장은 이미 끝났고, 통계는 덤이다.
 */
object RideSpotPing {

    /** 서버에 실어 보내는 출발지 이름 최대 길이 */
    const val NAME_MAX = RideSpotGate.NAME_MAX

    /** 운행 기록 화면에서 `📍 현위치` 로 잡아 둔 좌표 */
    data class Fix(val lat: Double, val lng: Double, val atMillis: Long)

    /**
     * 새 운행을 저장한 직후 호출한다 (수정 저장에서는 부르지 않는다).
     * [fix] 가 있으면 그 좌표를 그대로 쓰고, 없으면 마지막으로 알려진 좌표를 바로 쓰거나
     * 한 번만 갱신을 기다린다(최대 6초, 화면은 막지 않는다).
     */
    fun onRideSaved(
        context: Context,
        settings: AppSettings,
        rideTimeMillis: Long,
        fromPlace: String,
        fix: Fix? = null
    ) {
        if (!settings.shareRideStats) return
        if (!SpotCloud.isAvailable) return
        if (!LocationHelper.hasPermission(context)) return
        val name = departureName(fromPlace) ?: return
        val app = context.applicationContext

        val now = System.currentTimeMillis()
        if (fix != null && RideSpotGate.fixUsable(fix.atMillis, now)) {
            report(app, settings, rideTimeMillis, name, fix.lat, fix.lng, now)
            return
        }

        LocationHelper.request(app) { location ->
            if (location == null) return@request
            report(
                app, settings, rideTimeMillis, name,
                location.latitude, location.longitude, System.currentTimeMillis()
            )
        }
    }

    /** 몰아 올리기 제외 판정([RideSpotGate])을 통과한 좌표만 서버로 보낸다 */
    private fun report(
        context: Context,
        settings: AppSettings,
        rideTimeMillis: Long,
        name: String,
        lat: Double,
        lng: Double,
        now: Long
    ) {
        val decision = RideSpotGate.decide(
            rideTimeMillis = rideTimeMillis,
            nowMillis = now,
            lat = lat,
            lng = lng,
            lastAttemptAt = settings.spotPingLastAttemptAt,
            lastPing = settings.spotPingLastPing
        )
        // 판정과 상관없이 시도 시각을 남긴다 — 밀린 기록을 여러 건 올려도 첫 건 뒤로는 조용히 걸러진다
        settings.spotPingLastAttemptAt = now
        if (!decision.report) return
        settings.spotPingLastPing = RideSpotGate.LastPing(now, lat, lng)
        SpotCloud.submitRidePing(
            context = context,
            fromPlace = name,
            lat = lat,
            lng = lng,
            rideTimeMillis = rideTimeMillis
        ) { _, _ -> }
    }

    /** 출발지 칸 → 집계에 쓸 짧은 이름 ([RideSpotGate.departureName] 와 같은 규칙) */
    fun departureName(fromPlace: String): String? = RideSpotGate.departureName(fromPlace)
}
