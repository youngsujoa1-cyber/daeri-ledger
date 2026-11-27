package com.youngsu.daeriledger.data

import java.util.Locale

/**
 * 카카오 `coord2address` 응답 → 출발지에 넣을 **동·리·가** 이름 (v1.0.28).
 *
 * 한국에서 Android `Geocoder` 는 도로명 주소(`성남대로 393`)만 주거나 `성남시`까지만 주는 일이 많아,
 * 운행 기록의 `📍 현위치` 가 `성남시` 로 떨어졌다(사용자 실기기 확인). 카카오는 좌표를
 * 법정동·리(`region_3depth_name` — `정자동`, `백암면 박곡리`, `종로1가`)로 바로 돌려주므로
 * 그 값을 먼저 쓰고, 없을 때만 Android `Geocoder` + [PlaceNamer] 로 물러난다.
 *
 *  - 요청: `https://dapi.kakao.com/v2/local/geo/coord2address.json?x={lng}&y={lat}`
 *  - 인증: REST 키가 있으면 `Authorization: KakaoAK {REST 키}` 하나로 끝난다.
 *    REST 키가 없으면 앱이 이미 쓰는 **JavaScript 키**로 부른다. JavaScript 키는 카카오 SDK 와
 *    같은 방식으로 `KA` 헤더의 `origin`(콘솔에 등록한 도메인)을 확인하므로 그 헤더를 함께 보낸다.
 *    등록되지 않은 origin 은 카카오가 `domain mismatched` 로 거절한다.
 *
 * JSON 해석은 Android `org.json` 없이 이 파일 안에서 끝낸다 — `tools/logic-check` 가
 * 실제 카카오 응답 그대로 검사할 수 있게 하기 위해서다.
 */
object KakaoReverse {

    /** 카카오 콘솔(앱 1665700)에 등록한 JavaScript SDK 도메인 — 요청 origin 으로 쓴다 */
    const val ORIGIN = "https://youngsujoa1-cyber.github.io"

    /** 지도 페이지 주소 — JavaScript 키 요청의 Referer */
    const val REFERER = "$ORIGIN/daeri-ledger/kakaomap.html"

    /** JavaScript 키로 부를 때 함께 보내는 카카오 SDK 헤더 값 */
    fun kaHeader(): String = "sdk/1.0 os/javascript origin/$ORIGIN lang/ko-KR"

    /** 좌표 → 주소 요청 주소 (x=경도, y=위도 순서에 주의) */
    fun url(lat: Double, lng: Double): String = buildString {
        append("https://dapi.kakao.com/v2/local/geo/coord2address.json")
        append("?x=").append(String.format(Locale.US, "%.7f", lng))
        append("&y=").append(String.format(Locale.US, "%.7f", lat))
        append("&input_coord=WGS84")
    }

    /**
     * 응답 본문 → 동네 이름. 동·리·가가 없으면 구·시까지 내려간다.
     * 응답이 깨졌거나 결과가 없으면 null (호출한 쪽이 `Geocoder` 로 폴백한다).
     */
    fun nameFromJson(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val root = runCatching { Scan(body).value() as? Map<*, *> }.getOrNull() ?: return null
        val documents = root["documents"] as? List<*> ?: return null
        val document = documents.firstOrNull() as? Map<*, *> ?: return null
        val address = document["address"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val road = document["road_address"] as? Map<*, *> ?: emptyMap<String, Any?>()
        return nameOf(
            region3 = text(address["region_3depth_name"]),
            region3h = text(address["region_3depth_h_name"]),
            addressName = text(address["address_name"]),
            roadName = text(road["address_name"])
        )
    }

    /**
     * 카카오가 나눠 주는 이름 조각들 → 출발지 이름.
     * `region_3depth_name` 이 `백암면 박곡리` 처럼 읍·면과 리가 붙어 와도 [PlaceNamer] 가 리만 남긴다.
     */
    fun nameOf(
        region3: String?,
        region3h: String? = null,
        addressName: String? = null,
        roadName: String? = null
    ): String? {
        val candidates = listOfNotNull(region3, region3h, addressName, roadName)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return null
        return PlaceNamer.shortName(candidates)
    }

    private fun text(value: Any?): String? = (value as? String)?.takeIf { it.isNotBlank() }

    // ------------------------------------------------------------ 아주 작은 JSON 읽기

    /** 필요한 값만 꺼내려고 만든 최소 JSON 스캐너 — 문자열·숫자·참/거짓·null·배열·객체 */
    private class Scan(private val text: String) {
        private var i = 0

        fun value(): Any? {
            skip()
            if (i >= text.length) return null
            return when (text[i]) {
                '{' -> obj()
                '[' -> list()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> num()
            }
        }

        private fun skip() {
            while (i < text.length && text[i].isWhitespace()) i++
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++ // {
            skip()
            if (i < text.length && text[i] == '}') { i++; return out }
            while (i < text.length) {
                skip()
                if (i >= text.length || text[i] != '"') break
                val key = str()
                skip()
                if (i < text.length && text[i] == ':') i++
                out[key] = value()
                skip()
                if (i < text.length && text[i] == ',') { i++; continue }
                if (i < text.length && text[i] == '}') i++
                break
            }
            return out
        }

        private fun list(): List<Any?> {
            val out = ArrayList<Any?>()
            i++ // [
            skip()
            if (i < text.length && text[i] == ']') { i++; return out }
            while (i < text.length) {
                out.add(value())
                skip()
                if (i < text.length && text[i] == ',') { i++; continue }
                if (i < text.length && text[i] == ']') i++
                break
            }
            return out
        }

        private fun str(): String {
            val sb = StringBuilder()
            i++ // 여는 따옴표
            while (i < text.length) {
                when (val c = text[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= text.length) break
                        when (val escaped = text[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = text.substring(i, minOf(i + 4, text.length))
                                i += hex.length
                                hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            }
                            else -> sb.append(escaped)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun num(): Double? {
            val start = i
            i++
            while (i < text.length && (text[i].isDigit() || text[i] in "-+.eE")) i++
            return text.substring(start, i).toDoubleOrNull()
        }
    }
}
