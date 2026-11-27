package com.youngsu.daeriledger.data

/**
 * Geocoder 가 준 행정구역 조각 → 대리운전 기사가 바로 알아보는 **짧은 동네 이름** (v1.0.27).
 *
 * 운행 기록 화면의 `📍 현위치` 버튼이 잡은 좌표를 출발지 칸에 넣을 때 쓴다.
 * 도로명(`정자일로`)·건물명보다 **동/읍/면**을 먼저 고르고, 없으면 구·시를 쓴다.
 *
 *  - `성남시 분당구 정자동` → `정자동`
 *  - `성남시 분당구`          → `분당구`
 *  - `서울특별시`             → `서울`
 *
 * Android 와 무관한 순수 로직이라 `tools/logic-check` 에서 그대로 검사한다.
 */
object PlaceNamer {

    /** 출발지 칸에 넣는 이름 최대 길이 — 입력칸이 좁아 길면 줄인다 */
    const val MAX_LEN = 20

    /** 동네로 보는 접미사 */
    private val DONG_SUFFIXES = listOf("동", "읍", "면")

    /** 시·군·구·도로 끝나면 행정구역 이름으로 본다 */
    private val DISTRICT_SUFFIXES = listOf("시", "군", "구", "도")

    /** 시·도 이름에서 떼어 내는 접미사 — `서울특별시` → `서울` (긴 것부터) */
    private val SIDO_SUFFIXES = listOf("특별자치시", "특별자치도", "특별시", "광역시", "특례시")

    private val SPACES = Regex("\\s+")

    /**
     * @param locality     시·군 (예: `성남시`)
     * @param subLocality  구 또는 동 (기기·지역에 따라 `분당구` 도 `정자동` 도 들어온다)
     * @param subAdminArea 구·읍·면 같은 보조 행정구역 (예: `분당구`)
     * @param thoroughfare 도로명 (예: `정자일로`)
     * @param addressLines Geocoder 주소 줄 — 보통 `대한민국 경기도 성남시 분당구 정자동 7` 처럼 온다
     * @param adminArea    시·도 (예: `경기도`) — 마지막 대비책
     * @return 출발지 칸에 넣을 이름, 아무것도 못 찾으면 null
     */
    fun shortName(
        locality: String? = null,
        subLocality: String? = null,
        subAdminArea: String? = null,
        thoroughfare: String? = null,
        addressLines: List<String> = emptyList(),
        adminArea: String? = null
    ): String? {
        // 1) 동/읍/면 우선 — 주소 줄은 뒤쪽(가장 좁은 행정구역) 토큰부터 본다
        val lineTokens = addressLines.asReversed().flatMap { tokens(it).asReversed() }
        val candidates = listOfNotNull(subLocality, subAdminArea, thoroughfare) + lineTokens
        for (raw in candidates) {
            dongName(raw)?.let { return cut(it) }
        }
        // 2) 동/읍/면이 없으면 구·시 (좁은 쪽 먼저), 마지막으로 시·도
        val districtCandidates =
            listOfNotNull(subLocality, subAdminArea) + lineTokens + listOfNotNull(locality, adminArea)
        for (raw in districtCandidates) {
            districtName(raw)?.let { return cut(it) }
        }
        return null
    }

    /** 주소 한 줄을 그대로 넣어도 같은 결과를 내는 편의 함수 */
    fun shortName(line: String): String? = shortName(addressLines = listOf(line))

    // ------------------------------------------------------------ 내부 규칙

    /** 동/읍/면으로 끝나는 토큰이면 동 이름까지만 돌려준다 (`정자동123-4` → `정자동`) */
    private fun dongName(raw: String?): String? {
        val token = tidy(raw) ?: return null
        for (suffix in DONG_SUFFIXES) {
            if (!token.endsWith(suffix)) continue
            val head = token.substring(0, token.length - suffix.length)
            // 번지·건물 번호만 붙은 토큰(`123-4동`)이나 접미사 하나(`동`)는 동네 이름이 아니다
            if (head.isEmpty() || head.all { it.isDigit() || it == '-' || it == '.' }) continue
            return token
        }
        return null
    }

    /** 시·군·구·도로 끝나는 행정구역만 골라 `서울특별시` → `서울`, `성남시 분당구` → `분당구` */
    private fun districtName(raw: String?): String? {
        val narrow = placed(tidy(raw) ?: return null)
        if (narrow.length < 2) return null
        if (DISTRICT_SUFFIXES.none { narrow.endsWith(it) }) return null
        return sido(narrow)
    }

    /** `서울특별시 강남구` → `강남구` 처럼 가장 좁은 조각만 남긴다 */
    private fun placed(name: String): String = name.substringAfterLast(' ').trim().ifEmpty { name }

    /** 시·도 접미사 정리 — `서울특별시` → `서울` */
    private fun sido(name: String): String {
        for (suffix in SIDO_SUFFIXES) {
            if (name.length > suffix.length && name.endsWith(suffix)) {
                return name.dropLast(suffix.length).trim()
            }
        }
        return name
    }

    private fun cut(name: String): String =
        if (name.length <= MAX_LEN) name else name.take(MAX_LEN).trim()

    private fun tidy(raw: String?): String? {
        val value = raw?.trim()?.replace(SPACES, " ") ?: return null
        return value.ifEmpty { null }
    }

    private fun tokens(line: String): List<String> =
        line.trim().split(SPACES).filter { it.isNotEmpty() }
}
