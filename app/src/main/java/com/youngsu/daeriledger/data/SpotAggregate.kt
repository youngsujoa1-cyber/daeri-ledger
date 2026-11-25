package com.youngsu.daeriledger.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 서버에서 받은 제보 1건 (집계 입력).
 *
 * 등록(`kind = "콜밭"`)과 없어진 곳 신고(`kind = "삭제"`)가 **같은 8칸 스키마**로 온다.
 * 서버 보안 규칙도 두 종류를 구분하지 않으므로 앱이 `kind` 로 갈라 쓴다.
 */
data class SpotReport(
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String,
    val note: String,
    val deviceHash: String,
    val createdAt: Long,
    val kind: String = SpotAggregator.KIND_SPOT
)

/** 앱이 보여 줄 공용 콜밭 한 곳 (서로 다른 기기 5명 이상이 제보) */
data class PublicSpot(
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String,
    /** 서로 다른 기기(=기사님) 수 */
    val reporters: Int,
    /** 마지막 제보 시각(ms) */
    val lastAt: Long,
    val note: String = ""
)

/** 서로 다른 기기 5명 이상이 '없어진 곳'이라고 신고한 자리 — 반경 150m 안의 콜밭을 숨긴다 */
data class RemovedSpot(
    val name: String,
    val lat: Double,
    val lng: Double,
    /** 서로 다른 기기(=기사님) 수 */
    val reporters: Int,
    /** 마지막 신고 시각(ms) */
    val lastAt: Long
)

/** 공용 제보 집계 결과 — 새로 생긴 콜밭과 없어진 자리 */
data class SpotPublicState(
    val added: List<PublicSpot> = emptyList(),
    val removed: List<RemovedSpot> = emptyList()
)

/**
 * 제보 묶음 계산 — Android 와 무관한 순수 로직이라 단위 테스트로 검증할 수 있다.
 *
 * 규칙 (사용자가 정한 **5명 기준**):
 *  - 같은 자리(반경 150m)에 서로 다른 기기 **5명 이상**이 `콜밭` 제보 → 공용 콜밭으로 노출.
 *  - 그 자리 반경 150m 안에 서로 다른 기기 **5명 이상**이 `삭제` 신고 → 공용 콜밭에서도 숨긴다.
 *  - `삭제` 신고가 5명 이상 모인 자리는 [RemovedSpot] 으로 돌려주고, 화면은 그 반경 150m 안의
 *    기본·기사 추천·내 콜밭까지 모두 숨긴다.
 */
object SpotAggregator {

    /** 같은 콜밭으로 묶는 반경(m) */
    const val GROUP_RADIUS_M = 150.0
    /** 공용 등록·공용 삭제 모두 서로 다른 기기 5명 이상 */
    const val MIN_REPORTERS = 5

    /** 콜밭 등록 제보 */
    const val KIND_SPOT = "콜밭"
    /** 없어진 곳(삭제) 신고 */
    const val KIND_REMOVED = "삭제"

    /**
     * 제보 목록 -> 공용 콜밭·없어진 자리.
     * 1) 최신 제보부터 훑으며 아직 안 묶인 제보를 씨앗으로 삼고 150m 안의 같은 종류 제보를 한 묶음으로 모은다.
     * 2) 콜밭 묶음은 서로 다른 기기 5명 이상일 때만, 그 반경 150m 안의 삭제 신고 기기가 5명 미만일 때만 노출한다.
     * 3) 삭제 묶음은 서로 다른 기기 5명 이상이면 [RemovedSpot] 으로 내보낸다.
     */
    fun aggregate(reports: List<SpotReport>): SpotPublicState {
        if (reports.isEmpty()) return SpotPublicState()
        val removals = reports.filter { it.kind == KIND_REMOVED }
        val spots = reports.filter { it.kind != KIND_REMOVED }

        val removed = groupReports(removals)
            .filter { it.devices.size >= MIN_REPORTERS }
            .map { group ->
                RemovedSpot(
                    name = group.bestName(),
                    lat = group.lat,
                    lng = group.lng,
                    reporters = group.devices.size,
                    lastAt = group.lastAt
                )
            }
            .sortedWith(compareByDescending<RemovedSpot> { it.reporters }.thenByDescending { it.lastAt })

        val added = groupReports(spots)
            .filter { it.devices.size >= MIN_REPORTERS }
            // 같은 자리에서 5명 이상이 '없어졌다'고 하면 공용 콜밭으로도 올리지 않는다
            .filter { devicesNear(removals, it.lat, it.lng) < MIN_REPORTERS }
            .map { group ->
                PublicSpot(
                    name = group.bestName(),
                    lat = group.lat,
                    lng = group.lng,
                    address = group.reports.firstOrNull { it.address.isNotBlank() }?.address.orEmpty(),
                    reporters = group.devices.size,
                    lastAt = group.lastAt,
                    note = group.reports.firstOrNull { it.note.isNotBlank() }?.note.orEmpty()
                )
            }
            .sortedWith(compareByDescending<PublicSpot> { it.reporters }.thenByDescending { it.lastAt })

        return SpotPublicState(added = added, removed = removed)
    }

    /** 이 좌표가 5명 이상이 신고한 '없어진 자리' 반경 150m 안인지 */
    fun isRemoved(lat: Double, lng: Double, removed: List<RemovedSpot>): Boolean {
        if (removed.isEmpty()) return false
        return removed.any { distanceM(lat, lng, it.lat, it.lng) <= GROUP_RADIUS_M }
    }

    /** 좌표 하나에서 150m 안에 제보한 서로 다른 기기 수 */
    fun devicesNear(reports: List<SpotReport>, lat: Double, lng: Double): Int =
        reports.asSequence()
            .filter { it.deviceHash.isNotBlank() }
            .filter { distanceM(lat, lng, it.lat, it.lng) <= GROUP_RADIUS_M }
            .map { it.deviceHash.trim() }
            .toSet()
            .size

    /** 최신 제보부터 150m 반경으로 묶는다 (같은 기기가 여러 번 올려도 한 묶음 안에 남는다) */
    private fun groupReports(reports: List<SpotReport>): List<ReportGroup> {
        if (reports.isEmpty()) return emptyList()
        val sorted = reports.sortedByDescending { it.createdAt }
        val used = BooleanArray(sorted.size)
        val groups = ArrayList<ReportGroup>()
        for (i in sorted.indices) {
            if (used[i]) continue
            val seed = sorted[i]
            val members = ArrayList<SpotReport>()
            for (j in i until sorted.size) {
                if (used[j]) continue
                val report = sorted[j]
                if (distanceM(seed.lat, seed.lng, report.lat, report.lng) <= GROUP_RADIUS_M) {
                    used[j] = true
                    members.add(report)
                }
            }
            groups.add(ReportGroup(members))
        }
        return groups
    }

    private class ReportGroup(val reports: List<SpotReport>) {
        val lat: Double = reports.map { it.lat }.average()
        val lng: Double = reports.map { it.lng }.average()
        val devices: Set<String> =
            reports.map { it.deviceHash.trim() }.filter { it.isNotBlank() }.toSet()
        val lastAt: Long = reports.maxOf { it.createdAt }

        /** 이름은 가장 많이 나온 것, 같으면 긴 것 */
        fun bestName(): String {
            val best = reports
                .map { it.name.trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }
                        .thenByDescending { it.key.length }
                )
                .firstOrNull()
            return best?.key ?: reports.first().name.trim()
        }
    }

    /** 두 좌표 사이 거리(m) — Hotspots.distanceKm 과 같은 하버사인 */
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
