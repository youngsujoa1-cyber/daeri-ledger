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
 *
 * 운행 기록을 저장할 때 만든 **익명 GPS 제보**는 `note` 첫머리에 `#t:22` 같은 시간대 표식이 붙는다
 * ([SpotTimeBands]). 서버 규칙을 바꾸지 않고 시간대를 실어 보내기 위한 방법이다.
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
) {
    /** note 에 실린 시간대(0~23) — 없으면 null */
    val band: Int? get() = SpotTimeBands.bandOf(note)

    /** 운행 기록(GPS)에서 만들어진 익명 제보인지 */
    val rideBand: Boolean get() = band != null
}

/**
 * 앱이 보여 줄 공용 콜밭 한 곳 (서로 다른 기기 3명 이상이 제보한 자리, 또는 운행 GPS 핫스팟).
 *
 *  - [direct] = true : 기사님들이 `+ 콜밭 추가` 로 **직접 제보**해 올라온 자리 → 지도에 **노란 핀**
 *  - [hotspot] = true : `운행 기록` 출발지 GPS 가 **반경 500m** 안에 모인 자리 → 지도에 **빨간 핀**
 */
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
    /** 운영자(관리자)가 바로 공개한 콜밭이면 true — 기준 인원을 기다리지 않는다 */
    val admin: Boolean = false,
    /** 제보 시각 분포에서 찾은 잘 뜨는 시간대 — "22시~01시 활발" (모자라면 "") */
    val peak: String = "",
    /** 제보가 모인 시간대(0~23) — 잘 뜨는 시간대(피크)와 `지금 활발` 판단에 쓴다 */
    val hours: List<Int> = emptyList(),
    /** 운행 기록 GPS로 만든 익명 제보가 모인 자리(🔥 핫스팟)면 true — 빨간 핀 */
    val hotspot: Boolean = false,
    /** `+ 콜밭 추가` 로 직접 제보된 자리면 true — 노란 핀 (v1.0.30) */
    val direct: Boolean = false
)

/** 서로 다른 기기 2명 이상이 '없어진 곳'이라고 신고한 자리 — 반경 [SpotAggregator.GROUP_RADIUS_M]m 안의 콜밭을 숨긴다 */
data class RemovedSpot(
    val name: String,
    val lat: Double,
    val lng: Double,
    /** 서로 다른 기기(=기사님) 수 */
    val reporters: Int,
    /** 마지막 신고 시각(ms) */
    val lastAt: Long,
    /** 운영자(관리자)가 바로 삭제한 자리면 true — 기준 인원을 기다리지 않는다 */
    val admin: Boolean = false
)

/** 공용 제보 집계 결과 — 새로 생긴 콜밭과 없어진 자리 */
data class SpotPublicState(
    val added: List<PublicSpot> = emptyList(),
    val removed: List<RemovedSpot> = emptyList()
) {
    /** 직접 제보로 올라온 공용 콜밭(노란 핀) 수 */
    val directCount: Int get() = added.count { it.direct && !it.hotspot }
    /** 운행 GPS 핫스팟(빨간 핀) 수 */
    val hotspotCount: Int get() = added.count { it.hotspot }
}

/**
 * 제보 묶음 계산 — Android 와 무관한 순수 로직이라 단위 테스트로 검증할 수 있다.
 *
 * 규칙 (v1.0.30 — 사용자가 정한 **3명** 기준, 2026-11-27 정정):
 *  - 같은 자리(반경 [GROUP_RADIUS_M] 50m)에 서로 다른 기기 **3명 이상**이 `콜밭` 제보 → 공용 콜밭(🟡 노란 핀).
 *  - 운행 기록 GPS는 **반경 [HOTSPOT_RADIUS_M] 500m** 로 묶고, 서로 다른 기기 **3명 이상**이면서
 *    **지금 시간대(±1시간)에 입력이 모인** 자리만 🔴 핫스팟 핀으로 보여 준다.
 *  - 그 자리 반경 50m 안에 서로 다른 기기 **2명 이상**이 `삭제` 신고 → 공용 콜밭에서도 숨긴다
 *    (없어진 곳 신고 기준은 v1.0.26 의 2명을 그대로 둔다 — 사용자가 바꿔 달라고 한 것은 등록·핫스팟 기준이다).
 *
 * **두 종류를 따로 묶는다**: 직접 제보(고정 콜밭)와 운행 GPS(지금 활동)는 반경도 기준도 달라서
 * 한 묶음으로 섞지 않는다. 500m 로 넓힌 GPS 묶음에 직접 제보까지 넣으면 노란 핀이 엉뚱한 곳에 찍힌다.
 *
 * **잘 뜨는 시간대**: 묶인 제보들의 시간대 분포에서 3시간 창을 찾아 [PublicSpot.peak] 로 내보낸다.
 * 운행 기록 저장 시 만든 제보는 `note` 의 `#t:` 표식에서, 그 밖의 제보는 제보 시각에서 시간대를 읽는다.
 *
 * **운행 기록 GPS 집계 (v1.0.26 → v1.0.30)**: 기사님이 새 운행을 저장하면 그 순간의 좌표·출발지 요약명·시간대만
 * 익명으로 올린다([RideSpotGate] 가 몰아 올리기를 걸러 낸다). [aggregate] 의 [nowHour] 를 주면
 * **지금 시간대(±1시간)에 입력이 없는 핫스팟은 목록에서 뺀다** — "그 시간에 500m 안에서 출발지 입력이 많은 곳"만
 * 남기기 위해서다. [nowHour] 가 null 이면 시간대와 상관없이 3명 이상 모인 자리를 모두 돌려준다(테스트용).
 *
 * **운영자(관리자) 모드 (v1.0.23)**: 운영자 암호로 만든 기기 해시([adminDeviceHash])가 붙은 제보 한 건은
 * 기준 인원을 기다리지 않고 바로 공개·삭제된다. 서버 규칙(8칸 형식만 검사)을 그대로 쓰기 위해
 * kind 는 일반 제보와 같은 `콜밭`/`삭제` 이고, deviceHash 자리에 운영자 해시가 들어간다.
 */
object SpotAggregator {

    /** 직접 제보(고정 콜밭)를 한 자리로 묶는 반경(m) — v1.0.26 에서 20m → 50m */
    const val GROUP_RADIUS_M = 50.0

    /** 운행 기록 GPS 를 한 자리(핫스팟)로 묶는 반경(m) — v1.0.30 에서 50m → 500m */
    const val HOTSPOT_RADIUS_M = 500.0

    /** 직접 제보 공용 승격 기준 — 서로 다른 기기 3명 이상 (v1.0.30, 2명 → 3명) */
    const val MIN_REPORTERS = 3

    /** 운행 GPS 핫스팟 기준 — 서로 다른 기기 3명 이상 (v1.0.30, 2명 → 3명) */
    const val MIN_HOTSPOT_REPORTERS = 3

    /** 없어진 곳 신고 기준 — 서로 다른 기기 2명 이상 (v1.0.26 그대로) */
    const val MIN_REMOVERS = 2

    /** 핫스팟으로 보여 주려면 지금 시간대(±1시간)에 필요한 최소 입력 수 */
    const val MIN_HOTSPOT_NOW = 1

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
     * 제보 목록 -> 공용 콜밭·핫스팟·없어진 자리.
     * 1) 직접 제보(반경 50m)와 운행 GPS(반경 500m)를 **따로** 묶는다.
     * 2) 직접 제보 묶음은 서로 다른 기기 3명 이상일 때만(🟡 노란 핀), 그 반경 50m 안의 삭제 신고 기기가 2명 미만일 때만 노출한다.
     * 3) 운행 GPS 묶음은 서로 다른 기기 3명 이상 + [nowHour] 가 주어졌으면 지금(±1시간) 입력이 있을 때만(🔴 빨간 핀).
     * 4) 삭제 묶음은 서로 다른 기기 2명 이상이면 [RemovedSpot] 으로 내보낸다.
     * 5) 운영자 해시([adminHash])가 붙은 제보는 기기 수와 상관없이 바로 공개·삭제되고,
     *    같은 자리에서 운영자 쪽이 더 새로 하면 그쪽이 이긴다(오래된 삭제는 새 공개/새 등록에 풀린다).
     *
     * @param nowHour 지금 시(0~23). 주면 그 ±1시간에 입력이 없는 핫스팟은 빼고, null 이면 시간대와 무관하게 모두 낸다.
     */
    fun aggregate(
        reports: List<SpotReport>,
        adminHash: String? = null,
        nowHour: Int? = null
    ): SpotPublicState {
        if (reports.isEmpty()) return SpotPublicState()
        val admin = adminHash?.takeIf { it.isNotBlank() }
        val removals = reports.filter { it.kind == KIND_REMOVED }
        val direct = reports.filter { it.kind != KIND_REMOVED && !it.rideBand }
        val rides = reports.filter { it.kind != KIND_REMOVED && it.rideBand }
        val directGroups = groupReports(direct, GROUP_RADIUS_M)
        val removalGroups = groupReports(removals, GROUP_RADIUS_M)
        val rideGroups = groupReports(rides, HOTSPOT_RADIUS_M)

        /** 반경 50m 안에서 기기 수 [minDevices] 이상인 직접 제보 묶음의 가장 늦은 시각(ms) */
        fun crowdSpotAt(lat: Double, lng: Double, minDevices: Int): Long =
            directGroups.asSequence()
                .filter { distanceM(lat, lng, it.lat, it.lng) <= GROUP_RADIUS_M }
                .filter { it.devices.size >= minDevices }
                .map { it.lastAt }
                .maxOrNull() ?: 0L

        val removed = removalGroups
            .filter { group ->
                when {
                    // 운영자가 더 새로 이 자리를 공개했으면 숨기지 않는다
                    newestAdminAt(direct, group.lat, group.lng, admin) > group.lastAt -> false
                    // 그 뒤에 기사들이 다시 등록했으면 오래된 삭제는 풀린다
                    crowdSpotAt(group.lat, group.lng, MIN_REPORTERS) > group.lastAt -> false
                    // 운영자 삭제 한 건이면 기준 인원을 기다리지 않는다
                    group.hasAdmin(admin) -> true
                    else -> group.devices.size >= MIN_REMOVERS
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

        // ---------------------------------------------------------------- 🟡 직접 제보 (노란 핀)
        val addedDirect = directGroups
            .filter { group ->
                val adminRemovalAt = newestAdminAt(removals, group.lat, group.lng, admin)
                when {
                    // 운영자가 더 새로(또는 같은 때) 이 자리를 지웠으면 등록해도 올리지 않는다
                    adminRemovalAt > 0L && adminRemovalAt >= group.lastAt -> false
                    // 운영자가 이 자리를 공개했으면 기준 인원을 기다리지 않는다
                    group.hasAdmin(admin) -> true
                    // 같은 자리에서 2명 이상이 '없어졌다'고 하면 공용 콜밭으로도 올리지 않는다
                    else -> group.devices.size >= MIN_REPORTERS &&
                        devicesNear(removals, group.lat, group.lng) < MIN_REMOVERS
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
                    note = group.reports
                        .map { SpotTimeBands.decodeNote(it.note).first }
                        .firstOrNull { it.isNotBlank() }
                        .orEmpty(),
                    admin = group.hasAdmin(admin),
                    peak = SpotTimeBands.peakLabel(group.hours),
                    hours = group.hours.distinct().sorted(),
                    hotspot = false,
                    direct = true
                )
            }

        // ---------------------------------------------------------------- 🔴 운행 GPS 핫스팟 (빨간 핀)
        val addedHotspots = rideGroups
            .filter { group ->
                if (group.devices.size < MIN_HOTSPOT_REPORTERS) return@filter false
                // 운영자가 더 새로 이 자리를 지웠으면 핫스팟도 띄우지 않는다
                val adminRemovalAt = newestAdminAt(removals, group.lat, group.lng, admin)
                if (adminRemovalAt > 0L && adminRemovalAt >= group.lastAt) return@filter false
                // 지금 시간대(±1시간)에 입력이 모인 자리만 — "그 시간에 500m 안에서 출발지 입력이 많은 곳"
                if (nowHour != null &&
                    SpotTimeBands.activeCount(group.hours, nowHour) < MIN_HOTSPOT_NOW
                ) return@filter false
                true
            }
            .map { group ->
                PublicSpot(
                    name = group.bestName().ifBlank { "운행 핫스팟" },
                    lat = group.lat,
                    lng = group.lng,
                    address = "",
                    reporters = group.devices.size,
                    lastAt = group.lastAt,
                    note = "",
                    admin = false,
                    peak = SpotTimeBands.peakLabel(group.hours),
                    hours = group.hours.distinct().sorted(),
                    hotspot = true,
                    direct = false
                )
            }

        val added = (addedDirect + addedHotspots)
            .sortedWith(
                compareByDescending<PublicSpot> { it.admin }
                    .thenByDescending { it.reporters }
                    .thenByDescending { it.lastAt }
            )

        return SpotPublicState(added = added, removed = removed)
    }

    /** 이 좌표가 기준 인원 이상이 신고한 '없어진 자리' 반경 50m 안인지 */
    fun isRemoved(lat: Double, lng: Double, removed: List<RemovedSpot>): Boolean {
        if (removed.isEmpty()) return false
        return removed.any { distanceM(lat, lng, it.lat, it.lng) <= GROUP_RADIUS_M }
    }

    /** 반경 50m 안에 운영자가 바로 삭제한 자리가 있는지 */
    fun adminRemovalNear(
        removals: List<SpotReport>,
        lat: Double,
        lng: Double,
        adminHash: String?
    ): Boolean = newestAdminAt(removals, lat, lng, adminHash) > 0L

    /** 반경 50m 안에서 운영자가 제보한 가장 늦은 시각(ms) — 없으면 0 */
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

    /** 좌표 하나에서 50m 안에 제보한 서로 다른 기기 수 */
    fun devicesNear(reports: List<SpotReport>, lat: Double, lng: Double): Int =
        reports.asSequence()
            .filter { it.deviceHash.isNotBlank() }
            .filter { distanceM(lat, lng, it.lat, it.lng) <= GROUP_RADIUS_M }
            .map { it.deviceHash.trim() }
            .toSet()
            .size

    /** 최신 제보부터 [radius] 반경으로 묶는다 (같은 기기가 여러 번 올려도 한 묶음 안에 남는다) */
    private fun groupReports(reports: List<SpotReport>, radius: Double): List<ReportGroup> {
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
                if (distanceM(seed.lat, seed.lng, report.lat, report.lng) <= radius) {
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

        /** 제보마다의 시간대 — 운행 GPS 제보의 표식이 있으면 그것, 없으면 제보 시각의 현지 시 */
        val hours: List<Int> = reports.map {
            SpotTimeBands.bandOf(it.note) ?: SpotTimeBands.hourOf(it.createdAt)
        }

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
