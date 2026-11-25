package com.youngsu.daeriledger.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 주요 대리운전 콜밭 참고 목록.
 * 플랫폼 실시간 콜 수량이 아니라, 공개된 상권·역세권·먹자골목 기준으로 지역 특성과
 * 일반적으로 알려진 심야 수요 시간대를 정리한 자료다.
 */
data class Hotspot(
    val id: String,
    val name: String,
    val area: String,
    val city: String,
    val lat: Double,
    val lng: Double,
    val peak: String,
    val tip: String,
    /** 어디서 온 콜밭인지 — "" = 기본(직접 정리), "콜포인트1"·"콜포인트2"·"탁송" = 카카오맵 즐겨찾기 */
    val source: String = ""
) {
    /** 피크 시간대 정보가 있는 곳(직접 정리한 콜밭)만 true */
    val hasPeak: Boolean get() = peak.isNotBlank()

    /** 검색어가 이름·지역·시·특징 중 어디에든 들어 있는지 (공백으로 여러 낱말 AND) */
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val haystack = "$name $area $city $tip $source"
        return q.split(" ", "\t").filter { it.isNotBlank() }.all { haystack.contains(it, ignoreCase = true) }
    }
}

object Hotspots {

    /** 직접 정리한 기본 콜밭 — 피크 시간대·특징 메모가 붙어 있다 */
    val CURATED: List<Hotspot> = listOf(
        Hotspot(
            id = "hannam-ogeori",
            name = "한남오거리",
            area = "서울 용산구",
            city = "서울",
            lat = 37.533373,
            lng = 127.007634,
            peak = "23:00 ~ 03:30",
            tip = "한남동 식당·유흥 밀집, 새벽까지 손님이 끊기지 않는 사거리"
        ),
        Hotspot(
            id = "hannam-unvillage",
            name = "한남동 유엔빌리지",
            area = "서울 용산구",
            city = "서울",
            lat = 37.534713,
            lng = 127.011908,
            peak = "22:30 ~ 02:30",
            tip = "외국인·고소득 주거지, 장거리 귀가 콜 비중이 높음"
        ),
        Hotspot(
            id = "itaewon",
            name = "이태원역",
            area = "서울 용산구",
            city = "서울",
            lat = 37.534542,
            lng = 126.994596,
            peak = "23:00 ~ 04:00",
            tip = "외국인·관광객 수요, 주말 새벽까지 이어짐"
        ),
        Hotspot(
            id = "gyeongnidan",
            name = "경리단길 · 녹사평",
            area = "서울 용산구",
            city = "서울",
            lat = 37.534945,
            lng = 126.985351,
            peak = "22:30 ~ 03:00",
            tip = "와인바·레스토랑 밀집, 2차 이동 콜이 꾸준함"
        ),
        Hotspot(
            id = "haebangchon",
            name = "해방촌",
            area = "서울 용산구",
            city = "서울",
            lat = 37.540226,
            lng = 126.986706,
            peak = "22:00 ~ 02:30",
            tip = "언덕 골목 상권, 짧은 거리 귀가 콜 위주"
        ),
        Hotspot(
            id = "yongsan",
            name = "용산역 · 신용산",
            area = "서울 용산구",
            city = "서울",
            lat = 37.529944,
            lng = 126.964773,
            peak = "22:00 ~ 02:00",
            tip = "KTX·환승 거점, 심야 도착객 귀가 콜이 많음"
        ),
        Hotspot(
            id = "samgakji",
            name = "삼각지역",
            area = "서울 용산구",
            city = "서울",
            lat = 37.535032,
            lng = 126.973451,
            peak = "22:00 ~ 02:00",
            tip = "역 주변 술집·주거 수요가 섞인 구간"
        ),
        Hotspot(
            id = "sookdae",
            name = "숙대입구",
            area = "서울 용산구",
            city = "서울",
            lat = 37.546475,
            lng = 126.964692,
            peak = "22:00 ~ 02:00",
            tip = "대학가·주거 혼합, 평일 밤 수요가 꾸준함"
        ),
        Hotspot(
            id = "myeongdong",
            name = "명동",
            area = "서울 중구",
            city = "서울",
            lat = 37.559980,
            lng = 126.985830,
            peak = "22:00 ~ 03:00",
            tip = "관광·쇼핑 상권, 주말 심야에 강한 편"
        ),
        Hotspot(
            id = "euljiro-ipgu",
            name = "을지로입구",
            area = "서울 중구",
            city = "서울",
            lat = 37.566065,
            lng = 126.982679,
            peak = "22:00 ~ 02:00",
            tip = "직장인 회식 2차 수요, 평일 밤 비중이 높음"
        ),
        Hotspot(
            id = "euljiro-3ga",
            name = "을지로3가",
            area = "서울 중구",
            city = "서울",
            lat = 37.566288,
            lng = 126.991051,
            peak = "22:30 ~ 03:00",
            tip = "노포·술집 골목, 젊은 층 심야 이동 많음"
        ),
        Hotspot(
            id = "chungmuro",
            name = "충무로",
            area = "서울 중구",
            city = "서울",
            lat = 37.560997,
            lng = 126.994728,
            peak = "21:30 ~ 01:30",
            tip = "인쇄골목·회식 수요, 이른 심야 위주"
        ),
        Hotspot(
            id = "cityhall",
            name = "시청",
            area = "서울 중구",
            city = "서울",
            lat = 37.565704,
            lng = 126.976862,
            peak = "21:00 ~ 24:00",
            tip = "관공서·호텔 회식 수요, 주말은 한산한 편"
        ),
        Hotspot(
            id = "dongdaemun",
            name = "동대문 · DDP",
            area = "서울 중구",
            city = "서울",
            lat = 37.565682,
            lng = 127.008953,
            peak = "23:00 ~ 04:00",
            tip = "의류 도매시장 야간 수요로 새벽 콜이 많은 곳"
        ),
        Hotspot(
            id = "sindang",
            name = "신당역",
            area = "서울 중구",
            city = "서울",
            lat = 37.565669,
            lng = 127.019452,
            peak = "22:00 ~ 02:30",
            tip = "먹자골목·주거 밀집, 귀가 콜 비중이 높음"
        ),
        Hotspot(
            id = "yaksu",
            name = "약수역",
            area = "서울 중구",
            city = "서울",
            lat = 37.551313,
            lng = 127.010984,
            peak = "21:30 ~ 01:30",
            tip = "주거지역 상권, 이른 심야 짧은 콜"
        ),
        Hotspot(
            id = "cheonggu",
            name = "청구역",
            area = "서울 중구",
            city = "서울",
            lat = 37.559114,
            lng = 127.013100,
            peak = "21:30 ~ 01:00",
            tip = "환승 거점·주거 수요, 평일 밤 중심"
        ),
        Hotspot(
            id = "gwanghwamun",
            name = "광화문",
            area = "서울 종로구",
            city = "서울",
            lat = 37.572389,
            lng = 126.976912,
            peak = "21:00 ~ 24:00",
            tip = "직장인 회식·관광 수요, 주말은 한산한 편"
        ),
        Hotspot(
            id = "jonggak",
            name = "종각역",
            area = "서울 종로구",
            city = "서울",
            lat = 37.570176,
            lng = 126.983197,
            peak = "22:00 ~ 01:30",
            tip = "오피스 회식 밀집, 2차 이동 콜이 많음"
        ),
        Hotspot(
            id = "jongno",
            name = "종로3가 · 익선동",
            area = "서울 종로구",
            city = "서울",
            lat = 37.570436,
            lng = 126.992148,
            peak = "22:30 ~ 02:00",
            tip = "포장마차·익선동 상권, 직장인 2차 수요"
        ),
        Hotspot(
            id = "gangnam",
            name = "강남역",
            area = "서울 강남구",
            city = "서울",
            lat = 37.497952,
            lng = 127.027619,
            peak = "22:00 ~ 02:00",
            tip = "대로변 유동 인구가 많고 심야 대기 수요가 꾸준한 대표 콜밭"
        ),
        Hotspot(
            id = "sinnonhyeon",
            name = "신논현역",
            area = "서울 강남구",
            city = "서울",
            lat = 37.504724,
            lng = 127.025380,
            peak = "22:30 ~ 03:00",
            tip = "술집 골목·유흥가 중심, 새벽까지 콜이 이어짐"
        ),
        Hotspot(
            id = "nonhyeon",
            name = "논현역",
            area = "서울 강남구",
            city = "서울",
            lat = 37.510648,
            lng = 127.021828,
            peak = "22:00 ~ 02:30",
            tip = "먹자골목·유흥가 혼합, 짧은 거리 콜 비중 높음"
        ),
        Hotspot(
            id = "hakdong",
            name = "학동사거리",
            area = "서울 강남구",
            city = "서울",
            lat = 37.523097,
            lng = 127.039104,
            peak = "22:00 ~ 02:00",
            tip = "학동역 인근 먹자골목, 2차 귀가 콜 위주"
        ),
        Hotspot(
            id = "sinsa",
            name = "신사역 · 가로수길",
            area = "서울 강남구",
            city = "서울",
            lat = 37.516287,
            lng = 127.020023,
            peak = "22:30 ~ 03:00",
            tip = "가로수길·세로수길 상권, 주말 심야에 강함"
        ),
        Hotspot(
            id = "apgujeong",
            name = "압구정역",
            area = "서울 강남구",
            city = "서울",
            lat = 37.526330,
            lng = 127.028513,
            peak = "23:00 ~ 03:00",
            tip = "유흥가·백화점 인접, 금·토 심야 장거리 콜이 많음"
        ),
        Hotspot(
            id = "apgujeong-rodeo",
            name = "압구정로데오",
            area = "서울 강남구",
            city = "서울",
            lat = 37.527394,
            lng = 127.040572,
            peak = "23:00 ~ 04:00",
            tip = "클럽·바 밀집, 새벽까지 이어지는 대표 콜밭"
        ),
        Hotspot(
            id = "cheongdam-sageori",
            name = "청담사거리",
            area = "서울 강남구",
            city = "서울",
            lat = 37.524180,
            lng = 127.047325,
            peak = "23:00 ~ 03:30",
            tip = "고급 주점·레스토랑, 장거리 귀가 콜 비중이 높음"
        ),
        Hotspot(
            id = "cheongdam",
            name = "청담역 · 경기고",
            area = "서울 강남구",
            city = "서울",
            lat = 37.519120,
            lng = 127.051937,
            peak = "22:30 ~ 02:30",
            tip = "주점·상가 밀집, 심야 유동 인구 꾸준함"
        ),
        Hotspot(
            id = "samseong",
            name = "삼성역",
            area = "서울 강남구",
            city = "서울",
            lat = 37.508861,
            lng = 127.063149,
            peak = "22:00 ~ 01:30",
            tip = "오피스·전시장 회식 수요, 평일 심야 중심"
        ),
        Hotspot(
            id = "coex",
            name = "코엑스 · 봉은사",
            area = "서울 강남구",
            city = "서울",
            lat = 37.514221,
            lng = 127.060266,
            peak = "22:00 ~ 02:00",
            tip = "호텔·전시장 행사 뒤 귀가 콜이 몰리는 곳"
        ),
        Hotspot(
            id = "seolleung",
            name = "선릉역",
            area = "서울 강남구",
            city = "서울",
            lat = 37.504487,
            lng = 127.048957,
            peak = "22:30 ~ 01:30",
            tip = "오피스·유흥 수요가 섞여 2차 이동 콜이 자주 나오는 구간"
        ),
        Hotspot(
            id = "yeoksam",
            name = "역삼역",
            area = "서울 강남구",
            city = "서울",
            lat = 37.500078,
            lng = 127.038542,
            peak = "22:00 ~ 02:00",
            tip = "오피스 회식·유흥가 혼합, 평일 밤 강함"
        ),
        Hotspot(
            id = "eonju",
            name = "언주역 · 차병원사거리",
            area = "서울 강남구",
            city = "서울",
            lat = 37.507323,
            lng = 127.033903,
            peak = "21:30 ~ 01:30",
            tip = "병원·오피스 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "daechi",
            name = "대치 · 한티",
            area = "서울 강남구",
            city = "서울",
            lat = 37.496208,
            lng = 127.052883,
            peak = "21:30 ~ 01:00",
            tip = "학원가·주거 밀집, 밤늦게 귀가 콜이 늘어남"
        ),
        Hotspot(
            id = "dogok",
            name = "도곡 · 매봉",
            area = "서울 강남구",
            city = "서울",
            lat = 37.487035,
            lng = 127.046918,
            peak = "21:30 ~ 01:00",
            tip = "아파트·학원가 수요, 짧은 귀가 콜 위주"
        ),
        Hotspot(
            id = "yangjae",
            name = "양재역",
            area = "서울 강남구",
            city = "서울",
            lat = 37.484102,
            lng = 127.034369,
            peak = "22:00 ~ 02:00",
            tip = "환승·오피스 수요가 섞인 구간"
        ),
        Hotspot(
            id = "yangjae-citizen",
            name = "양재시민의숲",
            area = "서울 서초구",
            city = "서울",
            lat = 37.469913,
            lng = 127.038396,
            peak = "21:30 ~ 01:00",
            tip = "주거·공원 인접, 이른 심야 귀가 콜 위주"
        ),
        Hotspot(
            id = "suseo",
            name = "수서역",
            area = "서울 강남구",
            city = "서울",
            lat = 37.487468,
            lng = 127.101325,
            peak = "21:30 ~ 01:30",
            tip = "SRT·환승 거점, 도착객 귀가 콜이 많음"
        ),
        Hotspot(
            id = "gyodae",
            name = "교대역",
            area = "서울 서초구",
            city = "서울",
            lat = 37.493471,
            lng = 127.014229,
            peak = "22:00 ~ 02:00",
            tip = "법원·학원가 수요, 평일 밤 중심"
        ),
        Hotspot(
            id = "seocho",
            name = "서초역",
            area = "서울 서초구",
            city = "서울",
            lat = 37.491840,
            lng = 127.007688,
            peak = "21:30 ~ 01:00",
            tip = "법조·오피스 회식 수요, 이른 심야"
        ),
        Hotspot(
            id = "nambu",
            name = "남부터미널",
            area = "서울 서초구",
            city = "서울",
            lat = 37.484918,
            lng = 127.016290,
            peak = "21:30 ~ 01:30",
            tip = "터미널·학원가 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "bangbae",
            name = "방배역 · 내방",
            area = "서울 서초구",
            city = "서울",
            lat = 37.479494,
            lng = 126.993121,
            peak = "22:00 ~ 02:00",
            tip = "주거·먹자골목 혼합, 짧은 콜이 많음"
        ),
        Hotspot(
            id = "seorae",
            name = "서래마을",
            area = "서울 서초구",
            city = "서울",
            lat = 37.497266,
            lng = 126.998442,
            peak = "22:00 ~ 02:00",
            tip = "프랑스촌·레스토랑 상권, 장거리 귀가 콜"
        ),
        Hotspot(
            id = "expressbus",
            name = "고속터미널 · 반포",
            area = "서울 서초구",
            city = "서울",
            lat = 37.504914,
            lng = 127.004915,
            peak = "22:00 ~ 02:30",
            tip = "터미널·백화점·아파트 수요가 겹치는 구간"
        ),
        Hotspot(
            id = "jamwon",
            name = "신반포 · 잠원",
            area = "서울 서초구",
            city = "서울",
            lat = 37.512960,
            lng = 127.011611,
            peak = "21:30 ~ 01:30",
            tip = "아파트·한강공원 인접, 귀가 콜 위주"
        ),
        Hotspot(
            id = "jamsil",
            name = "잠실역",
            area = "서울 송파구",
            city = "서울",
            lat = 37.513261,
            lng = 127.100134,
            peak = "21:30 ~ 02:00",
            tip = "롯데월드타워·먹자골목 수요, 주말 늦은 시간까지 이어짐"
        ),
        Hotspot(
            id = "jamsilsaenae",
            name = "잠실새내(신천)",
            area = "서울 송파구",
            city = "서울",
            lat = 37.511624,
            lng = 127.085585,
            peak = "22:00 ~ 02:30",
            tip = "먹자골목·유흥가 밀집, 새벽까지 콜이 이어짐"
        ),
        Hotspot(
            id = "bangi",
            name = "방이먹자골목",
            area = "서울 송파구",
            city = "서울",
            lat = 37.514765,
            lng = 127.109209,
            peak = "22:30 ~ 03:00",
            tip = "대형 먹자골목, 2차 이동과 귀가 콜이 많음"
        ),
        Hotspot(
            id = "seokchon",
            name = "석촌 · 송파",
            area = "서울 송파구",
            city = "서울",
            lat = 37.505714,
            lng = 127.107069,
            peak = "21:30 ~ 01:30",
            tip = "호수공원·주거 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "munjeong",
            name = "문정법조단지",
            area = "서울 송파구",
            city = "서울",
            lat = 37.485585,
            lng = 127.122563,
            peak = "21:30 ~ 01:00",
            tip = "법조단지·오피스 회식 뒤 귀가 콜 위주"
        ),
        Hotspot(
            id = "garak",
            name = "가락시장역",
            area = "서울 송파구",
            city = "서울",
            lat = 37.492317,
            lng = 127.118104,
            peak = "21:30 ~ 02:00",
            tip = "시장 상인·주거 수요, 새벽 준비 시간대도 있음"
        ),
        Hotspot(
            id = "jangji",
            name = "장지 · 위례",
            area = "서울 송파구",
            city = "서울",
            lat = 37.477699,
            lng = 127.126274,
            peak = "21:30 ~ 01:00",
            tip = "신도시·주거 혼합, 귀가 콜 위주"
        ),
        Hotspot(
            id = "gangdong",
            name = "천호역",
            area = "서울 강동구",
            city = "서울",
            lat = 37.538486,
            lng = 127.123905,
            peak = "22:00 ~ 01:30",
            tip = "천호역 로데오 상권, 주말 심야에 강한 편"
        ),
        Hotspot(
            id = "gildong",
            name = "길동사거리",
            area = "서울 강동구",
            city = "서울",
            lat = 37.533931,
            lng = 127.138566,
            peak = "21:30 ~ 01:30",
            tip = "주거·먹자골목 혼합, 짧은 귀가 콜"
        ),
        Hotspot(
            id = "amsa",
            name = "암사역 · 명일",
            area = "서울 강동구",
            city = "서울",
            lat = 37.550148,
            lng = 127.127519,
            peak = "21:30 ~ 01:00",
            tip = "전통시장·아파트 수요, 이른 심야 중심"
        ),
        Hotspot(
            id = "gangdong-stn",
            name = "강동역",
            area = "서울 강동구",
            city = "서울",
            lat = 37.535940,
            lng = 127.132187,
            peak = "21:30 ~ 01:30",
            tip = "환승 거점·주거 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "dunchon",
            name = "둔촌동",
            area = "서울 강동구",
            city = "서울",
            lat = 37.527657,
            lng = 127.136231,
            peak = "21:30 ~ 01:00",
            tip = "대단지 아파트, 귀가 콜 비중이 높음"
        ),
        Hotspot(
            id = "seongsu",
            name = "성수역",
            area = "서울 성동구",
            city = "서울",
            lat = 37.544577,
            lng = 127.055991,
            peak = "22:00 ~ 02:30",
            tip = "카페거리·창고형 술집 수요, 주말 늦은 시간까지 이어짐"
        ),
        Hotspot(
            id = "ttukseom",
            name = "뚝섬 · 서울숲",
            area = "서울 성동구",
            city = "서울",
            lat = 37.544388,
            lng = 127.037442,
            peak = "22:00 ~ 02:00",
            tip = "핫플 카페·바 밀집, 주말 심야 수요"
        ),
        Hotspot(
            id = "wangsimni",
            name = "왕십리역",
            area = "서울 성동구",
            city = "서울",
            lat = 37.562353,
            lng = 127.038496,
            peak = "22:00 ~ 01:30",
            tip = "환승 거점·먹자골목, 귀가 콜과 2차 이동이 섞임"
        ),
        Hotspot(
            id = "sangwangsimni",
            name = "상왕십리 · 행당",
            area = "서울 성동구",
            city = "서울",
            lat = 37.564500,
            lng = 127.028872,
            peak = "21:30 ~ 01:00",
            tip = "주거 밀집지역, 짧은 귀가 콜 위주"
        ),
        Hotspot(
            id = "hanyang",
            name = "한양대앞",
            area = "서울 성동구",
            city = "서울",
            lat = 37.557176,
            lng = 127.045409,
            peak = "22:30 ~ 03:00",
            tip = "대학가 술집 밀집, 새벽까지 수요가 이어짐"
        ),
        Hotspot(
            id = "konkuk",
            name = "건대입구",
            area = "서울 광진구",
            city = "서울",
            lat = 37.540372,
            lng = 127.069276,
            peak = "22:00 ~ 03:00",
            tip = "대학가·먹자골목, 20~30대 심야 이동이 많음"
        ),
        Hotspot(
            id = "guui",
            name = "구의역 · 강변",
            area = "서울 광진구",
            city = "서울",
            lat = 37.537153,
            lng = 127.086152,
            peak = "21:30 ~ 02:00",
            tip = "강변역 상권·주거 수요가 섞임"
        ),
        Hotspot(
            id = "gunja",
            name = "군자역",
            area = "서울 광진구",
            city = "서울",
            lat = 37.557336,
            lng = 127.079589,
            peak = "22:00 ~ 02:00",
            tip = "주거 밀집지역 귀가 콜 위주"
        ),
        Hotspot(
            id = "janghanpyeong",
            name = "장한평역",
            area = "서울 동대문구",
            city = "서울",
            lat = 37.561472,
            lng = 127.064657,
            peak = "21:30 ~ 01:00",
            tip = "자동차부품 상가·주거 수요, 이른 심야"
        ),
        Hotspot(
            id = "cheongnyangni",
            name = "청량리역",
            area = "서울 동대문구",
            city = "서울",
            lat = 37.581381,
            lng = 127.048958,
            peak = "21:30 ~ 01:30",
            tip = "철도 환승·시장 상권, 이른 심야 수요가 많음"
        ),
        Hotspot(
            id = "hoegi",
            name = "회기역 · 경희대",
            area = "서울 동대문구",
            city = "서울",
            lat = 37.589756,
            lng = 127.057977,
            peak = "22:00 ~ 03:00",
            tip = "대학가 먹자골목, 새벽까지 이어짐"
        ),
        Hotspot(
            id = "dapsimni",
            name = "답십리",
            area = "서울 동대문구",
            city = "서울",
            lat = 37.567072,
            lng = 127.052382,
            peak = "21:30 ~ 01:00",
            tip = "주거 밀집·먹자골목, 짧은 귀가 콜"
        ),
        Hotspot(
            id = "sangbong",
            name = "상봉역",
            area = "서울 중랑구",
            city = "서울",
            lat = 37.596408,
            lng = 127.085825,
            peak = "22:00 ~ 01:30",
            tip = "동북권 환승 거점, 심야 귀가 수요"
        ),
        Hotspot(
            id = "myeonmok",
            name = "면목 · 사가정",
            area = "서울 중랑구",
            city = "서울",
            lat = 37.580966,
            lng = 127.088503,
            peak = "21:30 ~ 01:30",
            tip = "주거·먹자골목 혼합, 귀가 콜 위주"
        ),
        Hotspot(
            id = "hongdae",
            name = "홍대입구",
            area = "서울 마포구",
            city = "서울",
            lat = 37.557527,
            lng = 126.924467,
            peak = "23:00 ~ 04:00",
            tip = "새벽까지 클럽·술집 수요가 이어지는 대표 심야 콜밭"
        ),
        Hotspot(
            id = "yeonnam",
            name = "연남동",
            area = "서울 마포구",
            city = "서울",
            lat = 37.562744,
            lng = 126.921409,
            peak = "22:30 ~ 03:00",
            tip = "골목 상권·카페 밀집, 주말 심야 수요"
        ),
        Hotspot(
            id = "hapjeong",
            name = "합정역",
            area = "서울 마포구",
            city = "서울",
            lat = 37.548301,
            lng = 126.911175,
            peak = "22:00 ~ 02:30",
            tip = "술집·카페 밀집, 주말 심야 수요가 꾸준한 편"
        ),
        Hotspot(
            id = "sangsu",
            name = "상수 · 당인리",
            area = "서울 마포구",
            city = "서울",
            lat = 37.546670,
            lng = 126.924487,
            peak = "22:30 ~ 03:00",
            tip = "홍대권 술집 밀집, 짧은 거리 이동 많음"
        ),
        Hotspot(
            id = "mangwon",
            name = "망원역",
            area = "서울 마포구",
            city = "서울",
            lat = 37.556069,
            lng = 126.910091,
            peak = "22:00 ~ 02:00",
            tip = "먹자골목·시장 상권, 귀가 콜 위주"
        ),
        Hotspot(
            id = "gongdeok",
            name = "공덕역",
            area = "서울 마포구",
            city = "서울",
            lat = 37.543220,
            lng = 126.951576,
            peak = "21:30 ~ 01:30",
            tip = "오피스 회식·주거 수요가 함께 있는 구간"
        ),
        Hotspot(
            id = "mapo",
            name = "마포역",
            area = "서울 마포구",
            city = "서울",
            lat = 37.540040,
            lng = 126.945471,
            peak = "21:30 ~ 01:30",
            tip = "마포대로 오피스·주거 수요"
        ),
        Hotspot(
            id = "sinchon",
            name = "신촌",
            area = "서울 서대문구",
            city = "서울",
            lat = 37.559771,
            lng = 126.942367,
            peak = "22:00 ~ 03:00",
            tip = "대학가·번화가, 새벽까지 수요가 이어짐"
        ),
        Hotspot(
            id = "ewha",
            name = "이대",
            area = "서울 서대문구",
            city = "서울",
            lat = 37.556761,
            lng = 126.945857,
            peak = "22:00 ~ 02:30",
            tip = "대학가 쇼핑·술집 상권, 젊은 층 이동 많음"
        ),
        Hotspot(
            id = "ahyeon",
            name = "아현 · 충정로",
            area = "서울 서대문구",
            city = "서울",
            lat = 37.562683,
            lng = 126.964574,
            peak = "21:30 ~ 01:30",
            tip = "환승·오피스 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "sangam",
            name = "상암DMC",
            area = "서울 마포구",
            city = "서울",
            lat = 37.577964,
            lng = 126.900420,
            peak = "21:30 ~ 01:00",
            tip = "방송사·IT 야근 수요, 평일 심야 중심"
        ),
        Hotspot(
            id = "yeonsinnae",
            name = "연신내",
            area = "서울 은평구",
            city = "서울",
            lat = 37.618812,
            lng = 126.920842,
            peak = "22:00 ~ 02:00",
            tip = "서북권 환승 거점, 먹자골목 심야 수요"
        ),
        Hotspot(
            id = "bulgwang",
            name = "불광역",
            area = "서울 은평구",
            city = "서울",
            lat = 37.610503,
            lng = 126.929844,
            peak = "21:30 ~ 01:30",
            tip = "주거·병원 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "eungam",
            name = "응암 · 역촌",
            area = "서울 은평구",
            city = "서울",
            lat = 37.598472,
            lng = 126.915592,
            peak = "21:30 ~ 01:00",
            tip = "주거 밀집, 짧은 귀가 콜 비중 높음"
        ),
        Hotspot(
            id = "yeouido",
            name = "여의도역",
            area = "서울 영등포구",
            city = "서울",
            lat = 37.521569,
            lng = 126.924312,
            peak = "21:00 ~ 24:00",
            tip = "평일 회식 수요 중심, 주말은 한산한 편"
        ),
        Hotspot(
            id = "saetgang",
            name = "샛강 · 국회의사당",
            area = "서울 영등포구",
            city = "서울",
            lat = 37.528128,
            lng = 126.917853,
            peak = "21:00 ~ 24:00",
            tip = "국회·금융가 회식 수요, 평일 밤 중심"
        ),
        Hotspot(
            id = "yeongdeungpo",
            name = "영등포역 · 타임스퀘어",
            area = "서울 영등포구",
            city = "서울",
            lat = 37.515413,
            lng = 126.907129,
            peak = "22:00 ~ 02:00",
            tip = "역 주변 먹자골목·모텔 수요, 짧은 콜이 많음"
        ),
        Hotspot(
            id = "dangsan",
            name = "당산역",
            area = "서울 영등포구",
            city = "서울",
            lat = 37.534028,
            lng = 126.901990,
            peak = "22:00 ~ 02:30",
            tip = "먹자골목·오피스 혼합, 주말 심야 강함"
        ),
        Hotspot(
            id = "mullae",
            name = "문래창작촌",
            area = "서울 영등포구",
            city = "서울",
            lat = 37.516637,
            lng = 126.891505,
            peak = "22:00 ~ 02:00",
            tip = "술집·공방 골목, 젊은 층 2차 이동"
        ),
        Hotspot(
            id = "daerim",
            name = "대림역",
            area = "서울 구로구",
            city = "서울",
            lat = 37.492504,
            lng = 126.894961,
            peak = "22:00 ~ 03:00",
            tip = "외국인 상권·먹자골목, 새벽까지 이어짐"
        ),
        Hotspot(
            id = "sadang",
            name = "사당역",
            area = "서울 동작구",
            city = "서울",
            lat = 37.476559,
            lng = 126.981633,
            peak = "22:00 ~ 02:00",
            tip = "강남·사당 이동 거점, 환승 수요 많음"
        ),
        Hotspot(
            id = "isu",
            name = "이수역",
            area = "서울 동작구",
            city = "서울",
            lat = 37.486491,
            lng = 126.981876,
            peak = "22:00 ~ 02:30",
            tip = "먹자골목·유흥가 밀집, 짧은 콜이 많음"
        ),
        Hotspot(
            id = "noryangjin",
            name = "노량진",
            area = "서울 동작구",
            city = "서울",
            lat = 37.514082,
            lng = 126.941687,
            peak = "22:00 ~ 02:00",
            tip = "학원가·수산시장 새벽 수요가 섞여 있음"
        ),
        Hotspot(
            id = "jangseungbaegi",
            name = "장승배기 · 상도",
            area = "서울 동작구",
            city = "서울",
            lat = 37.504905,
            lng = 126.939191,
            peak = "21:30 ~ 01:00",
            tip = "주거·대학가 혼합, 귀가 콜 위주"
        ),
        Hotspot(
            id = "snu",
            name = "서울대입구",
            area = "서울 관악구",
            city = "서울",
            lat = 37.481210,
            lng = 126.952712,
            peak = "22:30 ~ 03:00",
            tip = "대학가·먹자골목, 새벽까지 수요 이어짐"
        ),
        Hotspot(
            id = "sillim",
            name = "신림역",
            area = "서울 관악구",
            city = "서울",
            lat = 37.484269,
            lng = 126.929676,
            peak = "22:30 ~ 03:00",
            tip = "대학가·먹자골목, 새벽까지 수요가 이어짐"
        ),
        Hotspot(
            id = "bongcheon",
            name = "봉천 · 낙성대",
            area = "서울 관악구",
            city = "서울",
            lat = 37.477117,
            lng = 126.963418,
            peak = "22:00 ~ 02:00",
            tip = "대학가 주거지, 짧은 귀가 콜 위주"
        ),
        Hotspot(
            id = "sindorim",
            name = "신도림역",
            area = "서울 구로구",
            city = "서울",
            lat = 37.508810,
            lng = 126.891206,
            peak = "21:30 ~ 01:30",
            tip = "역세권·오피스 수요, 평일 밤 중심"
        ),
        Hotspot(
            id = "guro-dg",
            name = "구로디지털단지",
            area = "서울 구로구",
            city = "서울",
            lat = 37.485215,
            lng = 126.901594,
            peak = "22:00 ~ 01:30",
            tip = "IT 단지 야근·회식 수요로 평일 심야가 강함"
        ),
        Hotspot(
            id = "gaebong",
            name = "개봉 · 구로역",
            area = "서울 구로구",
            city = "서울",
            lat = 37.494698,
            lng = 126.858504,
            peak = "21:30 ~ 01:00",
            tip = "주거·시장 상권, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "gasan",
            name = "가산디지털단지",
            area = "서울 금천구",
            city = "서울",
            lat = 37.480890,
            lng = 126.882574,
            peak = "21:30 ~ 01:00",
            tip = "업무지구 야근 수요, 주말은 조용한 편"
        ),
        Hotspot(
            id = "mokdong",
            name = "목동역",
            area = "서울 양천구",
            city = "서울",
            lat = 37.526072,
            lng = 126.864301,
            peak = "22:00 ~ 01:30",
            tip = "학원가·먹자골목, 밤늦게까지 수요가 남는 편"
        ),
        Hotspot(
            id = "omokgyo",
            name = "오목교 · 목동현대",
            area = "서울 양천구",
            city = "서울",
            lat = 37.524553,
            lng = 126.875050,
            peak = "21:30 ~ 01:30",
            tip = "백화점·학원가 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "hwagok",
            name = "화곡 · 까치산",
            area = "서울 강서구",
            city = "서울",
            lat = 37.541578,
            lng = 126.840436,
            peak = "22:00 ~ 02:00",
            tip = "주거 밀집지역 술집 상권, 귀가 콜 위주"
        ),
        Hotspot(
            id = "magok",
            name = "발산 · 마곡",
            area = "서울 강서구",
            city = "서울",
            lat = 37.558683,
            lng = 126.837595,
            peak = "21:30 ~ 01:00",
            tip = "업무지구 야근 수요, 주말은 조용함"
        ),
        Hotspot(
            id = "gimpo-airport",
            name = "김포공항 · 방화",
            area = "서울 강서구",
            city = "서울",
            lat = 37.562240,
            lng = 126.801396,
            peak = "21:30 ~ 01:00",
            tip = "공항 도착객·주거 수요, 심야 귀가 콜"
        ),
        Hotspot(
            id = "anam",
            name = "안암 · 고려대",
            area = "서울 성북구",
            city = "서울",
            lat = 37.586296,
            lng = 127.029137,
            peak = "22:00 ~ 02:30",
            tip = "대학가 술집 밀집, 평일 밤 수요가 꾸준함"
        ),
        Hotspot(
            id = "seongsin",
            name = "성신여대입구",
            area = "서울 성북구",
            city = "서울",
            lat = 37.592720,
            lng = 127.016544,
            peak = "22:00 ~ 02:30",
            tip = "대학가·먹자골목, 젊은 층 이동 많음"
        ),
        Hotspot(
            id = "gireum",
            name = "길음역",
            area = "서울 성북구",
            city = "서울",
            lat = 37.603245,
            lng = 127.024959,
            peak = "21:30 ~ 01:00",
            tip = "재개발 아파트·주거 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "suyu",
            name = "수유역",
            area = "서울 강북구",
            city = "서울",
            lat = 37.637110,
            lng = 127.024732,
            peak = "22:00 ~ 02:00",
            tip = "대학가·주거 상권이 섞인 구간"
        ),
        Hotspot(
            id = "mia",
            name = "미아사거리",
            area = "서울 강북구",
            city = "서울",
            lat = 37.613251,
            lng = 127.030084,
            peak = "21:30 ~ 01:30",
            tip = "주거·백화점 상권, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "changdong",
            name = "창동역",
            area = "서울 도봉구",
            city = "서울",
            lat = 37.652570,
            lng = 127.048016,
            peak = "22:00 ~ 01:00",
            tip = "주거지 환승 거점, 귀가 콜 위주"
        ),
        Hotspot(
            id = "ssangmun",
            name = "쌍문역",
            area = "서울 도봉구",
            city = "서울",
            lat = 37.648224,
            lng = 127.034387,
            peak = "21:30 ~ 01:00",
            tip = "주거 밀집, 짧은 귀가 콜 비중 높음"
        ),
        Hotspot(
            id = "nowon",
            name = "노원역",
            area = "서울 노원구",
            city = "서울",
            lat = 37.654527,
            lng = 127.060551,
            peak = "22:00 ~ 01:30",
            tip = "주거지 중심, 귀가 콜 비중이 높음"
        ),
        Hotspot(
            id = "sanggye",
            name = "상계 · 중계은행사거리",
            area = "서울 노원구",
            city = "서울",
            lat = 37.660566,
            lng = 127.073202,
            peak = "21:30 ~ 01:00",
            tip = "대단지 아파트·학원가, 귀가 콜 위주"
        ),
        Hotspot(
            id = "gongneung",
            name = "공릉 · 태릉입구",
            area = "서울 노원구",
            city = "서울",
            lat = 37.617908,
            lng = 127.075035,
            peak = "21:30 ~ 01:00",
            tip = "대학가·주거 혼합, 이른 심야 수요"
        ),
        Hotspot(
            id = "pangyo",
            name = "판교역",
            area = "경기 성남시",
            city = "경기",
            lat = 37.394776,
            lng = 127.111160,
            peak = "22:00 ~ 02:00",
            tip = "IT 업무지구 야근·회식 수요, 평일 심야에 강함"
        ),
        Hotspot(
            id = "baekhyeon",
            name = "백현동 카페거리",
            area = "경기 성남시",
            city = "경기",
            lat = 37.384693,
            lng = 127.111232,
            peak = "22:00 ~ 02:00",
            tip = "카페·술집 밀집, 판교 야근객 2차 수요가 많음"
        ),
        Hotspot(
            id = "seohyeon",
            name = "서현역",
            area = "경기 성남시",
            city = "경기",
            lat = 37.384994,
            lng = 127.123402,
            peak = "22:00 ~ 02:00",
            tip = "서현역 먹자골목 중심, 주말 늦은 시간 수요"
        ),
        Hotspot(
            id = "sunae",
            name = "수내역",
            area = "경기 성남시",
            city = "경기",
            lat = 37.378442,
            lng = 127.114230,
            peak = "22:00 ~ 01:30",
            tip = "아파트 밀집지역, 귀가 콜 위주"
        ),
        Hotspot(
            id = "jeongja",
            name = "정자역",
            area = "경기 성남시",
            city = "경기",
            lat = 37.366766,
            lng = 127.108265,
            peak = "22:00 ~ 01:30",
            tip = "정자·수내 주거 수요, 장거리 귀가 콜이 많음"
        ),
        Hotspot(
            id = "migeum",
            name = "미금역",
            area = "경기 성남시",
            city = "경기",
            lat = 37.350078,
            lng = 127.108952,
            peak = "21:30 ~ 01:30",
            tip = "미금 먹자골목·주거 수요, 짧은 귀가 콜"
        ),
        Hotspot(
            id = "ori",
            name = "오리역",
            area = "경기 성남시",
            city = "경기",
            lat = 37.339832,
            lng = 127.108985,
            peak = "21:30 ~ 01:00",
            tip = "구미·정자 주거지 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "yatap",
            name = "야탑역",
            area = "경기 성남시",
            city = "경기",
            lat = 37.411320,
            lng = 127.128674,
            peak = "22:00 ~ 02:00",
            tip = "야탑역 로데오 상권, 주말 심야 수요"
        ),
        Hotspot(
            id = "imae",
            name = "이매 · 모란역",
            area = "경기 성남시",
            city = "경기",
            lat = 37.395846,
            lng = 127.128272,
            peak = "21:30 ~ 01:30",
            tip = "환승·먹자골목 혼합, 이른 심야 수요"
        ),
        Hotspot(
            id = "moran",
            name = "성남 모란",
            area = "경기 성남시",
            city = "경기",
            lat = 37.432124,
            lng = 127.129064,
            peak = "22:00 ~ 03:00",
            tip = "모란역 먹자골목·시장 상권, 새벽까지 이어짐"
        ),
        Hotspot(
            id = "taepyeong",
            name = "성남 태평 · 수진",
            area = "경기 성남시",
            city = "경기",
            lat = 37.439854,
            lng = 127.128035,
            peak = "21:30 ~ 02:00",
            tip = "성남 구도심 술집·시장 상권, 짧은 콜이 많음"
        ),
        Hotspot(
            id = "wirye",
            name = "위례중앙광장",
            area = "경기 성남시",
            city = "경기",
            lat = 37.472785,
            lng = 127.141898,
            peak = "21:30 ~ 01:00",
            tip = "신도시 상가·주거 혼합, 귀가 콜 위주"
        ),
        Hotspot(
            id = "suji",
            name = "수지구청 · 풍덕천",
            area = "경기 용인시",
            city = "경기",
            lat = 37.322380,
            lng = 127.094447,
            peak = "22:00 ~ 02:00",
            tip = "수지구청·구성 상권, 아파트 귀가 콜이 많음"
        ),
        Hotspot(
            id = "seongbok",
            name = "성복역",
            area = "경기 용인시",
            city = "경기",
            lat = 37.313557,
            lng = 127.080341,
            peak = "21:30 ~ 01:30",
            tip = "역 상권·아파트 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "sanghyeon",
            name = "상현역",
            area = "경기 용인시",
            city = "경기",
            lat = 37.296813,
            lng = 127.068980,
            peak = "21:30 ~ 01:30",
            tip = "광교·상현 주거 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "jukjeon",
            name = "죽전역",
            area = "경기 용인시",
            city = "경기",
            lat = 37.324583,
            lng = 127.107398,
            peak = "22:00 ~ 02:00",
            tip = "죽전역 상권·대학가 수요가 섞여 있음"
        ),
        Hotspot(
            id = "bojeong",
            name = "보정동 카페거리",
            area = "경기 용인시",
            city = "경기",
            lat = 37.321600,
            lng = 127.110103,
            peak = "21:30 ~ 01:30",
            tip = "카페·음식점 밀집, 주말 저녁 수요가 많음"
        ),
        Hotspot(
            id = "dongcheon",
            name = "동천역",
            area = "경기 용인시",
            city = "경기",
            lat = 37.337788,
            lng = 127.102832,
            peak = "21:30 ~ 01:00",
            tip = "신축 아파트·상가 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "giheung",
            name = "기흥구청 · 구갈",
            area = "경기 용인시",
            city = "경기",
            lat = 37.280406,
            lng = 127.114659,
            peak = "22:00 ~ 01:30",
            tip = "기흥역 주변 주거·상가 수요"
        ),
        Hotspot(
            id = "dongbaek",
            name = "동백역",
            area = "경기 용인시",
            city = "경기",
            lat = 37.269017,
            lng = 127.152837,
            peak = "22:00 ~ 01:30",
            tip = "동백지구 상가·주거 혼합, 귀가 콜 위주"
        ),
        Hotspot(
            id = "yeokbuk",
            name = "역북 · 명지대",
            area = "경기 용인시",
            city = "경기",
            lat = 37.237953,
            lng = 127.187413,
            peak = "22:00 ~ 01:30",
            tip = "역북·김량장 상권, 시내 귀가 수요"
        ),
        Hotspot(
            id = "guseong",
            name = "구성역",
            area = "경기 용인시",
            city = "경기",
            lat = 37.299013,
            lng = 127.105664,
            peak = "21:30 ~ 01:00",
            tip = "아파트·오피스 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "singal",
            name = "용인 신갈",
            area = "경기 용인시",
            city = "경기",
            lat = 37.282795,
            lng = 127.103571,
            peak = "22:00 ~ 02:00",
            tip = "신갈오거리 유흥·먹자골목, 시외 이동 콜도 많음"
        ),
        Hotspot(
            id = "suwon-ingye",
            name = "수원 인계동 · 나혜석거리",
            area = "경기 수원시",
            city = "경기",
            lat = 37.264229,
            lng = 127.033279,
            peak = "22:30 ~ 03:00",
            tip = "수원 대표 유흥가, 새벽까지 이어지는 편"
        ),
        Hotspot(
            id = "suwon-station",
            name = "수원역",
            area = "경기 수원시",
            city = "경기",
            lat = 37.266440,
            lng = 126.999408,
            peak = "21:00 ~ 01:00",
            tip = "역 주변 술집·모텔 수요, 짧은 콜 많음"
        ),
        Hotspot(
            id = "gwanggyo",
            name = "광교중앙역 · 아브뉴프랑",
            area = "경기 수원시",
            city = "경기",
            lat = 37.288412,
            lng = 127.051658,
            peak = "21:30 ~ 01:00",
            tip = "광교중앙 상권·아브뉴프랑 몰, 주말 2차 이동이 많음"
        ),
        Hotspot(
            id = "ajou",
            name = "아주대삼거리",
            area = "경기 수원시",
            city = "경기",
            lat = 37.282019,
            lng = 127.046341,
            peak = "22:00 ~ 02:30",
            tip = "대학가·병원 수요, 심야 귀가 콜 꾸준함"
        ),
        Hotspot(
            id = "yeongtong",
            name = "영통역",
            area = "경기 수원시",
            city = "경기",
            lat = 37.251521,
            lng = 127.071340,
            peak = "22:00 ~ 01:30",
            tip = "영통역 먹자골목, 대학가 수요도 있음"
        ),
        Hotspot(
            id = "mangpo",
            name = "망포역",
            area = "경기 수원시",
            city = "경기",
            lat = 37.245807,
            lng = 127.057375,
            peak = "22:00 ~ 01:30",
            tip = "망포역 주변 상가·주거 수요"
        ),
        Hotspot(
            id = "maetan",
            name = "매탄 · 권선",
            area = "경기 수원시",
            city = "경기",
            lat = 37.261725,
            lng = 127.048939,
            peak = "21:30 ~ 01:00",
            tip = "주거·오피스 혼합, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "hwaseo",
            name = "화서역",
            area = "경기 수원시",
            city = "경기",
            lat = 37.283085,
            lng = 126.994234,
            peak = "21:30 ~ 01:00",
            tip = "역 상권·아파트 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "homaesil",
            name = "호매실",
            area = "경기 수원시",
            city = "경기",
            lat = 37.262493,
            lng = 126.953036,
            peak = "21:30 ~ 01:00",
            tip = "택지지구 상가 수요, 신도시 귀가 콜"
        ),
        Hotspot(
            id = "haenggung",
            name = "수원 행궁동",
            area = "경기 수원시",
            city = "경기",
            lat = 37.287120,
            lng = 127.011938,
            peak = "21:30 ~ 01:30",
            tip = "행궁동 카페·술집 골목, 주말 관광 수요"
        ),
        Hotspot(
            id = "dongtan-nam",
            name = "동탄1 남광장",
            area = "경기 화성시",
            city = "경기",
            lat = 37.200645,
            lng = 127.073532,
            peak = "22:30 ~ 03:00",
            tip = "동탄 대표 유흥가, 남광장 일대 새벽 콜이 많음"
        ),
        Hotspot(
            id = "dongtan-buk",
            name = "동탄1 북광장 · 메타폴리스",
            area = "경기 화성시",
            city = "경기",
            lat = 37.205000,
            lng = 127.068889,
            peak = "22:00 ~ 02:30",
            tip = "북광장 먹자골목·오피스 수요, 주말 심야 강함"
        ),
        Hotspot(
            id = "dongtan2",
            name = "동탄역",
            area = "경기 화성시",
            city = "경기",
            lat = 37.199327,
            lng = 127.097018,
            peak = "22:00 ~ 02:00",
            tip = "SRT 도착객·신도시 상가 수요, 장거리 귀가 콜"
        ),
        Hotspot(
            id = "dongtan-cheonggye",
            name = "동탄2 청계동",
            area = "경기 화성시",
            city = "경기",
            lat = 37.195677,
            lng = 127.111893,
            peak = "21:30 ~ 01:30",
            tip = "신축 아파트·상가 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "dongtan-yeongcheon",
            name = "동탄2 영천동",
            area = "경기 화성시",
            city = "경기",
            lat = 37.210601,
            lng = 127.106184,
            peak = "21:30 ~ 01:00",
            tip = "동탄2 주거지, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "byeongjeom",
            name = "화성 병점",
            area = "경기 화성시",
            city = "경기",
            lat = 37.206821,
            lng = 127.033268,
            peak = "22:00 ~ 01:30",
            tip = "병점역 먹자골목, 주거지 귀가 수요"
        ),
        Hotspot(
            id = "hyangnam",
            name = "화성 향남",
            area = "경기 화성시",
            city = "경기",
            lat = 37.114561,
            lng = 126.927045,
            peak = "21:30 ~ 01:00",
            tip = "향남읍 중심 상권, 인근 산단 야근 수요도 있음"
        ),
        Hotspot(
            id = "bongdam",
            name = "화성 봉담",
            area = "경기 화성시",
            city = "경기",
            lat = 37.203897,
            lng = 126.939222,
            peak = "21:30 ~ 01:00",
            tip = "대학가·택지 상가 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "osan",
            name = "오산역",
            area = "경기 오산시",
            city = "경기",
            lat = 37.145810,
            lng = 127.067125,
            peak = "22:00 ~ 02:00",
            tip = "오산역 먹자골목, 미군기지 인접 수요도 있음"
        ),
        Hotspot(
            id = "osan-unam",
            name = "오산 운암 · 세교",
            area = "경기 오산시",
            city = "경기",
            lat = 37.187584,
            lng = 127.039840,
            peak = "21:30 ~ 01:00",
            tip = "세교신도시 상가·아파트 수요, 귀가 콜"
        ),
        Hotspot(
            id = "pyeongtaek-station",
            name = "평택역",
            area = "경기 평택시",
            city = "경기",
            lat = 36.991145,
            lng = 127.084981,
            peak = "22:00 ~ 02:00",
            tip = "평택역 먹자골목, 미군 주둔지 인접 수요도 있음"
        ),
        Hotspot(
            id = "songtan",
            name = "평택 송탄",
            area = "경기 평택시",
            city = "경기",
            lat = 37.075533,
            lng = 127.054377,
            peak = "22:00 ~ 02:00",
            tip = "송탄역 상권·미군부대 인근 유흥 수요"
        ),
        Hotspot(
            id = "godeok",
            name = "평택 고덕",
            area = "경기 평택시",
            city = "경기",
            lat = 37.043953,
            lng = 127.039396,
            peak = "21:30 ~ 01:00",
            tip = "고덕국제신도시 상가, 귀가 콜 위주"
        ),
        Hotspot(
            id = "anjoong",
            name = "평택 안중",
            area = "경기 평택시",
            city = "경기",
            lat = 36.990889,
            lng = 126.936634,
            peak = "21:00 ~ 24:00",
            tip = "안중 시내 상권, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "pyeongtaek-bijeon",
            name = "평택 비전동",
            area = "경기 평택시",
            city = "경기",
            lat = 36.999832,
            lng = 127.104418,
            peak = "22:00 ~ 01:30",
            tip = "평택 시내 유흥·먹자골목, 짧은 콜이 많음"
        ),
        Hotspot(
            id = "pyeongtaek-sosabeol",
            name = "평택 소사벌",
            area = "경기 평택시",
            city = "경기",
            lat = 36.995246,
            lng = 127.116916,
            peak = "21:30 ~ 01:00",
            tip = "택지지구 상가 수요, 아파트 귀가 콜"
        ),
        Hotspot(
            id = "beomgye",
            name = "범계역",
            area = "경기 안양시",
            city = "경기",
            lat = 37.389854,
            lng = 126.950627,
            peak = "22:00 ~ 02:00",
            tip = "범계역 로데오거리 중심 콜밭"
        ),
        Hotspot(
            id = "pyeongchon",
            name = "평촌역",
            area = "경기 안양시",
            city = "경기",
            lat = 37.394758,
            lng = 126.964667,
            peak = "22:00 ~ 02:00",
            tip = "평촌 학원가·먹자골목, 밤늦게까지 수요"
        ),
        Hotspot(
            id = "anyang-ilbeonga",
            name = "안양 1번가",
            area = "경기 안양시",
            city = "경기",
            lat = 37.400356,
            lng = 126.922379,
            peak = "22:00 ~ 02:00",
            tip = "안양역 앞 1번가 상권, 심야 유동 인구 많음"
        ),
        Hotspot(
            id = "indukwon",
            name = "인덕원역",
            area = "경기 안양시",
            city = "경기",
            lat = 37.400807,
            lng = 126.976384,
            peak = "21:30 ~ 01:30",
            tip = "환승 거점·주거 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "gwacheon-cheongsa",
            name = "과천정부청사",
            area = "경기 과천시",
            city = "경기",
            lat = 37.426482,
            lng = 126.990021,
            peak = "21:00 ~ 24:00",
            tip = "공무원 회식·주거 수요, 이른 심야 중심"
        ),
        Hotspot(
            id = "gwacheon-byeolyang",
            name = "과천 별양동",
            area = "경기 과천시",
            city = "경기",
            lat = 37.427820,
            lng = 126.995664,
            peak = "21:30 ~ 01:00",
            tip = "아파트·상가 수요, 짧은 귀가 콜"
        ),
        Hotspot(
            id = "sanbon",
            name = "군포 산본",
            area = "경기 군포시",
            city = "경기",
            lat = 37.358019,
            lng = 126.932969,
            peak = "22:00 ~ 02:00",
            tip = "산본역 로데오거리, 주말 심야 수요"
        ),
        Hotspot(
            id = "gunpo-yeok",
            name = "군포역 · 당동",
            area = "경기 군포시",
            city = "경기",
            lat = 37.354006,
            lng = 126.948539,
            peak = "21:30 ~ 01:00",
            tip = "역 상권·주거 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "naeson",
            name = "의왕 내손",
            area = "경기 의왕시",
            city = "경기",
            lat = 37.377414,
            lng = 126.983920,
            peak = "21:30 ~ 01:00",
            tip = "택지지구 상가·아파트 수요, 귀가 콜"
        ),
        Hotspot(
            id = "bucheon-station",
            name = "부천역",
            area = "경기 부천시",
            city = "경기",
            lat = 37.484085,
            lng = 126.782803,
            peak = "22:00 ~ 02:30",
            tip = "부천역 먹자골목, 젊은 층 심야 이동 많음"
        ),
        Hotspot(
            id = "sangdong",
            name = "부천 상동",
            area = "경기 부천시",
            city = "경기",
            lat = 37.505814,
            lng = 126.753163,
            peak = "22:00 ~ 02:00",
            tip = "상동역 로데오·영화관 상권, 주말 심야 수요"
        ),
        Hotspot(
            id = "jungdong",
            name = "부천 중동 · 신중동",
            area = "경기 부천시",
            city = "경기",
            lat = 37.503064,
            lng = 126.775977,
            peak = "22:00 ~ 02:00",
            tip = "중동 신도시 상가·유흥 수요가 섞임"
        ),
        Hotspot(
            id = "songnae",
            name = "부천 송내",
            area = "경기 부천시",
            city = "경기",
            lat = 37.487626,
            lng = 126.753045,
            peak = "21:30 ~ 01:30",
            tip = "역 상권·주거 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "wonjong",
            name = "부천 원종 · 오정",
            area = "경기 부천시",
            city = "경기",
            lat = 37.526987,
            lng = 126.804118,
            peak = "21:30 ~ 01:00",
            tip = "산단 배후 주거 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "yeokgok",
            name = "부천 역곡",
            area = "경기 부천시",
            city = "경기",
            lat = 37.485140,
            lng = 126.811622,
            peak = "22:00 ~ 02:00",
            tip = "대학가·원룸촌 수요, 심야 짧은 콜이 많음"
        ),
        Hotspot(
            id = "cheolsan",
            name = "철산역",
            area = "경기 광명시",
            city = "경기",
            lat = 37.479417,
            lng = 126.868923,
            peak = "22:00 ~ 01:30",
            tip = "철산 상권·아파트 수요, 짧은 귀가 콜"
        ),
        Hotspot(
            id = "gwangmyeong-sageori",
            name = "광명사거리",
            area = "경기 광명시",
            city = "경기",
            lat = 37.479307,
            lng = 126.854675,
            peak = "21:30 ~ 01:30",
            tip = "전통시장·주거 상권, 이른 심야 수요"
        ),
        Hotspot(
            id = "gwangmyeong-stn",
            name = "광명역(KTX)",
            area = "경기 광명시",
            city = "경기",
            lat = 37.416522,
            lng = 126.884789,
            peak = "21:00 ~ 24:00",
            tip = "KTX 도착객 귀가 콜, 막차 시간대 집중"
        ),
        Hotspot(
            id = "jeongwang",
            name = "시흥 정왕",
            area = "경기 시흥시",
            city = "경기",
            lat = 37.351816,
            lng = 126.742844,
            peak = "22:00 ~ 02:00",
            tip = "정왕역·산단 배후 상권, 야근·귀가 수요"
        ),
        Hotspot(
            id = "sihwa",
            name = "시흥 시화",
            area = "경기 시흥시",
            city = "경기",
            lat = 37.340772,
            lng = 126.755971,
            peak = "21:30 ~ 01:30",
            tip = "산단 야근·외국인 상권, 심야 수요 꾸준함"
        ),
        Hotspot(
            id = "baegot",
            name = "시흥 배곧",
            area = "경기 시흥시",
            city = "경기",
            lat = 37.370150,
            lng = 126.723792,
            peak = "21:30 ~ 01:00",
            tip = "배곧신도시 상가·대학가 수요"
        ),
        Hotspot(
            id = "eungye",
            name = "시흥 은계지구",
            area = "경기 시흥시",
            city = "경기",
            lat = 37.438970,
            lng = 126.803477,
            peak = "21:30 ~ 01:00",
            tip = "신도시 상가·아파트 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "daeya",
            name = "시흥 대야역",
            area = "경기 시흥시",
            city = "경기",
            lat = 37.450523,
            lng = 126.794389,
            peak = "21:30 ~ 01:00",
            tip = "역 상권·주거 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "ansan-jungang",
            name = "안산 중앙동",
            area = "경기 안산시",
            city = "경기",
            lat = 37.316055,
            lng = 126.838532,
            peak = "22:00 ~ 02:30",
            tip = "안산 중앙역 로데오거리, 외국인 수요도 많음"
        ),
        Hotspot(
            id = "gojan",
            name = "안산 고잔 · 한대앞",
            area = "경기 안산시",
            city = "경기",
            lat = 37.309685,
            lng = 126.853656,
            peak = "22:00 ~ 02:00",
            tip = "고잔역 상권·대학가 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "seonbu",
            name = "안산 선부동",
            area = "경기 안산시",
            city = "경기",
            lat = 37.343576,
            lng = 126.806032,
            peak = "21:30 ~ 01:00",
            tip = "주거 밀집·외국인 상권, 짧은 귀가 콜"
        ),
        Hotspot(
            id = "ansan-wongok",
            name = "안산 원곡동",
            area = "경기 안산시",
            city = "경기",
            lat = 37.331284,
            lng = 126.793294,
            peak = "22:00 ~ 02:00",
            tip = "다문화 상권·산단 배후, 심야 유동 인구 많음"
        ),
        Hotspot(
            id = "ansan-choji",
            name = "안산 초지 · 단원",
            area = "경기 안산시",
            city = "경기",
            lat = 37.320583,
            lng = 126.806151,
            peak = "21:30 ~ 01:30",
            tip = "역 상권·아파트 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "ilsan",
            name = "일산 라페스타 · 정발산",
            area = "경기 고양시",
            city = "경기",
            lat = 37.660709,
            lng = 126.768959,
            peak = "22:00 ~ 02:00",
            tip = "라페스타·웨스턴돔 유흥 수요"
        ),
        Hotspot(
            id = "western-dome",
            name = "일산 웨스턴돔",
            area = "경기 고양시",
            city = "경기",
            lat = 37.654776,
            lng = 126.772642,
            peak = "22:30 ~ 03:00",
            tip = "클럽·바 밀집, 주말 새벽까지 이어짐"
        ),
        Hotspot(
            id = "baekseok",
            name = "백석 · 벨라시타",
            area = "경기 고양시",
            city = "경기",
            lat = 37.642443,
            lng = 126.792729,
            peak = "22:00 ~ 02:00",
            tip = "벨라시타 상권·오피스 수요, 주말 심야"
        ),
        Hotspot(
            id = "daehwa",
            name = "대화 · 킨텍스",
            area = "경기 고양시",
            city = "경기",
            lat = 37.668987,
            lng = 126.745887,
            peak = "21:30 ~ 01:00",
            tip = "전시장 행사·주거 수요, 행사일 귀가 콜 몰림"
        ),
        Hotspot(
            id = "hwajeong",
            name = "고양 화정",
            area = "경기 고양시",
            city = "경기",
            lat = 37.637366,
            lng = 126.834676,
            peak = "22:00 ~ 02:00",
            tip = "화정역 먹자골목·학원가, 밤늦게까지 수요"
        ),
        Hotspot(
            id = "samsong",
            name = "삼송 · 원흥",
            area = "경기 고양시",
            city = "경기",
            lat = 37.653096,
            lng = 126.895559,
            peak = "21:30 ~ 01:00",
            tip = "신도시 상가·아파트 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "wondang",
            name = "고양 원당",
            area = "경기 고양시",
            city = "경기",
            lat = 37.653328,
            lng = 126.843006,
            peak = "21:30 ~ 01:00",
            tip = "구도심 상권·주거 수요, 이른 심야"
        ),
        Hotspot(
            id = "haengsin",
            name = "고양 행신",
            area = "경기 고양시",
            city = "경기",
            lat = 37.611846,
            lng = 126.834059,
            peak = "21:30 ~ 01:00",
            tip = "아파트 밀집·역 상권, 귀가 콜 위주"
        ),
        Hotspot(
            id = "unjeong",
            name = "파주 운정",
            area = "경기 파주시",
            city = "경기",
            lat = 37.725402,
            lng = 126.767152,
            peak = "21:30 ~ 01:00",
            tip = "운정신도시 상가·주거 수요"
        ),
        Hotspot(
            id = "yadang",
            name = "야당역",
            area = "경기 파주시",
            city = "경기",
            lat = 37.712749,
            lng = 126.761535,
            peak = "21:30 ~ 01:00",
            tip = "신도시 상가·아파트 수요, 귀가 콜"
        ),
        Hotspot(
            id = "geumchon",
            name = "파주 금촌",
            area = "경기 파주시",
            city = "경기",
            lat = 37.766106,
            lng = 126.774515,
            peak = "22:00 ~ 01:30",
            tip = "금촌역 전통 상권, 귀가 콜 위주"
        ),
        Hotspot(
            id = "munsan",
            name = "파주 문산",
            area = "경기 파주시",
            city = "경기",
            lat = 37.874229,
            lng = 126.780383,
            peak = "21:00 ~ 24:00",
            tip = "문산 시내 상권·군부대 수요, 이른 심야"
        ),
        Hotspot(
            id = "gurae",
            name = "김포 구래",
            area = "경기 김포시",
            city = "경기",
            lat = 37.645397,
            lng = 126.628708,
            peak = "21:30 ~ 01:00",
            tip = "구래역 상권·신도시 주거 수요"
        ),
        Hotspot(
            id = "raveniche",
            name = "김포 라베니체",
            area = "경기 김포시",
            city = "경기",
            lat = 37.638750,
            lng = 126.678146,
            peak = "22:00 ~ 02:00",
            tip = "라베니체 상가·유흥 수요, 주말 심야에 강함"
        ),
        Hotspot(
            id = "pungmu",
            name = "김포 풍무 · 사우",
            area = "경기 김포시",
            city = "경기",
            lat = 37.619120,
            lng = 126.726089,
            peak = "21:30 ~ 01:00",
            tip = "김포 구도심 상권·주거 수요, 귀가 콜"
        ),
        Hotspot(
            id = "gocheon",
            name = "김포 고촌",
            area = "경기 김포시",
            city = "경기",
            lat = 37.606502,
            lng = 126.762868,
            peak = "21:30 ~ 01:00",
            tip = "서울 경계 신도시 상가 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "uijeongbu",
            name = "의정부역 · 로데오",
            area = "경기 의정부시",
            city = "경기",
            lat = 37.738729,
            lng = 127.045891,
            peak = "22:00 ~ 03:00",
            tip = "의정부역 주변 유흥가, 미군 인접 수요도 있음"
        ),
        Hotspot(
            id = "minrak",
            name = "의정부 민락2지구",
            area = "경기 의정부시",
            city = "경기",
            lat = 37.746676,
            lng = 127.112240,
            peak = "21:30 ~ 01:00",
            tip = "신도시 상가·아파트 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "howon",
            name = "의정부 호원 · 회룡",
            area = "경기 의정부시",
            city = "경기",
            lat = 37.724522,
            lng = 127.047203,
            peak = "21:30 ~ 01:00",
            tip = "역 상권·주거 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "guri",
            name = "구리역 · 돌다리",
            area = "경기 구리시",
            city = "경기",
            lat = 37.603306,
            lng = 127.143293,
            peak = "22:00 ~ 02:00",
            tip = "구리역 상권·전통시장 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "galmae",
            name = "구리 갈매",
            area = "경기 구리시",
            city = "경기",
            lat = 37.634827,
            lng = 127.115662,
            peak = "21:30 ~ 01:00",
            tip = "신도시 상가·아파트 수요, 귀가 콜"
        ),
        Hotspot(
            id = "dasan",
            name = "남양주 다산역",
            area = "경기 남양주시",
            city = "경기",
            lat = 37.624165,
            lng = 127.149707,
            peak = "21:30 ~ 01:00",
            tip = "다산신도시 상가·주거 혼합 수요"
        ),
        Hotspot(
            id = "byeollae",
            name = "남양주 별내역",
            area = "경기 남양주시",
            city = "경기",
            lat = 37.641999,
            lng = 127.127726,
            peak = "21:30 ~ 01:00",
            tip = "별내신도시 상가 수요, 서울 경계 귀가 콜"
        ),
        Hotspot(
            id = "pyeongnae",
            name = "평내호평역",
            area = "경기 남양주시",
            city = "경기",
            lat = 37.653229,
            lng = 127.244452,
            peak = "21:30 ~ 01:00",
            tip = "역 상권·아파트 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "jinjeop",
            name = "남양주 진접",
            area = "경기 남양주시",
            city = "경기",
            lat = 37.719823,
            lng = 127.203248,
            peak = "21:30 ~ 01:00",
            tip = "신도시 상가·주거 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "hwado",
            name = "남양주 화도 · 마석",
            area = "경기 남양주시",
            city = "경기",
            lat = 37.652845,
            lng = 127.315987,
            peak = "21:00 ~ 24:00",
            tip = "마석 시내 상권, 관광지 귀가 수요도 있음"
        ),
        Hotspot(
            id = "misa",
            name = "하남 미사역",
            area = "경기 하남시",
            city = "경기",
            lat = 37.563118,
            lng = 127.192932,
            peak = "21:30 ~ 01:00",
            tip = "미사강변 상가·주거 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "hanam-cityhall",
            name = "하남시청 · 덕풍",
            area = "경기 하남시",
            city = "경기",
            lat = 37.541967,
            lng = 127.206298,
            peak = "21:30 ~ 01:00",
            tip = "구도심 상권·주거 수요, 짧은 귀가 콜"
        ),
        Hotspot(
            id = "gamil",
            name = "하남 감일",
            area = "경기 하남시",
            city = "경기",
            lat = 37.504665,
            lng = 127.155605,
            peak = "21:30 ~ 01:00",
            tip = "신도시 상가·아파트 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "gwangju-gg",
            name = "경기광주 경안동",
            area = "경기 광주시",
            city = "경기",
            lat = 37.410491,
            lng = 127.252835,
            peak = "21:30 ~ 01:00",
            tip = "경안동 시내 상권·주거 수요"
        ),
        Hotspot(
            id = "gwangju-yeokdong",
            name = "경기광주 역동 · 터미널",
            area = "경기 광주시",
            city = "경기",
            lat = 37.409032,
            lng = 127.261596,
            peak = "21:30 ~ 01:00",
            tip = "터미널·역 상권 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "gonjiam",
            name = "광주 곤지암",
            area = "경기 광주시",
            city = "경기",
            lat = 37.363958,
            lng = 127.382484,
            peak = "21:00 ~ 24:00",
            tip = "곤지암 시내 상권, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "icheon",
            name = "이천 중앙통",
            area = "경기 이천시",
            city = "경기",
            lat = 37.272246,
            lng = 127.435045,
            peak = "21:30 ~ 01:00",
            tip = "이천 시내 중심 상권, 산단 야근 수요도 있음"
        ),
        Hotspot(
            id = "icheon-terminal",
            name = "이천 터미널",
            area = "경기 이천시",
            city = "경기",
            lat = 37.277744,
            lng = 127.446913,
            peak = "21:30 ~ 01:00",
            tip = "터미널·먹자골목 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "icheon-bubal",
            name = "이천 부발",
            area = "경기 이천시",
            city = "경기",
            lat = 37.273148,
            lng = 127.507007,
            peak = "21:00 ~ 24:00",
            tip = "산단 배후 상권, 야근·귀가 수요"
        ),
        Hotspot(
            id = "yeoju-cityhall",
            name = "여주 시청",
            area = "경기 여주시",
            city = "경기",
            lat = 37.298217,
            lng = 127.637171,
            peak = "21:00 ~ 24:00",
            tip = "여주 시내 상권, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "yeoju-station",
            name = "여주역",
            area = "경기 여주시",
            city = "경기",
            lat = 37.282141,
            lng = 127.628580,
            peak = "21:00 ~ 24:00",
            tip = "역 도착객·주거 수요, 막차 시간대 집중"
        ),
        Hotspot(
            id = "anseong",
            name = "안성 중앙로",
            area = "경기 안성시",
            city = "경기",
            lat = 37.010096,
            lng = 127.260047,
            peak = "21:30 ~ 01:00",
            tip = "안성 시내 상권·대학가 수요, 귀가 콜"
        ),
        Hotspot(
            id = "okjeong",
            name = "양주 옥정",
            area = "경기 양주시",
            city = "경기",
            lat = 37.823636,
            lng = 127.089073,
            peak = "21:30 ~ 01:00",
            tip = "옥정신도시 상가·아파트 수요, 귀가 콜"
        ),
        Hotspot(
            id = "dongducheon",
            name = "동두천 중앙로",
            area = "경기 동두천시",
            city = "경기",
            lat = 37.902154,
            lng = 127.058796,
            peak = "21:30 ~ 01:00",
            tip = "시내 상권·군부대 수요, 이른 심야"
        ),
        Hotspot(
            id = "pocheon",
            name = "포천 신읍동",
            area = "경기 포천시",
            city = "경기",
            lat = 37.900196,
            lng = 127.184774,
            peak = "21:00 ~ 24:00",
            tip = "포천 시내 상권, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "gapyeong",
            name = "가평",
            area = "경기 가평군",
            city = "경기",
            lat = 37.828846,
            lng = 127.487526,
            peak = "21:00 ~ 02:00",
            tip = "관광지·펜션 귀가 콜, 주말 심야 장거리 많음"
        ),
        Hotspot(
            id = "yangpyeong",
            name = "양평",
            area = "경기 양평군",
            city = "경기",
            lat = 37.493553,
            lng = 127.514071,
            peak = "21:00 ~ 24:00",
            tip = "양평 시내 상권·관광 수요, 이른 심야"
        ),
        Hotspot(
            id = "bupyeong",
            name = "부평역",
            area = "인천 부평구",
            city = "인천",
            lat = 37.489481,
            lng = 126.724517,
            peak = "22:00 ~ 03:00",
            tip = "부평역 먹자골목, 인천 최대 심야 콜밭 중 하나"
        ),
        Hotspot(
            id = "bupyeong-gucheong",
            name = "부평구청 · 갈산",
            area = "인천 부평구",
            city = "인천",
            lat = 37.496673,
            lng = 126.721209,
            peak = "22:00 ~ 02:00",
            tip = "구청·주거 상권, 짧은 귀가 콜이 많음"
        ),
        Hotspot(
            id = "sangok",
            name = "부평 삼산 · 상동",
            area = "인천 부평구",
            city = "인천",
            lat = 37.506483,
            lng = 126.742024,
            peak = "21:30 ~ 01:30",
            tip = "대형 아파트·상가 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "guwol",
            name = "구월동 로데오",
            area = "인천 남동구",
            city = "인천",
            lat = 37.448594,
            lng = 126.711134,
            peak = "22:00 ~ 03:00",
            tip = "구월동 로데오거리, 인천 대표 유흥가"
        ),
        Hotspot(
            id = "incheon-terminal",
            name = "인천터미널 · 예술회관",
            area = "인천 남동구",
            city = "인천",
            lat = 37.441328,
            lng = 126.701052,
            peak = "21:30 ~ 01:30",
            tip = "터미널·백화점·오피스 수요가 겹치는 구간"
        ),
        Hotspot(
            id = "mansu",
            name = "만수동",
            area = "인천 남동구",
            city = "인천",
            lat = 37.454941,
            lng = 126.735096,
            peak = "21:30 ~ 01:30",
            tip = "주거 밀집·학원가 수요, 짧은 귀가 콜"
        ),
        Hotspot(
            id = "namdong",
            name = "남동공단",
            area = "인천 남동구",
            city = "인천",
            lat = 37.398914,
            lng = 126.707396,
            peak = "21:30 ~ 01:00",
            tip = "산단 야근 수요, 평일 심야 중심"
        ),
        Hotspot(
            id = "nonhyeon-ic",
            name = "인천 논현",
            area = "인천 남동구",
            city = "인천",
            lat = 37.404671,
            lng = 126.724735,
            peak = "22:00 ~ 02:00",
            tip = "논현역·남동공단 배후 수요, 야근 콜도 있음"
        ),
        Hotspot(
            id = "songdo",
            name = "송도 센트럴파크",
            area = "인천 연수구",
            city = "인천",
            lat = 37.392262,
            lng = 126.639689,
            peak = "21:30 ~ 01:30",
            tip = "주거·업무 혼합, 귀가 콜 위주"
        ),
        Hotspot(
            id = "songdo-inu",
            name = "인천대입구",
            area = "인천 연수구",
            city = "인천",
            lat = 37.386196,
            lng = 126.639404,
            peak = "22:00 ~ 02:00",
            tip = "대학가·원룸촌 수요, 심야 짧은 콜 많음"
        ),
        Hotspot(
            id = "songdo-canal",
            name = "송도 커낼워크",
            area = "인천 연수구",
            city = "인천",
            lat = 37.400869,
            lng = 126.638310,
            peak = "22:00 ~ 02:00",
            tip = "상가·레스토랑 밀집, 주말 심야 수요"
        ),
        Hotspot(
            id = "songdo-triple",
            name = "송도 트리플스트리트",
            area = "인천 연수구",
            city = "인천",
            lat = 37.378646,
            lng = 126.662747,
            peak = "21:30 ~ 01:30",
            tip = "쇼핑몰·영화관 상권, 귀가 콜 위주"
        ),
        Hotspot(
            id = "yeonsu",
            name = "연수역",
            area = "인천 연수구",
            city = "인천",
            lat = 37.418569,
            lng = 126.679674,
            peak = "22:00 ~ 01:30",
            tip = "연수역 상권·주거 수요"
        ),
        Hotspot(
            id = "woninje",
            name = "원인재역",
            area = "인천 연수구",
            city = "인천",
            lat = 37.412757,
            lng = 126.687129,
            peak = "21:30 ~ 01:00",
            tip = "환승 거점·주거 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "cheongna",
            name = "청라 커낼웨이",
            area = "인천 서구",
            city = "인천",
            lat = 37.533016,
            lng = 126.651575,
            peak = "22:00 ~ 02:00",
            tip = "청라 상가·유흥 수요, 주말 심야 강함"
        ),
        Hotspot(
            id = "geomdan",
            name = "검단 · 완정역",
            area = "인천 서구",
            city = "인천",
            lat = 37.592865,
            lng = 126.673030,
            peak = "21:30 ~ 01:00",
            tip = "검단신도시 상가·아파트 수요, 귀가 콜"
        ),
        Hotspot(
            id = "juan",
            name = "주안역",
            area = "인천 미추홀구",
            city = "인천",
            lat = 37.456976,
            lng = 126.680996,
            peak = "22:00 ~ 03:00",
            tip = "주안역 먹자골목·유흥가, 새벽까지 이어짐"
        ),
        Hotspot(
            id = "songnim",
            name = "송림동 · 동산",
            area = "인천 미추홀구",
            city = "인천",
            lat = 37.478573,
            lng = 126.650890,
            peak = "21:30 ~ 01:00",
            tip = "구도심 상권·시장 수요, 이른 심야 귀가 콜"
        ),
        Hotspot(
            id = "dongincheon",
            name = "동인천역",
            area = "인천 중구",
            city = "인천",
            lat = 37.475414,
            lng = 126.632598,
            peak = "22:00 ~ 02:00",
            tip = "역 상권·숙소 수요, 짧은 콜이 많음"
        ),
        Hotspot(
            id = "sinpo",
            name = "신포동 · 차이나타운",
            area = "인천 중구",
            city = "인천",
            lat = 37.471079,
            lng = 126.626683,
            peak = "22:00 ~ 02:30",
            tip = "관광·먹자골목, 주말 심야 수요"
        ),
        Hotspot(
            id = "gyeyang",
            name = "계양 · 작전역",
            area = "인천 계양구",
            city = "인천",
            lat = 37.530279,
            lng = 126.722516,
            peak = "22:00 ~ 01:30",
            tip = "계양역 환승 거점, 귀가 콜 위주"
        ),
        Hotspot(
            id = "yeongjong",
            name = "영종 하늘도시",
            area = "인천 중구",
            city = "인천",
            lat = 37.494082,
            lng = 126.536367,
            peak = "21:30 ~ 01:00",
            tip = "공항 배후 신도시, 귀가 콜·공항 이동 수요"
        ),
        Hotspot(
            id = "seomyeon",
            name = "부산 서면",
            area = "부산 부산진구",
            city = "부산",
            lat = 35.157800,
            lng = 129.059500,
            peak = "22:30 ~ 04:00",
            tip = "부산 최대 유흥가, 새벽 콜 비중 높음"
        ),
        Hotspot(
            id = "haeundae",
            name = "부산 해운대",
            area = "부산 해운대구",
            city = "부산",
            lat = 35.163100,
            lng = 129.163600,
            peak = "22:00 ~ 03:00",
            tip = "해운대 해변로·먹자골목, 관광 시즌 수요 증가"
        ),
        Hotspot(
            id = "gwangalli",
            name = "부산 광안리",
            area = "부산 수영구",
            city = "부산",
            lat = 35.153100,
            lng = 129.118600,
            peak = "22:00 ~ 03:00",
            tip = "광안리 해변 상권, 주말 심야에 강함"
        ),
        Hotspot(
            id = "nampo",
            name = "부산 남포동 · 부산역",
            area = "부산 중구",
            city = "부산",
            lat = 35.099000,
            lng = 129.034000,
            peak = "22:00 ~ 03:00",
            tip = "영화의거리·자갈치 상권, 관광객 심야 수요"
        ),
        Hotspot(
            id = "gyeongseong",
            name = "부산 경성대 · 부경대",
            area = "부산 남구",
            city = "부산",
            lat = 35.137300,
            lng = 129.100500,
            peak = "22:00 ~ 03:00",
            tip = "대학가 먹자골목, 젊은 층 심야 이동 많음"
        ),
        Hotspot(
            id = "sasang",
            name = "부산 사상",
            area = "부산 사상구",
            city = "부산",
            lat = 35.162700,
            lng = 128.985300,
            peak = "21:30 ~ 01:30",
            tip = "사상역 터미널·먹자골목 수요"
        ),
        Hotspot(
            id = "yeonsan",
            name = "부산 연산 · 교대",
            area = "부산 연제구",
            city = "부산",
            lat = 35.186500,
            lng = 129.081000,
            peak = "22:00 ~ 02:30",
            tip = "연산역 로데오·법원 상권, 회식 수요"
        ),
        Hotspot(
            id = "hadan",
            name = "부산 하단",
            area = "부산 사하구",
            city = "부산",
            lat = 35.106200,
            lng = 128.966600,
            peak = "22:00 ~ 02:00",
            tip = "하단역 대학가·주거 수요"
        ),
        Hotspot(
            id = "dongnae",
            name = "동래 · 온천장",
            area = "부산 동래구",
            city = "부산",
            lat = 35.205600,
            lng = 129.078600,
            peak = "22:00 ~ 02:00",
            tip = "동래역 온천장·먹자골목, 주말 심야 수요"
        ),
        Hotspot(
            id = "busan-station",
            name = "부산역 · 중앙동",
            area = "부산 동구",
            city = "부산",
            lat = 35.115000,
            lng = 129.040000,
            peak = "21:30 ~ 01:30",
            tip = "철도 이용객·중앙동 상권 수요"
        ),
        Hotspot(
            id = "dongseongro",
            name = "대구 동성로",
            area = "대구 중구",
            city = "대구",
            lat = 35.869300,
            lng = 128.595000,
            peak = "22:00 ~ 03:00",
            tip = "대구 중심 유흥가, 젊은 층 이동 많음"
        ),
        Hotspot(
            id = "dongdaegu",
            name = "대구 동대구역",
            area = "대구 동구",
            city = "대구",
            lat = 35.876000,
            lng = 128.628600,
            peak = "21:30 ~ 02:00",
            tip = "동대구역 상권·호텔 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "suseong",
            name = "대구 수성 · 범어",
            area = "대구 수성구",
            city = "대구",
            lat = 35.858000,
            lng = 128.626000,
            peak = "22:00 ~ 02:30",
            tip = "범어·수성 유흥가, 장거리 귀가 콜 비중 높음"
        ),
        Hotspot(
            id = "seongseo",
            name = "대구 성서",
            area = "대구 달서구",
            city = "대구",
            lat = 35.851700,
            lng = 128.507200,
            peak = "22:00 ~ 02:00",
            tip = "계명대·산단 배후 상권, 야근 콜도 있음"
        ),
        Hotspot(
            id = "sangin",
            name = "대구 상인 · 월배",
            area = "대구 달서구",
            city = "대구",
            lat = 35.819000,
            lng = 128.530000,
            peak = "21:30 ~ 01:30",
            tip = "상인역 상권·주거 수요"
        ),
        Hotspot(
            id = "chilgok",
            name = "칠곡 · 태전",
            area = "대구 북구",
            city = "대구",
            lat = 35.929200,
            lng = 128.549800,
            peak = "21:30 ~ 01:30",
            tip = "칠곡 대학가·주거 밀집, 귀가 콜 위주"
        ),
        Hotspot(
            id = "dunsan",
            name = "대전 둔산동",
            area = "대전 서구",
            city = "대전",
            lat = 36.351100,
            lng = 127.384500,
            peak = "22:00 ~ 02:00",
            tip = "시청·둔산 업무지구 회식 수요"
        ),
        Hotspot(
            id = "eunhaeng",
            name = "대전 은행동 · 중앙로",
            area = "대전 중구",
            city = "대전",
            lat = 36.328000,
            lng = 127.427000,
            peak = "22:00 ~ 03:00",
            tip = "중앙로역 먹자골목·유흥가, 새벽까지 이어짐"
        ),
        Hotspot(
            id = "yuseong",
            name = "대전 유성온천",
            area = "대전 유성구",
            city = "대전",
            lat = 36.355200,
            lng = 127.340500,
            peak = "22:00 ~ 02:30",
            tip = "유성온천 상권·대학가 수요"
        ),
        Hotspot(
            id = "daejeon-station",
            name = "대전역",
            area = "대전 동구",
            city = "대전",
            lat = 36.332500,
            lng = 127.434000,
            peak = "21:30 ~ 01:30",
            tip = "대전역 상권·귀가 수요"
        ),
        Hotspot(
            id = "bongmyeong",
            name = "대전 궁동 · 봉명",
            area = "대전 유성구",
            city = "대전",
            lat = 36.366500,
            lng = 127.345500,
            peak = "22:00 ~ 02:30",
            tip = "충남대 앞 대학가, 젊은 층 심야 수요"
        ),
        Hotspot(
            id = "sangmu",
            name = "광주 상무지구",
            area = "광주 서구",
            city = "광주",
            lat = 35.152000,
            lng = 126.850000,
            peak = "22:00 ~ 02:30",
            tip = "광주 대표 유흥·회식 밀집 지역"
        ),
        Hotspot(
            id = "chungjangro",
            name = "광주 충장로",
            area = "광주 동구",
            city = "광주",
            lat = 35.147800,
            lng = 126.912000,
            peak = "22:00 ~ 03:00",
            tip = "광주 중심 번화가, 주말 심야 수요"
        ),
        Hotspot(
            id = "usuq",
            name = "광주 터미널 · 금남로",
            area = "광주 서구",
            city = "광주",
            lat = 35.160000,
            lng = 126.884000,
            peak = "21:30 ~ 01:30",
            tip = "유스퀘어 터미널·금남로 상권 수요"
        ),
        Hotspot(
            id = "suwan",
            name = "광주 수완지구",
            area = "광주 광산구",
            city = "광주",
            lat = 35.188000,
            lng = 126.820000,
            peak = "21:30 ~ 01:00",
            tip = "수완지구 상가·주거 수요"
        ),
        Hotspot(
            id = "samsan",
            name = "울산 삼산",
            area = "울산 남구",
            city = "울산",
            lat = 35.539000,
            lng = 129.337000,
            peak = "22:00 ~ 02:00",
            tip = "삼산지구 술집 밀집, 산업단지 야근 수요도 있음"
        ),
        Hotspot(
            id = "seongnam-ulsan",
            name = "울산 성남동",
            area = "울산 중구",
            city = "울산",
            lat = 35.552000,
            lng = 129.316000,
            peak = "22:00 ~ 03:00",
            tip = "성남동 문화의거리, 울산 대표 심야 상권"
        ),
        Hotspot(
            id = "okdong",
            name = "울산 옥동",
            area = "울산 남구",
            city = "울산",
            lat = 35.523900,
            lng = 129.288700,
            peak = "21:30 ~ 01:30",
            tip = "옥동 학원가·주거 수요"
        ),
        Hotspot(
            id = "sejong",
            name = "세종 나성동",
            area = "세종특별자치시",
            city = "세종",
            lat = 36.486400,
            lng = 127.263800,
            peak = "21:30 ~ 01:00",
            tip = "세종시청·정부청사 상권, 공무원 회식 수요"
        ),
        Hotspot(
            id = "jochiwon",
            name = "세종 조치원",
            area = "세종특별자치시",
            city = "세종",
            lat = 36.600500,
            lng = 127.296600,
            peak = "21:30 ~ 01:00",
            tip = "조치원역 대학가·전통시장 수요"
        ),
        Hotspot(
            id = "seongangil",
            name = "청주 성안길",
            area = "충북 청주시",
            city = "충북",
            lat = 36.636000,
            lng = 127.488000,
            peak = "22:00 ~ 02:00",
            tip = "청주 중심 상권, 주말 심야 수요"
        ),
        Hotspot(
            id = "chungju",
            name = "충주",
            area = "충북 충주시",
            city = "충북",
            lat = 36.975900,
            lng = 127.909100,
            peak = "21:30 ~ 01:30",
            tip = "충주역·시내 상권, 터미널 수요도 있음"
        ),
        Hotspot(
            id = "jecheon",
            name = "제천",
            area = "충북 제천시",
            city = "충북",
            lat = 37.128000,
            lng = 128.205300,
            peak = "21:30 ~ 01:00",
            tip = "제천역·중앙시장 상권 수요"
        ),
        Hotspot(
            id = "cheonan-station",
            name = "천안역",
            area = "충남 천안시",
            city = "충남",
            lat = 36.810100,
            lng = 127.146600,
            peak = "22:00 ~ 02:30",
            tip = "천안역 먹자골목·유흥가, 새벽까지 이어짐"
        ),
        Hotspot(
            id = "cheonan-buldang",
            name = "천안 불당",
            area = "충남 천안시",
            city = "충남",
            lat = 36.809100,
            lng = 127.106300,
            peak = "22:00 ~ 02:00",
            tip = "불당지구 상가·주거 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "onyang",
            name = "아산 온양",
            area = "충남 아산시",
            city = "충남",
            lat = 36.780400,
            lng = 127.016000,
            peak = "21:30 ~ 01:30",
            tip = "온양온천역 상권·관광 수요"
        ),
        Hotspot(
            id = "seosan",
            name = "서산",
            area = "충남 서산시",
            city = "충남",
            lat = 36.780000,
            lng = 126.450000,
            peak = "21:30 ~ 01:00",
            tip = "서산시내 상권, 산단 야근 수요도 있음"
        ),
        Hotspot(
            id = "chuncheon",
            name = "춘천 명동",
            area = "강원 춘천시",
            city = "강원",
            lat = 37.879000,
            lng = 127.728000,
            peak = "22:00 ~ 02:00",
            tip = "명동 먹자골목·대학가 수요"
        ),
        Hotspot(
            id = "wonju",
            name = "원주 중앙로",
            area = "강원 원주시",
            city = "강원",
            lat = 37.349700,
            lng = 127.951000,
            peak = "22:00 ~ 02:00",
            tip = "원주 중앙로 상권, 군부대·학원가 수요"
        ),
        Hotspot(
            id = "gangneung",
            name = "강릉",
            area = "강원 강릉시",
            city = "강원",
            lat = 37.764000,
            lng = 128.900000,
            peak = "21:30 ~ 01:30",
            tip = "강릉역·중앙시장 상권, 관광 시즌 수요"
        ),
        Hotspot(
            id = "sokcho",
            name = "속초",
            area = "강원 속초시",
            city = "강원",
            lat = 38.205000,
            lng = 128.594000,
            peak = "21:30 ~ 01:30",
            tip = "속초 중앙시장·해변 상권, 관광객 수요"
        ),
        Hotspot(
            id = "jeonju-hanok",
            name = "전주 한옥마을 · 객리단",
            area = "전북 전주시",
            city = "전북",
            lat = 35.815000,
            lng = 127.153000,
            peak = "22:00 ~ 02:00",
            tip = "한옥마을·객리단길 상권, 관광 수요"
        ),
        Hotspot(
            id = "jeonju-daehak",
            name = "전주 대학로",
            area = "전북 전주시",
            city = "전북",
            lat = 35.846800,
            lng = 127.129900,
            peak = "22:00 ~ 03:00",
            tip = "대학가 술집 밀집, 젊은 층 심야 이동 많음"
        ),
        Hotspot(
            id = "iksan",
            name = "익산",
            area = "전북 익산시",
            city = "전북",
            lat = 35.945000,
            lng = 126.957000,
            peak = "21:30 ~ 01:30",
            tip = "익산역·시내 상권 수요"
        ),
        Hotspot(
            id = "gunsan",
            name = "군산 나운동",
            area = "전북 군산시",
            city = "전북",
            lat = 35.962400,
            lng = 126.697000,
            peak = "21:30 ~ 01:30",
            tip = "군산역·시내 상권, 산단 야근 수요도 있음"
        ),
        Hotspot(
            id = "suncheon",
            name = "순천",
            area = "전남 순천시",
            city = "전남",
            lat = 34.945000,
            lng = 127.503000,
            peak = "21:30 ~ 01:30",
            tip = "순천역·신대지구 상권 수요"
        ),
        Hotspot(
            id = "yeosu",
            name = "여수",
            area = "전남 여수시",
            city = "전남",
            lat = 34.750000,
            lng = 127.745000,
            peak = "22:00 ~ 02:30",
            tip = "여수엑스포·이순신광장 상권, 관광 수요"
        ),
        Hotspot(
            id = "mokpo",
            name = "목포",
            area = "전남 목포시",
            city = "전남",
            lat = 34.793000,
            lng = 126.386000,
            peak = "21:30 ~ 01:30",
            tip = "목포역·평화광장 상권 수요"
        ),
        Hotspot(
            id = "pohang",
            name = "포항",
            area = "경북 포항시",
            city = "경북",
            lat = 36.035000,
            lng = 129.365000,
            peak = "22:00 ~ 02:00",
            tip = "죽도시장·중앙상가 상권, 철강 야근 수요도 있음"
        ),
        Hotspot(
            id = "gyeongju",
            name = "경주 중앙시장",
            area = "경북 경주시",
            city = "경북",
            lat = 35.844000,
            lng = 129.206400,
            peak = "21:30 ~ 01:30",
            tip = "경주 시내 상권·관광 수요"
        ),
        Hotspot(
            id = "gumi",
            name = "구미",
            area = "경북 구미시",
            city = "경북",
            lat = 36.128000,
            lng = 128.334000,
            peak = "22:00 ~ 02:00",
            tip = "구미역·산단 배후 상권, 야근 콜 비중 높음"
        ),
        Hotspot(
            id = "andong",
            name = "안동",
            area = "경북 안동시",
            city = "경북",
            lat = 36.568000,
            lng = 128.729000,
            peak = "21:30 ~ 01:00",
            tip = "안동역·중앙시장 상권 수요"
        ),
        Hotspot(
            id = "changwon",
            name = "창원 상남동",
            area = "경남 창원시",
            city = "경남",
            lat = 35.225000,
            lng = 128.681000,
            peak = "22:00 ~ 02:30",
            tip = "상남동 먹자골목·유흥가, 창원 대표 콜밭"
        ),
        Hotspot(
            id = "gimhae",
            name = "김해",
            area = "경남 김해시",
            city = "경남",
            lat = 35.234000,
            lng = 128.889000,
            peak = "21:30 ~ 01:30",
            tip = "김해시청·내외동 상권 수요"
        ),
        Hotspot(
            id = "jinju",
            name = "진주",
            area = "경남 진주시",
            city = "경남",
            lat = 35.196000,
            lng = 128.085000,
            peak = "21:30 ~ 01:30",
            tip = "진주 중앙시장·대학가 상권 수요"
        ),
        Hotspot(
            id = "yangsan",
            name = "양산",
            area = "경남 양산시",
            city = "경남",
            lat = 35.336000,
            lng = 129.037000,
            peak = "21:30 ~ 01:30",
            tip = "양산역·물금 상권, 부산 경계 이동 수요"
        ),
        Hotspot(
            id = "jeju-yeon",
            name = "제주 연동 · 노형",
            area = "제주 제주시",
            city = "제주",
            lat = 33.486100,
            lng = 126.490700,
            peak = "22:00 ~ 03:00",
            tip = "연동 먹자골목·호텔가, 관광객 심야 수요"
        ),
        Hotspot(
            id = "seogwipo",
            name = "서귀포",
            area = "제주 서귀포시",
            city = "제주",
            lat = 33.254000,
            lng = 126.560000,
            peak = "21:30 ~ 02:00",
            tip = "서귀포 시내·올레시장 상권, 관광 수요"
        ),
    )

    val CITIES: List<String> = listOf(
        "서울",
        "경기",
        "인천",
        "부산",
        "대구",
        "대전",
        "광주",
        "울산",
        "세종",
        "충북",
        "충남",
        "강원",
        "전북",
        "전남",
        "경북",
        "경남",
        "제주"
    )

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

    /** 콜밭 출처 필터 — (값, 화면에 보이는 이름) */
    val SOURCES: List<Pair<String, String>> = listOf(
        "" to "기본",
        "콜포인트1" to "콜포인트1",
        "콜포인트2" to "콜포인트2",
        "탁송" to "탁송"
    )

    /** 이름으로 대략적인 좌표 찾기 (내 콜밭 저장·표시용) */
    fun findByName(name: String, spots: List<Hotspot>): Hotspot? {
        val n = name.trim()
        if (n.isEmpty()) return null
        return spots.firstOrNull { it.name == n }
            ?: spots.firstOrNull { it.name.contains(n) || n.contains(it.name) }
            ?: spots.firstOrNull { it.name.split(" ").any { part -> part.isNotBlank() && n.contains(part) } }
    }

    fun sameSpot(a: String, b: String): Boolean =
        a.trim().equals(b.trim(), ignoreCase = true)
}
