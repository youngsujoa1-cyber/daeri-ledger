package com.youngsu.daeriledger.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 주요 대리운전 콜밭 참고 목록.
 * 플랫폼 실시간 콜 수량이 아니라, 지역 특성과 일반적으로 알려진 심야 수요 시간대를 정리한 자료다.
 */
data class Hotspot(
    val id: String,
    val name: String,
    val area: String,
    val city: String,
    val lat: Double,
    val lng: Double,
    val peak: String,
    val tip: String
)

object Hotspots {

    val ALL: List<Hotspot> = listOf(
        Hotspot("gangnam", "강남역 · 역삼", "서울 강남구", "서울", 37.4979, 127.0276, "22:00 ~ 02:00", "대로변 유동 인구가 많고 심야 대기 수요가 꾸준한 대표 콜밭"),
        Hotspot("seolleung", "선릉 · 삼성", "서울 강남구", "서울", 37.5045, 127.0490, "22:30 ~ 01:30", "오피스·유흥 수요가 섞여 2차 이동 콜이 자주 나오는 구간"),
        Hotspot("apgujeong", "압구정 · 청담", "서울 강남구", "서울", 37.5270, 127.0286, "23:00 ~ 03:00", "금·토 심야 장거리 콜 비중이 높은 편"),
        Hotspot("nonhyeon", "논현 · 신사", "서울 강남구", "서울", 37.5111, 127.0218, "22:00 ~ 02:00", "가로수길·먹자골목 인접, 짧은 거리 콜이 많음"),
        Hotspot("jamsil", "잠실 · 신천", "서울 송파구", "서울", 37.5133, 127.1000, "21:30 ~ 02:00", "롯데월드타워·먹자골목 수요, 주말 늦은 시간까지 이어짐"),
        Hotspot("konkuk", "건대 · 성수", "서울 광진구", "서울", 37.5404, 127.0695, "22:00 ~ 03:00", "대학가·성수 카페거리, 20~30대 심야 이동이 많음"),
        Hotspot("hongdae", "홍대 · 합정", "서울 마포구", "서울", 37.5572, 126.9245, "23:00 ~ 04:00", "새벽까지 클럽·술집 수요가 이어지는 대표 심야 콜밭"),
        Hotspot("sinchon", "신촌 · 이대", "서울 서대문구", "서울", 37.5559, 126.9368, "22:00 ~ 02:00", "대학가 특성상 평일 밤에도 꾸준한 편"),
        Hotspot("jongno", "종로 · 을지로", "서울 종로구", "서울", 37.5701, 126.9829, "22:30 ~ 02:00", "직장인 회식 2차 수요, 평일 밤 비중이 높음"),
        Hotspot("yeouido", "여의도", "서울 영등포구", "서울", 37.5219, 126.9245, "21:00 ~ 24:00", "평일 회식 수요 중심, 주말은 한산한 편"),
        Hotspot("yeongdeungpo", "영등포 · 구디", "서울 영등포구", "서울", 37.5159, 126.9076, "22:00 ~ 02:00", "구로디지털단지 야근·회식 수요가 섞임"),
        Hotspot("sadang", "사당 · 이수", "서울 동작구", "서울", 37.4766, 126.9816, "22:00 ~ 02:00", "강남·사당 이동 거점, 환승 수요 많음"),
        Hotspot("magok", "마곡 · 발산", "서울 강서구", "서울", 37.5601, 126.8253, "21:30 ~ 01:00", "업무지구 야근 수요, 주말은 조용함"),
        Hotspot("nowon", "노원 · 상계", "서울 노원구", "서울", 37.6550, 127.0610, "22:00 ~ 01:30", "주거지 중심, 귀가 콜 비중이 높음"),
        Hotspot("pangyo", "판교역", "경기 성남시", "경기", 37.3948, 127.1112, "22:00 ~ 02:00", "IT 업무지구 야근·회식 수요, 평일 심야에 강함"),
        Hotspot("jeongja", "분당 정자역", "경기 성남시", "경기", 37.3671, 127.1085, "22:00 ~ 01:30", "정자·수내 주거 수요, 장거리 귀가 콜이 많음"),
        Hotspot("seohyeon", "분당 서현 · 야탑", "경기 성남시", "경기", 37.3848, 127.1230, "22:00 ~ 02:00", "서현역 먹자골목 중심, 주말 늦은 시간 수요"),
        Hotspot("suwon-ingye", "수원 인계동", "경기 수원시", "경기", 37.2635, 127.0320, "22:30 ~ 03:00", "수원 대표 유흥가, 새벽까지 이어지는 편"),
        Hotspot("suwon-station", "수원역", "경기 수원시", "경기", 37.2657, 126.9997, "21:00 ~ 01:00", "역 주변 술집·모텔 수요, 짧은 콜 많음"),
        Hotspot("beomgye", "안양 범계", "경기 안양시", "경기", 37.3909, 126.9510, "22:00 ~ 02:00", "범계역 로데오거리 중심 콜밭"),
        Hotspot("ilsan", "일산 정발산", "경기 고양시", "경기", 37.6595, 126.7731, "22:00 ~ 02:00", "라페스타·웨스턴돔 유흥 수요"),
        Hotspot("bupyeong", "부평", "인천 부평구", "인천", 37.4894, 126.7247, "22:00 ~ 03:00", "부평역 먹자골목, 인천 최대 심야 콜밭 중 하나"),
        Hotspot("songdo", "송도", "인천 연수구", "인천", 37.3820, 126.6560, "21:30 ~ 01:00", "주거·업무 혼합, 귀가 콜 위주"),
        Hotspot("seomyeon", "부산 서면", "부산 부산진구", "부산", 35.1578, 129.0595, "22:30 ~ 04:00", "부산 최대 유흥가, 새벽 콜 비중 높음"),
        Hotspot("haeundae", "부산 해운대", "부산 해운대구", "부산", 35.1631, 129.1636, "22:00 ~ 03:00", "해운대 해변로·먹자골목, 관광 시즌 수요 증가"),
        Hotspot("gwangalli", "부산 광안리", "부산 수영구", "부산", 35.1531, 129.1186, "22:00 ~ 03:00", "광안리 해변 상권, 주말 심야에 강함"),
        Hotspot("dongseongro", "대구 동성로", "대구 중구", "대구", 35.8693, 128.5950, "22:00 ~ 03:00", "대구 중심 유흥가, 젊은 층 이동 많음"),
        Hotspot("dunsan", "대전 둔산동", "대전 서구", "대전", 36.3511, 127.3845, "22:00 ~ 02:00", "시청·둔산 업무지구 회식 수요"),
        Hotspot("sangmu", "광주 상무지구", "광주 서구", "광주", 35.1520, 126.8500, "22:00 ~ 02:30", "광주 대표 유흥·회식 밀집 지역"),
        Hotspot("samsan", "울산 삼산", "울산 남구", "울산", 35.5390, 129.3370, "22:00 ~ 02:00", "삼산지구 술집 밀집, 산업단지 야근 수요도 있음"),
        Hotspot("seongangil", "청주 성안길", "충북 청주시", "충북", 36.6360, 127.4880, "22:00 ~ 02:00", "청주 중심 상권, 주말 심야 수요"),
        Hotspot("gunja", "군자 · 장한평", "서울 광진구", "서울", 37.5575, 127.0794, "22:00 ~ 02:00", "주거 밀집지역 귀가 콜 위주"),
        Hotspot("sangbong", "상봉 · 면목", "서울 중랑구", "서울", 37.5960, 127.0857, "22:00 ~ 01:30", "동북권 환승 거점, 심야 귀가 수요"),
        Hotspot("guro", "구로 · 신도림", "서울 구로구", "서울", 37.5088, 126.8912, "21:30 ~ 01:30", "역세권·오피스 수요, 평일 밤 중심"),
        Hotspot("bundang-gu", "분당 수내", "경기 성남시", "경기", 37.3784, 127.1153, "22:00 ~ 01:30", "아파트 밀집지역, 귀가 콜 위주")
    )

    val CITIES: List<String> = listOf("서울", "경기", "인천", "부산", "대구", "대전", "광주", "울산", "충북")

    /** 두 좌표 사이 거리(km) — 하버사인 */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun formatDistance(km: Double?): String {
        if (km == null) return ""
        if (km < 1.0) return "${(km * 1000).toInt()}m"
        if (km < 100) return String.format(java.util.Locale.KOREA, "%.1fkm", km)
        return String.format(java.util.Locale.KOREA, "%.0fkm", km)
    }

    fun byId(id: String): Hotspot? = ALL.firstOrNull { it.id == id }

    /** 이름으로 대략적인 좌표 찾기 (내 콜밭 저장·표시용) */
    fun findByName(name: String): Hotspot? {
        val n = name.trim()
        if (n.isEmpty()) return null
        return ALL.firstOrNull { it.name == n }
            ?: ALL.firstOrNull { it.name.contains(n) || n.contains(it.name) }
            ?: ALL.firstOrNull { it.name.split(" ").any { part -> part.isNotBlank() && n.contains(part) } }
    }

    fun sameSpot(a: String, b: String): Boolean =
        a.trim().equals(b.trim(), ignoreCase = true)
}
