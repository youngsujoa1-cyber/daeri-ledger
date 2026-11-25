package com.youngsu.daeriledger.data

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
    val note: String = "",
    /** 운영자(관리자)가 바로 공개한 콜밭이면 true — 5명을 기다리지 않는다 */
    val admin: Boolean = false
)

/** 서로 다른 기기 5명 이상이 '없어진 곳'이라고 신고한 자리 — 반경 20m 안의 콜밭을 숨긴다 */
data class RemovedSpot(
    val name: String,
    val lat: Double,
    val lng: Double,
    /** 서로 다른 기기(=기사님) 수 */
    val reporters: Int,
    /** 마지막 신고 시각(ms) */
    val lastAt: Long,
    /** 운영자(관리자)가 바로 삭제한 자리면 true — 5명을 기다리지 않는다 */
    val admin: Boolean = false
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
 *  - 같은 자리(반경 20m)에 서로 다른 기기 **5명 이상**이 `콜밭` 제보 → 공용 콜밭으로 노출.
 *  - 그 자리 반경 20m 안에 서로 다른 기기 **5명 이상**이 `삭제` 신고 → 공용 콜밭에서도 숨긴다.
 *  - `삭제` 신고가 5명 이상 모인 자리는 [RemovedSpot] 으로 돌려주고, 화면은 그 반경 20m 안의
 *    기본·기사 추천·내 콜밭까지 모두 숨긴다.
 *
 * **거리 기준 (v1.0.24)**: 같은 자리로 보는 반경은 **20m**다(사용자 요청으로 150m → 20m).
 *  등록 묶기·삭제 묶기·없어진 자리 숨김·운영자 판정이 모두 [GROUP_RADIUS_M] 하나를 쓴다.
 *
 * **운영자(관리자) 모드 (v1.0.23)**: 운영자 암호로 만든 기기 해시([adminDeviceHash])가 붙은 제보 한 건은
 * 5명을 기다리지 않고 바로 공개·삭제된다. 서버 규칙(8칸 형식만 검사)을 그대로 쓰기 위해
 * kind 는 일반 제보와 같은 `콜밭`/`삭제` 이고, deviceHash 자리에 운영자 해시가 들어간다.
 */
object SpotAggregator {

    /** 같은 콜밭으로 묶는 반경(m) — v1.0.24 에서 150m → 20m */
    const val GROUP_RADIUS_M = 20.0
    /** 공용 등록·공용 삭제 모두 서로 다른 기기 5명 이상 */
    const val MIN_REPORTERS = 5

    /** 콜밭 등록 제보 */
    const val KIND_SPOT = "콜밭"
    /** 없어진 곳(삭제) 신고 */
    const val KIND_REMOVED = "삭제"

    /**
     * 운영자 제보임을 알아보는 기기 해시 — `local.properties` 의 `ADMIN_PASS` 로만 만들어진다.
     * 일반 기기 해시와 **같은 형식(소문자 16진수 32자)** 으로 만들어, 서버 규칙(8~64자 문자열)을
     * 그대로 통과하게 한다. 암호가 다르면 값이 달라 일반 기기 해시와 섞이지 않는다.
     */
    fun adminDeviceHash(pass: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("daeri-admin|$pass").toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder(32)
        for (b in digest) sb.append(String.format("%02x", b))
        return sb.substring(0, 32)
    }

    /** 이 제보가 운영자 해시를 단 운영자 제보인지 */
    fun isAdminReport(report: SpotReport, adminHash: String?): Boolean =
        !adminHash.isNullOrBlank() && report.deviceHash.trim() == adminHash

    /**
     * 제보 목록 -> 공용 콜밭·없어진 자리.
     * 1) 최신 제보부터 훑으며 아직 안 묶인 제보를 씨앗으로 삼고 20m 안의 같은 종류 제보를 한 묶음으로 모은다.
     * 2) 콜밭 묶음은 서로 다른 기기 5명 이상일 때만, 그 반경 20m 안의 삭제 신고 기기가 5명 미만일 때만 노출한다.
     * 3) 삭제 묶음은 서로 다른 기기 5명 이상이면 [RemovedSpot] 으로 내보낸다.
     * 4) 운영자 해시([adminHash])가 붙은 제보는 기기 수와 상관없이 바로 공개·삭제되고,
     *    같은 자리에서 운영자 쪽이 더 새로 하면 그쪽이 이긴다(오래된 삭제는 새 공개/새 5명 등록에 풀린다).
     */
    fun aggregate(reports: List<SpotReport>, adminHash: String? = null): SpotPublicState {
        if (reports.isEmpty()) return SpotPublicState()
        val admin = adminHash?.takeIf { it.isNotBlank() }
        val removals = reports.filter { it.kind == KIND_REMOVED }
        val spots = reports.filter { it.kind != KIND_REMOVED }
        val spotGroups = groupReports(spots)
        val removalGroups = groupReports(removals)

        /** 반경 20m 안에서 기기 수 [minDevices] 이상인 콜밭 묶음의 가장 늦은 시각(ms) */
        fun crowdSpotAt(lat: Double, lng: Double, minDevices: Int): Long =
            spotGroups.asSequence()
                .filter { distanceM(lat, lng, it.lat, it.lng) <= GROUP_RADIUS_M }
                .filter { it.devices.size >= minDevices }
                .map { it.lastAt }
                .maxOrNull() ?: 0L

        val removed = removalGroups
            .filter { group ->
                when {
                    // 운영자가 더 새로 이 자리를 공개했으면 숨기지 않는다
                    newestAdminAt(spots, group.lat, group.lng, admin) > group.lastAt -> false
                    // 그 뒤에 기사 5명이 다시 등록했으면 오래된 삭제는 풀린다
                    crowdSpotAt(group.lat, group.lng, MIN_REPORTERS) > group.lastAt -> false
                    // 운영자 삭제 한 건이면 5명을 기다리지 않는다
                    group.hasAdmin(admin) -> true
                    else -> group.devices.size >= MIN_REPORTERS
                }
            }
            .map { group ->
                RemovedSpot(
                    name = group.bestName(),
                    lat = group.lat,
                    lng = group.lng,
                    reporters = group.devices.size,
                    lastAt = group.lastAt,
                    admin = group.hasAdmin(admin)
                )
            }
            .sortedWith(compareByDescending<RemovedSpot> { it.admin }.thenByDescending { it.reporters }.thenByDescending { it.lastAt })

        val added = spotGroups
            .filter { group ->
                val adminRemovalAt = newestAdminAt(removals, group.lat, group.lng, admin)
                when {
                    // 운영자가 더 새로(또는 같은 때) 이 자리를 지웠으면 5명이 등록해도 올리지 않는다
                    adminRemovalAt > 0L && adminRemovalAt >= group.lastAt -> false
                    // 운영자가 이 자리를 공개했으면 5명을 기다리지 않는다
                    group.hasAdmin(admin) -> true
                    // 같은 자리에서 5명 이상이 '없어졌다'고 하면 공용 콜밭으로도 올리지 않는다
                    else -> group.devices.size >= MIN_REPORTERS &&
                        devicesNear(removals, group.lat, group.lng) < MIN_REPORTERS
                }
            }
            .map { group ->
                PublicSpot(
                    name = group.bestName(),
                    lat = group.lat,
                    lng = group.lng,
                    address = group.reports.firstOrNull { it.address.isNotBlank() }?.address.orEmpty(),
                    reporters = group.devices.size,
                    lastAt = group.lastAt,
                    note = group.reports.firstOrNull { it.note.isNotBlank() }?.note.orEmpty(),
                    admin = group.hasAdmin(admin)
                )
            }
            .sortedWith(compareByDescending<PublicSpot> { it.admin }.thenByDescending { it.reporters }.thenByDescending { it.lastAt })

        return SpotPublicState(added = added, removed = removed)
    }

    /** 이 좌표가 5명 이상이 신고한 '없어진 자리' 반경 20m 안인지 */
    fun isRemoved(lat: Double, lng: Double, removed: List<RemovedSpot>): Boolean {
        if (removed.isEmpty()) return false
        return removed.any { distanceM(lat, lng, it.lat, it.lng) <= GROUP_RADIUS_M }
    }

    /** 반경 20m 안에 운영자가 바로 삭제한 자리가 있는지 */
    fun adminRemovalNear(
        removals: List<SpotReport>,
        lat: Double,
        lng: Double,
        adminHash: String?
    ): Boolean = newestAdminAt(removals, lat, lng, adminHash) > 0L

    /** 반경 20m 안에서 운영자가 제보한 가장 늦은 시각(ms) — 없으면 0 */
    fun newestAdminAt(
        reports: List<SpotReport>,
        lat: Double,
        lng: Double,
        adminHash: String?
    ): Long {
        if (adminHash.isNullOrBlank()) return 0L
        return reports.asSequence()
            .filter { isAdminReport(it, adminHash) }
            .filter { distanceM(lat, lng, it.lat, it.lng) <= GROUP_RADIUS_M }
            .map { it.createdAt }
            .maxOrNull() ?: 0L
    }

    /** 좌표 하나에서 20m 안에 제보한 서로 다른 기기 수 */
    fun devicesNear(reports: List<SpotReport>, lat: Double, lng: Double): Int =
        reports.asSequence()
            .filter { it.deviceHash.isNotBlank() }
            .filter { distanceM(lat, lng, it.lat, it.lng) <= GROUP_RADIUS_M }
            .map { it.deviceHash.trim() }
            .toSet()
            .size

    /** 최신 제보부터 20m 반경으로 묶는다 (같은 기기가 여러 번 올려도 한 묶음 안에 남는다) */
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

        /** 이 묶음에 운영자 해시가 붙은 제보가 있는지 */
        fun hasAdmin(adminHash: String?): Boolean =
            !adminHash.isNullOrBlank() && reports.any { it.deviceHash.trim() == adminHash }

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
