package com.youngsu.daeriledger.data

/**
 * `📍 현위치` 가 채울 칸 — 출발지 · 도착지 · 경유 (v1.0.28).
 *
 * 사용자 요청(`"출발지 / 도착지 / 경유 중 마지막으로 클릭해 둔 칸에 적용"`)대로
 * 3개 위치 칸 중 마지막으로 손댄 칸을 기억했다가 그 칸에 넣고, 한 번도 안 골랐으면 출발지에 넣는다.
 *
 * **콜밭 집계**: 현위치 좌표는 출발지에 넣었을 때만 쓴다([keepsSpotFix]).
 * 도착지·경유 좌표까지 콜밭으로 모으면 기사들이 실제로 서는 자리가 아니어도 콜밭이 생긴다.
 */
enum class PlaceTarget {
    FROM,
    TO,
    VIA;

    /** 출발지 칸인가 — 콜밭 집계 좌표를 남겨도 되는 유일한 칸 */
    val keepsSpotFix: Boolean get() = this == FROM

    companion object {
        /**
         * @param focused    지금 포커스가 있는 칸 (3개 밖이면 null)
         * @param remembered 마지막으로 손댄 칸 (없으면 null)
         * @return 현위치를 넣을 칸 — 포커스 → 기억 → 출발지 순서
         */
        fun choose(focused: PlaceTarget?, remembered: PlaceTarget?): PlaceTarget =
            focused ?: remembered ?: FROM
    }
}
