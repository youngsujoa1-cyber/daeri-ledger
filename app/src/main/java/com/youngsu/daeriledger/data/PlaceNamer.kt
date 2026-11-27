package com.youngsu.daeriledger.data

/**
 * Geocoder·카카오 `coord2address` 가 준 행정구역 조각 → 대리운전 기사가 바로 알아보는 **짧은 동네 이름** (v1.0.28).
 *
 * 운행 기록 화면의 `📍 현위치` 버튼이 잡은 좌표를 출발지 칸에 넣을 때 쓴다.
 * 도로명(`정자일로`)·건물명보다 **동·리·가·읍·면**을 먼저 고르고, 없으면 구·시를 쓴다.
 *
 *  - `성남시 분당구 정자동`     → `정자동`
 *  - `용인시 처인구 백암면 박곡리` → `박곡리`   (읍·면보다 좁은 리를 먼저)
 *  - `서울 종로구 종로1가`      → `종로1가`
 *  - `성남시 분당구`            → `분당구`
 *  - `서울특별시`               → `서울`
 *
 * Android 와 무관한 순수 로직이라 `tools/logic-check` 에서 그대로 검사한다.
 */
object PlaceNamer {

    /** 출발지 칸에 넣는 이름 최대 길이 — 입력칸이 좁아 길면 줄인다 */
    const val MAX_LEN = 20

    /**
     * 동네로 보는 접미사 — **좁은 단위가 앞**.
     * `백암면 박곡리` 처럼 읍·면 안에 리가 들어 있으면 리를 고르고,
     * `을지로3가` 처럼 번지가 붙은 `가` 이름은 동보다도 좁은 자리 이름이라 앞에 둔다.
     */
    private val DONG_UNITS: List<List<String>> = listOf(
        listOf("리"),
        listOf("동"),
        listOf("가"),
        listOf("읍"),
        listOf("면")
    )

    /** 시·군·구 — 도보다 좁다 */
    private val DISTRICT_UNITS = listOf("시", "군", "구")

    /** 시·도 — 마지막 대비책 */
    private val SIDO_UNITS = listOf("도")

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
        // 주소 줄은 뒤쪽(가장 좁은 행정구역)부터 본다
        val lines = addressLines.asReversed()
        val dongCandidates = listOfNotNull(subLocality, subAdminArea, thoroughfare) + lines
        val districtCandidates =
            listOfNotNull(subLocality, subAdminArea) + lines + listOfNotNull(locality, adminArea)
        return pick(dongCandidates, districtCandidates)
    }

    /** 주소 한 줄을 그대로 넣어도 같은 결과를 내는 편의 함수 */
    fun shortName(line: String): String? = shortName(addressLines = listOf(line))

    /**
     * 카카오 `coord2address` 처럼 **이미 동네 단위로 쪼개진 이름들**을 우선순위대로 넘길 때 쓴다.
     * 앞에 있는 이름이 이기고(`region_3depth_name` → 행정동 → 지번 주소 → 도로명 주소),
     * 한 이름 안에서는 뒤쪽(좁은) 토큰을 먼저 본다.
     */
    fun shortName(candidates: List<String>): String? = pick(candidates, candidates)

    // ------------------------------------------------------------ 내부 규칙

    /** 동네 단위(리 → 동 → 가 → 읍 → 면)를 먼저, 없으면 구·시, 마지막으로 시·도 */
    private fun pick(dongCandidates: List<String>, districtCandidates: List<String>): String? {
        for (units in DONG_UNITS) {
            for (candidate in dongCandidates) {
                val hit = tokens(candidate).lastOrNull { matchesUnit(it, units) } ?: continue
                return cut(hit)
            }
        }
        for (units in listOf(DISTRICT_UNITS, SIDO_UNITS)) {
            for (candidate in districtCandidates) {
                val hit = tokens(candidate).lastOrNull { matchesUnit(it, units) } ?: continue
                return cut(sido(hit))
            }
        }
        return null
    }

    /**
     * 토큰이 그 단위로 끝나는지 — 번지·건물 번호만 붙은 토큰(`123-4동`, `123-4리`)과
     * 접미사 하나짜리 토큰(`동`)은 동네 이름이 아니다.
     * `가` 는 번지가 붙은 자리 이름만 인정한다(`종로1가`·`을지로3가` — `국가` 같은 낱말 방지).
     */
    private fun matchesUnit(token: String, units: List<String>): Boolean {
        for (unit in units) {
            if (!token.endsWith(unit)) continue
            val head = token.substring(0, token.length - unit.length)
            if (head.isEmpty() || head.all { it.isDigit() || it == '-' || it == '.' }) return false
            if (unit == "가" && head.none { it.isDigit() }) return false
            return true
        }
        return false
    }

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

    private fun tokens(line: String): List<String> =
        line.trim().replace(SPACES, " ").split(" ").filter { it.isNotEmpty() }
}
