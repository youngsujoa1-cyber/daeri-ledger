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
    val tip: String
) {
    /** 검색어가 이름·지역·시·특징 중 어디에든 들어 있는지 (공백으로 여러 낱말 AND) */
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val haystack = "$name $area $city $tip"
        return q.split(" ", "\t").filter { it.isNotBlank() }.all { haystack.contains(it, ignoreCase = true) }
    }
}

object Hotspots {

    val ALL: List<Hotspot> = listOf(
        Hotspot(
            id = "gangnam",
            name = "강남역 · 역삼",
            area = "서울 강남구",
            city = "서울",
            lat = 37.4979,
            lng = 127.0276,
            peak = "22:00 ~ 02:00",
            tip = "대로변 유동 인구가 많고 심야 대기 수요가 꾸준한 대표 콜밭"
        ),
        Hotspot(
            id = "seolleung",
            name = "선릉 · 삼성",
            area = "서울 강남구",
            city = "서울",
            lat = 37.5045,
            lng = 127.0490,
            peak = "22:30 ~ 01:30",
            tip = "오피스·유흥 수요가 섞여 2차 이동 콜이 자주 나오는 구간"
        ),
        Hotspot(
            id = "apgujeong",
            name = "압구정 · 청담",
            area = "서울 강남구",
            city = "서울",
            lat = 37.5270,
            lng = 127.0286,
            peak = "23:00 ~ 03:00",
            tip = "금·토 심야 장거리 콜 비중이 높은 편"
        ),
        Hotspot(
            id = "nonhyeon",
            name = "논현 · 신사",
            area = "서울 강남구",
            city = "서울",
            lat = 37.5111,
            lng = 127.0218,
            peak = "22:00 ~ 02:00",
            tip = "가로수길·먹자골목 인접, 짧은 거리 콜이 많음"
        ),
        Hotspot(
            id = "jamsil",
            name = "잠실 · 신천",
            area = "서울 송파구",
            city = "서울",
            lat = 37.5133,
            lng = 127.1000,
            peak = "21:30 ~ 02:00",
            tip = "롯데월드타워·먹자골목 수요, 주말 늦은 시간까지 이어짐"
        ),
        Hotspot(
            id = "munjeong",
            name = "문정 · 가락",
            area = "서울 송파구",
            city = "서울",
            lat = 37.4857,
            lng = 127.1225,
            peak = "21:30 ~ 01:00",
            tip = "법조단지·오피스 회식 뒤 귀가 콜 위주"
        ),
        Hotspot(
            id = "gangdong",
            name = "천호 · 강동",
            area = "서울 강동구",
            city = "서울",
            lat = 37.5385,
            lng = 127.1238,
            peak = "22:00 ~ 01:30",
            tip = "천호역 로데오 상권, 주말 심야에 강한 편"
        ),
        Hotspot(
            id = "konkuk",
            name = "건대입구",
            area = "서울 광진구",
            city = "서울",
            lat = 37.5404,
            lng = 127.0695,
            peak = "22:00 ~ 03:00",
            tip = "대학가·먹자골목, 20~30대 심야 이동이 많음"
        ),
        Hotspot(
            id = "gunja",
            name = "군자 · 장한평",
            area = "서울 광진구",
            city = "서울",
            lat = 37.5575,
            lng = 127.0794,
            peak = "22:00 ~ 02:00",
            tip = "주거 밀집지역 귀가 콜 위주"
        ),
        Hotspot(
            id = "seongsu",
            name = "성수",
            area = "서울 성동구",
            city = "서울",
            lat = 37.5445,
            lng = 127.0557,
            peak = "22:00 ~ 02:30",
            tip = "카페거리·창고형 술집 수요, 주말 늦은 시간까지 이어짐"
        ),
        Hotspot(
            id = "wangsimni",
            name = "왕십리",
            area = "서울 성동구",
            city = "서울",
            lat = 37.5613,
            lng = 127.0370,
            peak = "22:00 ~ 01:30",
            tip = "환승 거점·먹자골목, 귀가 콜과 2차 이동이 섞임"
        ),
        Hotspot(
            id = "hongdae",
            name = "홍대입구",
            area = "서울 마포구",
            city = "서울",
            lat = 37.5572,
            lng = 126.9245,
            peak = "23:00 ~ 04:00",
            tip = "새벽까지 클럽·술집 수요가 이어지는 대표 심야 콜밭"
        ),
        Hotspot(
            id = "hapjeong",
            name = "합정 · 망원",
            area = "서울 마포구",
            city = "서울",
            lat = 37.5497,
            lng = 126.9138,
            peak = "22:00 ~ 02:00",
            tip = "술집·카페 밀집, 주말 심야 수요가 꾸준한 편"
        ),
        Hotspot(
            id = "gongdeok",
            name = "공덕 · 마포",
            area = "서울 마포구",
            city = "서울",
            lat = 37.5445,
            lng = 126.9515,
            peak = "21:30 ~ 01:30",
            tip = "오피스 회식·주거 수요가 함께 있는 구간"
        ),
        Hotspot(
            id = "sinchon",
            name = "신촌 · 이대",
            area = "서울 서대문구",
            city = "서울",
            lat = 37.5559,
            lng = 126.9368,
            peak = "22:00 ~ 02:00",
            tip = "대학가 특성상 평일 밤에도 꾸준한 편"
        ),
        Hotspot(
            id = "jongno",
            name = "종로 · 익선동",
            area = "서울 종로구",
            city = "서울",
            lat = 37.5704,
            lng = 126.9919,
            peak = "22:30 ~ 02:00",
            tip = "직장인 회식 2차 수요, 평일 밤 비중이 높음"
        ),
        Hotspot(
            id = "gwanghwamun",
            name = "광화문 · 시청",
            area = "서울 종로구",
            city = "서울",
            lat = 37.5657,
            lng = 126.9770,
            peak = "21:00 ~ 24:00",
            tip = "직장인 회식·관광 수요, 주말은 한산한 편"
        ),
        Hotspot(
            id = "yeouido",
            name = "여의도",
            area = "서울 영등포구",
            city = "서울",
            lat = 37.5219,
            lng = 126.9245,
            peak = "21:00 ~ 24:00",
            tip = "평일 회식 수요 중심, 주말은 한산한 편"
        ),
        Hotspot(
            id = "yeongdeungpo",
            name = "영등포",
            area = "서울 영등포구",
            city = "서울",
            lat = 37.5156,
            lng = 126.9075,
            peak = "22:00 ~ 02:00",
            tip = "역 주변 먹자골목·모텔 수요, 짧은 콜이 많음"
        ),
        Hotspot(
            id = "sindorim",
            name = "신도림 · 구로",
            area = "서울 구로구",
            city = "서울",
            lat = 37.5088,
            lng = 126.8912,
            peak = "21:30 ~ 01:30",
            tip = "역세권·오피스 수요, 평일 밤 중심"
        ),
        Hotspot(
            id = "guro-dg",
            name = "구로디지털단지",
            area = "서울 구로구",
            city = "서울",
            lat = 37.4852,
            lng = 126.9016,
            peak = "22:00 ~ 01:30",
            tip = "IT 단지 야근·회식 수요로 평일 심야가 강함"
        ),
        Hotspot(
            id = "gasan",
            name = "가산 · 독산",
            area = "서울 금천구",
            city = "서울",
            lat = 37.4810,
            lng = 126.8820,
            peak = "21:30 ~ 01:00",
            tip = "업무지구 야근 수요, 주말은 조용한 편"
        ),
        Hotspot(
            id = "sadang",
            name = "사당 · 이수",
            area = "서울 동작구",
            city = "서울",
            lat = 37.4766,
            lng = 126.9816,
            peak = "22:00 ~ 02:00",
            tip = "강남·사당 이동 거점, 환승 수요 많음"
        ),
        Hotspot(
            id = "noryangjin",
            name = "노량진",
            area = "서울 동작구",
            city = "서울",
            lat = 37.5142,
            lng = 126.9425,
            peak = "22:00 ~ 02:00",
            tip = "학원가·수산시장 새벽 수요가 섞여 있음"
        ),
        Hotspot(
            id = "sillim",
            name = "신림",
            area = "서울 관악구",
            city = "서울",
            lat = 37.4842,
            lng = 126.9296,
            peak = "22:30 ~ 03:00",
            tip = "대학가·먹자골목, 새벽까지 수요가 이어짐"
        ),
        Hotspot(
            id = "magok",
            name = "마곡 · 발산",
            area = "서울 강서구",
            city = "서울",
            lat = 37.5601,
            lng = 126.8253,
            peak = "21:30 ~ 01:00",
            tip = "업무지구 야근 수요, 주말은 조용함"
        ),
        Hotspot(
            id = "hwagok",
            name = "화곡 · 까치산",
            area = "서울 강서구",
            city = "서울",
            lat = 37.5417,
            lng = 126.8404,
            peak = "22:00 ~ 02:00",
            tip = "주거 밀집지역 술집 상권, 귀가 콜 위주"
        ),
        Hotspot(
            id = "mokdong",
            name = "목동 · 오목교",
            area = "서울 양천구",
            city = "서울",
            lat = 37.5245,
            lng = 126.8750,
            peak = "22:00 ~ 01:30",
            tip = "학원가·먹자골목, 밤늦게까지 수요가 남는 편"
        ),
        Hotspot(
            id = "nowon",
            name = "노원 · 상계",
            area = "서울 노원구",
            city = "서울",
            lat = 37.6550,
            lng = 127.0610,
            peak = "22:00 ~ 01:30",
            tip = "주거지 중심, 귀가 콜 비중이 높음"
        ),
        Hotspot(
            id = "changdong",
            name = "창동 · 도봉",
            area = "서울 도봉구",
            city = "서울",
            lat = 37.6531,
            lng = 127.0474,
            peak = "22:00 ~ 01:00",
            tip = "주거지 환승 거점, 귀가 콜 위주"
        ),
        Hotspot(
            id = "suyu",
            name = "수유 · 미아",
            area = "서울 강북구",
            city = "서울",
            lat = 37.6378,
            lng = 127.0255,
            peak = "22:00 ~ 02:00",
            tip = "대학가·주거 상권이 섞인 구간"
        ),
        Hotspot(
            id = "anam",
            name = "안암 · 고려대",
            area = "서울 성북구",
            city = "서울",
            lat = 37.5862,
            lng = 127.0292,
            peak = "22:00 ~ 02:30",
            tip = "대학가 술집 밀집, 평일 밤 수요가 꾸준함"
        ),
        Hotspot(
            id = "cheongnyangni",
            name = "청량리",
            area = "서울 동대문구",
            city = "서울",
            lat = 37.5801,
            lng = 127.0470,
            peak = "21:30 ~ 01:30",
            tip = "철도 환승·시장 상권, 이른 심야 수요가 많음"
        ),
        Hotspot(
            id = "dongdaemun",
            name = "동대문 · DDP",
            area = "서울 중구",
            city = "서울",
            lat = 37.5657,
            lng = 127.0090,
            peak = "23:00 ~ 04:00",
            tip = "의류 도매시장 야간 수요로 새벽 콜이 많은 곳"
        ),
        Hotspot(
            id = "itaewon",
            name = "이태원 · 경리단",
            area = "서울 용산구",
            city = "서울",
            lat = 37.5345,
            lng = 126.9946,
            peak = "23:00 ~ 04:00",
            tip = "외국인·관광객 수요, 주말 새벽까지 이어짐"
        ),
        Hotspot(
            id = "yongsan",
            name = "용산 · 삼각지",
            area = "서울 용산구",
            city = "서울",
            lat = 37.5299,
            lng = 126.9647,
            peak = "22:00 ~ 02:00",
            tip = "역 주변 술집·전자상가 수요가 섞임"
        ),
        Hotspot(
            id = "sangbong",
            name = "상봉 · 면목",
            area = "서울 중랑구",
            city = "서울",
            lat = 37.5960,
            lng = 127.0857,
            peak = "22:00 ~ 01:30",
            tip = "동북권 환승 거점, 심야 귀가 수요"
        ),
        Hotspot(
            id = "eunpyeong",
            name = "연신내 · 불광",
            area = "서울 은평구",
            city = "서울",
            lat = 37.6191,
            lng = 126.9210,
            peak = "22:00 ~ 02:00",
            tip = "서북권 환승 거점, 먹자골목 심야 수요"
        ),
        Hotspot(
            id = "pangyo",
            name = "판교역",
            area = "경기 성남시",
            city = "경기",
            lat = 37.3948,
            lng = 127.1112,
            peak = "22:00 ~ 02:00",
            tip = "IT 업무지구 야근·회식 수요, 평일 심야에 강함"
        ),
        Hotspot(
            id = "jeongja",
            name = "분당 정자",
            area = "경기 성남시",
            city = "경기",
            lat = 37.3671,
            lng = 127.1085,
            peak = "22:00 ~ 01:30",
            tip = "정자·수내 주거 수요, 장거리 귀가 콜이 많음"
        ),
        Hotspot(
            id = "seohyeon",
            name = "분당 서현",
            area = "경기 성남시",
            city = "경기",
            lat = 37.3848,
            lng = 127.1230,
            peak = "22:00 ~ 02:00",
            tip = "서현역 먹자골목 중심, 주말 늦은 시간 수요"
        ),
        Hotspot(
            id = "yatap",
            name = "분당 야탑",
            area = "경기 성남시",
            city = "경기",
            lat = 37.4113,
            lng = 127.1287,
            peak = "22:00 ~ 02:00",
            tip = "야탑역 로데오 상권, 주말 심야 수요"
        ),
        Hotspot(
            id = "sunae",
            name = "분당 수내",
            area = "경기 성남시",
            city = "경기",
            lat = 37.3784,
            lng = 127.1153,
            peak = "22:00 ~ 01:30",
            tip = "아파트 밀집지역, 귀가 콜 위주"
        ),
        Hotspot(
            id = "moran",
            name = "성남 모란",
            area = "경기 성남시",
            city = "경기",
            lat = 37.4321,
            lng = 127.1290,
            peak = "22:00 ~ 03:00",
            tip = "모란역 먹자골목·시장 상권, 새벽까지 이어짐"
        ),
        Hotspot(
            id = "wirye",
            name = "위례",
            area = "경기 성남시",
            city = "경기",
            lat = 37.4728,
            lng = 127.1419,
            peak = "21:30 ~ 01:00",
            tip = "신도시 상가·주거 혼합, 귀가 콜 위주"
        ),
        Hotspot(
            id = "suji",
            name = "용인 수지",
            area = "경기 용인시",
            city = "경기",
            lat = 37.3225,
            lng = 127.0954,
            peak = "22:00 ~ 02:00",
            tip = "수지구청·구성 상권, 아파트 귀가 콜이 많음"
        ),
        Hotspot(
            id = "jukjeon",
            name = "용인 죽전",
            area = "경기 용인시",
            city = "경기",
            lat = 37.3246,
            lng = 127.1074,
            peak = "22:00 ~ 02:00",
            tip = "죽전역 상권·대학가 수요가 섞여 있음"
        ),
        Hotspot(
            id = "giheung",
            name = "용인 기흥",
            area = "경기 용인시",
            city = "경기",
            lat = 37.2756,
            lng = 127.1155,
            peak = "22:00 ~ 01:30",
            tip = "기흥역 주변 주거·상가 수요"
        ),
        Hotspot(
            id = "dongbaek",
            name = "용인 동백",
            area = "경기 용인시",
            city = "경기",
            lat = 37.2763,
            lng = 127.1544,
            peak = "22:00 ~ 01:30",
            tip = "동백지구 상가·주거 혼합, 귀가 콜 위주"
        ),
        Hotspot(
            id = "yeokbuk",
            name = "용인 역북",
            area = "경기 용인시",
            city = "경기",
            lat = 37.2340,
            lng = 127.1900,
            peak = "22:00 ~ 01:30",
            tip = "역북·김량장 상권, 시내 귀가 수요"
        ),
        Hotspot(
            id = "suwon-ingye",
            name = "수원 인계동",
            area = "경기 수원시",
            city = "경기",
            lat = 37.2635,
            lng = 127.0320,
            peak = "22:30 ~ 03:00",
            tip = "수원 대표 유흥가, 새벽까지 이어지는 편"
        ),
        Hotspot(
            id = "suwon-station",
            name = "수원역",
            area = "경기 수원시",
            city = "경기",
            lat = 37.2657,
            lng = 126.9997,
            peak = "21:00 ~ 01:00",
            tip = "역 주변 술집·모텔 수요, 짧은 콜 많음"
        ),
        Hotspot(
            id = "gwanggyo",
            name = "수원 광교",
            area = "경기 수원시",
            city = "경기",
            lat = 37.2885,
            lng = 127.0516,
            peak = "21:30 ~ 01:00",
            tip = "광교중앙 상권·오피스 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "yeongtong",
            name = "수원 영통",
            area = "경기 수원시",
            city = "경기",
            lat = 37.2514,
            lng = 127.0716,
            peak = "22:00 ~ 01:30",
            tip = "영통역 먹자골목, 대학가 수요도 있음"
        ),
        Hotspot(
            id = "mangpo",
            name = "수원 망포",
            area = "경기 수원시",
            city = "경기",
            lat = 37.2456,
            lng = 127.0610,
            peak = "22:00 ~ 01:30",
            tip = "망포역 주변 상가·주거 수요"
        ),
        Hotspot(
            id = "dongtan1",
            name = "화성 동탄1",
            area = "경기 화성시",
            city = "경기",
            lat = 37.2040,
            lng = 127.0730,
            peak = "22:00 ~ 02:00",
            tip = "동탄1신도시 중심 상권, 주말 심야 수요"
        ),
        Hotspot(
            id = "dongtan2",
            name = "화성 동탄2",
            area = "경기 화성시",
            city = "경기",
            lat = 37.2007,
            lng = 127.0955,
            peak = "21:30 ~ 01:30",
            tip = "동탄역·신도시 상가 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "byeongjeom",
            name = "화성 병점",
            area = "경기 화성시",
            city = "경기",
            lat = 37.2066,
            lng = 127.0334,
            peak = "22:00 ~ 01:30",
            tip = "병점역 먹자골목, 주거지 귀가 수요"
        ),
        Hotspot(
            id = "hyangnam",
            name = "화성 향남",
            area = "경기 화성시",
            city = "경기",
            lat = 37.1330,
            lng = 126.9180,
            peak = "21:30 ~ 01:00",
            tip = "향남읍 중심 상권, 인근 산단 야근 수요도 있음"
        ),
        Hotspot(
            id = "beomgye",
            name = "안양 범계",
            area = "경기 안양시",
            city = "경기",
            lat = 37.3909,
            lng = 126.9510,
            peak = "22:00 ~ 02:00",
            tip = "범계역 로데오거리 중심 콜밭"
        ),
        Hotspot(
            id = "pyeongchon",
            name = "안양 평촌",
            area = "경기 안양시",
            city = "경기",
            lat = 37.3942,
            lng = 126.9638,
            peak = "22:00 ~ 02:00",
            tip = "평촌 학원가·먹자골목, 밤늦게까지 수요"
        ),
        Hotspot(
            id = "anyang-ilbeonga",
            name = "안양 1번가",
            area = "경기 안양시",
            city = "경기",
            lat = 37.4016,
            lng = 126.9222,
            peak = "22:00 ~ 02:00",
            tip = "안양역 앞 1번가 상권, 심야 유동 인구 많음"
        ),
        Hotspot(
            id = "sangdong",
            name = "부천 상동",
            area = "경기 부천시",
            city = "경기",
            lat = 37.5034,
            lng = 126.7518,
            peak = "22:00 ~ 02:00",
            tip = "상동역 로데오·영화관 상권, 주말 심야 수요"
        ),
        Hotspot(
            id = "jungdong",
            name = "부천 중동",
            area = "경기 부천시",
            city = "경기",
            lat = 37.5035,
            lng = 126.7690,
            peak = "22:00 ~ 02:00",
            tip = "중동 신도시 상가·유흥 수요가 섞임"
        ),
        Hotspot(
            id = "bucheon-station",
            name = "부천역",
            area = "경기 부천시",
            city = "경기",
            lat = 37.4845,
            lng = 126.7816,
            peak = "22:00 ~ 02:30",
            tip = "부천역 먹자골목, 젊은 층 심야 이동 많음"
        ),
        Hotspot(
            id = "ansan-jungang",
            name = "안산 중앙동",
            area = "경기 안산시",
            city = "경기",
            lat = 37.3164,
            lng = 126.8386,
            peak = "22:00 ~ 02:30",
            tip = "안산 중앙역 로데오거리, 외국인 수요도 많음"
        ),
        Hotspot(
            id = "gojan",
            name = "안산 고잔",
            area = "경기 안산시",
            city = "경기",
            lat = 37.3117,
            lng = 126.8227,
            peak = "22:00 ~ 02:00",
            tip = "고잔역 상권·주거 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "jeongwang",
            name = "시흥 정왕",
            area = "경기 시흥시",
            city = "경기",
            lat = 37.3503,
            lng = 126.7399,
            peak = "22:00 ~ 02:00",
            tip = "정왕역·산단 배후 상권, 야근·귀가 수요"
        ),
        Hotspot(
            id = "baegot",
            name = "시흥 배곧",
            area = "경기 시흥시",
            city = "경기",
            lat = 37.3600,
            lng = 126.7200,
            peak = "21:30 ~ 01:00",
            tip = "배곧신도시 상가·대학가 수요"
        ),
        Hotspot(
            id = "pyeongtaek-station",
            name = "평택역",
            area = "경기 평택시",
            city = "경기",
            lat = 36.9906,
            lng = 127.0880,
            peak = "22:00 ~ 02:00",
            tip = "평택역 먹자골목, 미군 주둔지 인접 수요도 있음"
        ),
        Hotspot(
            id = "songtan",
            name = "평택 송탄",
            area = "경기 평택시",
            city = "경기",
            lat = 37.0754,
            lng = 127.0500,
            peak = "22:00 ~ 02:00",
            tip = "송탄역 상권·미군부대 인근 유흥 수요"
        ),
        Hotspot(
            id = "godeok",
            name = "평택 고덕",
            area = "경기 평택시",
            city = "경기",
            lat = 37.0450,
            lng = 127.0450,
            peak = "21:30 ~ 01:00",
            tip = "고덕국제신도시 상가, 귀가 콜 위주"
        ),
        Hotspot(
            id = "uijeongbu",
            name = "의정부역",
            area = "경기 의정부시",
            city = "경기",
            lat = 37.7380,
            lng = 127.0457,
            peak = "22:00 ~ 03:00",
            tip = "의정부역 주변 유흥가, 미군 인접 수요도 있음"
        ),
        Hotspot(
            id = "ilsan",
            name = "일산 정발산",
            area = "경기 고양시",
            city = "경기",
            lat = 37.6595,
            lng = 126.7731,
            peak = "22:00 ~ 02:00",
            tip = "라페스타·웨스턴돔 유흥 수요"
        ),
        Hotspot(
            id = "hwajeong",
            name = "고양 화정",
            area = "경기 고양시",
            city = "경기",
            lat = 37.6354,
            lng = 126.8328,
            peak = "22:00 ~ 02:00",
            tip = "화정역 먹자골목·학원가, 밤늦게까지 수요"
        ),
        Hotspot(
            id = "guri",
            name = "구리",
            area = "경기 구리시",
            city = "경기",
            lat = 37.6013,
            lng = 127.1351,
            peak = "22:00 ~ 02:00",
            tip = "구리역 상권·전통시장 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "dasan",
            name = "남양주 다산",
            area = "경기 남양주시",
            city = "경기",
            lat = 37.6243,
            lng = 127.1498,
            peak = "21:30 ~ 01:00",
            tip = "다산신도시 상가·주거 혼합 수요"
        ),
        Hotspot(
            id = "byeollae",
            name = "남양주 별내",
            area = "경기 남양주시",
            city = "경기",
            lat = 37.6424,
            lng = 127.1272,
            peak = "21:30 ~ 01:00",
            tip = "별내신도시 상가 수요, 서울 경계 귀가 콜"
        ),
        Hotspot(
            id = "misa",
            name = "하남 미사",
            area = "경기 하남시",
            city = "경기",
            lat = 37.5619,
            lng = 127.1930,
            peak = "21:30 ~ 01:00",
            tip = "미사강변 상가·주거 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "gurae",
            name = "김포 구래",
            area = "경기 김포시",
            city = "경기",
            lat = 37.6453,
            lng = 126.6280,
            peak = "21:30 ~ 01:00",
            tip = "구래역 상권·신도시 주거 수요"
        ),
        Hotspot(
            id = "raveniche",
            name = "김포 라베니체",
            area = "경기 김포시",
            city = "경기",
            lat = 37.6316,
            lng = 126.6833,
            peak = "22:00 ~ 02:00",
            tip = "라베니체 상가·유흥 수요, 주말 심야에 강함"
        ),
        Hotspot(
            id = "osan",
            name = "오산역",
            area = "경기 오산시",
            city = "경기",
            lat = 37.1496,
            lng = 127.0696,
            peak = "22:00 ~ 02:00",
            tip = "오산역 먹자골목, 미군기지 인접 수요도 있음"
        ),
        Hotspot(
            id = "unjeong",
            name = "파주 운정",
            area = "경기 파주시",
            city = "경기",
            lat = 37.7225,
            lng = 126.7658,
            peak = "21:30 ~ 01:00",
            tip = "운정신도시 상가·주거 수요"
        ),
        Hotspot(
            id = "geumchon",
            name = "파주 금촌",
            area = "경기 파주시",
            city = "경기",
            lat = 37.7588,
            lng = 126.7741,
            peak = "22:00 ~ 01:30",
            tip = "금촌역 전통 상권, 귀가 콜 위주"
        ),
        Hotspot(
            id = "icheon",
            name = "이천",
            area = "경기 이천시",
            city = "경기",
            lat = 37.2640,
            lng = 127.4425,
            peak = "21:30 ~ 01:00",
            tip = "이천역·시내 상권, 산단 야근 수요도 있음"
        ),
        Hotspot(
            id = "gwangju-gg",
            name = "경기광주",
            area = "경기 광주시",
            city = "경기",
            lat = 37.3990,
            lng = 127.2540,
            peak = "21:30 ~ 01:00",
            tip = "경기광주역 상권·주거 수요"
        ),
        Hotspot(
            id = "sanbon",
            name = "군포 산본",
            area = "경기 군포시",
            city = "경기",
            lat = 37.3583,
            lng = 126.9331,
            peak = "22:00 ~ 02:00",
            tip = "산본역 로데오거리, 주말 심야 수요"
        ),
        Hotspot(
            id = "bupyeong",
            name = "부평",
            area = "인천 부평구",
            city = "인천",
            lat = 37.4894,
            lng = 126.7247,
            peak = "22:00 ~ 03:00",
            tip = "부평역 먹자골목, 인천 최대 심야 콜밭 중 하나"
        ),
        Hotspot(
            id = "guwol",
            name = "구월 · 로데오",
            area = "인천 남동구",
            city = "인천",
            lat = 37.4520,
            lng = 126.7100,
            peak = "22:00 ~ 03:00",
            tip = "구월동 로데오거리, 인천 대표 유흥가"
        ),
        Hotspot(
            id = "songdo",
            name = "송도",
            area = "인천 연수구",
            city = "인천",
            lat = 37.3820,
            lng = 126.6560,
            peak = "21:30 ~ 01:00",
            tip = "주거·업무 혼합, 귀가 콜 위주"
        ),
        Hotspot(
            id = "cheongna",
            name = "청라",
            area = "인천 서구",
            city = "인천",
            lat = 37.5340,
            lng = 126.6490,
            peak = "21:30 ~ 01:00",
            tip = "청라국제도시 상가·주거 수요"
        ),
        Hotspot(
            id = "juan",
            name = "주안",
            area = "인천 미추홀구",
            city = "인천",
            lat = 37.4652,
            lng = 126.6804,
            peak = "22:00 ~ 03:00",
            tip = "주안역 먹자골목·유흥가, 새벽까지 이어짐"
        ),
        Hotspot(
            id = "gyeyang",
            name = "계양 · 작전",
            area = "인천 계양구",
            city = "인천",
            lat = 37.5303,
            lng = 126.7225,
            peak = "22:00 ~ 01:30",
            tip = "계양역 환승 거점, 귀가 콜 위주"
        ),
        Hotspot(
            id = "yeonsu",
            name = "연수 · 동춘",
            area = "인천 연수구",
            city = "인천",
            lat = 37.4175,
            lng = 126.6785,
            peak = "22:00 ~ 01:30",
            tip = "연수역 상권·주거 수요"
        ),
        Hotspot(
            id = "nonhyeon-ic",
            name = "인천 논현",
            area = "인천 남동구",
            city = "인천",
            lat = 37.4005,
            lng = 126.7165,
            peak = "22:00 ~ 02:00",
            tip = "논현역·남동공단 배후 수요, 야근 콜도 있음"
        ),
        Hotspot(
            id = "seomyeon",
            name = "부산 서면",
            area = "부산 부산진구",
            city = "부산",
            lat = 35.1578,
            lng = 129.0595,
            peak = "22:30 ~ 04:00",
            tip = "부산 최대 유흥가, 새벽 콜 비중 높음"
        ),
        Hotspot(
            id = "haeundae",
            name = "부산 해운대",
            area = "부산 해운대구",
            city = "부산",
            lat = 35.1631,
            lng = 129.1636,
            peak = "22:00 ~ 03:00",
            tip = "해운대 해변로·먹자골목, 관광 시즌 수요 증가"
        ),
        Hotspot(
            id = "gwangalli",
            name = "부산 광안리",
            area = "부산 수영구",
            city = "부산",
            lat = 35.1531,
            lng = 129.1186,
            peak = "22:00 ~ 03:00",
            tip = "광안리 해변 상권, 주말 심야에 강함"
        ),
        Hotspot(
            id = "nampo",
            name = "부산 남포동 · 부산역",
            area = "부산 중구",
            city = "부산",
            lat = 35.0990,
            lng = 129.0340,
            peak = "22:00 ~ 03:00",
            tip = "영화의거리·자갈치 상권, 관광객 심야 수요"
        ),
        Hotspot(
            id = "gyeongseong",
            name = "부산 경성대 · 부경대",
            area = "부산 남구",
            city = "부산",
            lat = 35.1373,
            lng = 129.1005,
            peak = "22:00 ~ 03:00",
            tip = "대학가 먹자골목, 젊은 층 심야 이동 많음"
        ),
        Hotspot(
            id = "sasang",
            name = "부산 사상",
            area = "부산 사상구",
            city = "부산",
            lat = 35.1627,
            lng = 128.9853,
            peak = "21:30 ~ 01:30",
            tip = "사상역 터미널·먹자골목 수요"
        ),
        Hotspot(
            id = "yeonsan",
            name = "부산 연산 · 교대",
            area = "부산 연제구",
            city = "부산",
            lat = 35.1865,
            lng = 129.0810,
            peak = "22:00 ~ 02:30",
            tip = "연산역 로데오·법원 상권, 회식 수요"
        ),
        Hotspot(
            id = "hadan",
            name = "부산 하단",
            area = "부산 사하구",
            city = "부산",
            lat = 35.1062,
            lng = 128.9666,
            peak = "22:00 ~ 02:00",
            tip = "하단역 대학가·주거 수요"
        ),
        Hotspot(
            id = "dongnae",
            name = "동래 · 온천장",
            area = "부산 동래구",
            city = "부산",
            lat = 35.2056,
            lng = 129.0786,
            peak = "22:00 ~ 02:00",
            tip = "동래역 온천장·먹자골목, 주말 심야 수요"
        ),
        Hotspot(
            id = "busan-station",
            name = "부산역 · 중앙동",
            area = "부산 동구",
            city = "부산",
            lat = 35.1150,
            lng = 129.0400,
            peak = "21:30 ~ 01:30",
            tip = "철도 이용객·중앙동 상권 수요"
        ),
        Hotspot(
            id = "dongseongro",
            name = "대구 동성로",
            area = "대구 중구",
            city = "대구",
            lat = 35.8693,
            lng = 128.5950,
            peak = "22:00 ~ 03:00",
            tip = "대구 중심 유흥가, 젊은 층 이동 많음"
        ),
        Hotspot(
            id = "dongdaegu",
            name = "대구 동대구역",
            area = "대구 동구",
            city = "대구",
            lat = 35.8760,
            lng = 128.6286,
            peak = "21:30 ~ 02:00",
            tip = "동대구역 상권·호텔 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "suseong",
            name = "대구 수성 · 범어",
            area = "대구 수성구",
            city = "대구",
            lat = 35.8580,
            lng = 128.6260,
            peak = "22:00 ~ 02:30",
            tip = "범어·수성 유흥가, 장거리 귀가 콜 비중 높음"
        ),
        Hotspot(
            id = "seongseo",
            name = "대구 성서",
            area = "대구 달서구",
            city = "대구",
            lat = 35.8517,
            lng = 128.5072,
            peak = "22:00 ~ 02:00",
            tip = "계명대·산단 배후 상권, 야근 콜도 있음"
        ),
        Hotspot(
            id = "sangin",
            name = "대구 상인 · 월배",
            area = "대구 달서구",
            city = "대구",
            lat = 35.8190,
            lng = 128.5300,
            peak = "21:30 ~ 01:30",
            tip = "상인역 상권·주거 수요"
        ),
        Hotspot(
            id = "chilgok",
            name = "칠곡 · 태전",
            area = "대구 북구",
            city = "대구",
            lat = 35.9292,
            lng = 128.5498,
            peak = "21:30 ~ 01:30",
            tip = "칠곡 대학가·주거 밀집, 귀가 콜 위주"
        ),
        Hotspot(
            id = "dunsan",
            name = "대전 둔산동",
            area = "대전 서구",
            city = "대전",
            lat = 36.3511,
            lng = 127.3845,
            peak = "22:00 ~ 02:00",
            tip = "시청·둔산 업무지구 회식 수요"
        ),
        Hotspot(
            id = "eunhaeng",
            name = "대전 은행동 · 중앙로",
            area = "대전 중구",
            city = "대전",
            lat = 36.3280,
            lng = 127.4270,
            peak = "22:00 ~ 03:00",
            tip = "중앙로역 먹자골목·유흥가, 새벽까지 이어짐"
        ),
        Hotspot(
            id = "yuseong",
            name = "대전 유성온천",
            area = "대전 유성구",
            city = "대전",
            lat = 36.3552,
            lng = 127.3405,
            peak = "22:00 ~ 02:30",
            tip = "유성온천 상권·대학가 수요"
        ),
        Hotspot(
            id = "daejeon-station",
            name = "대전역",
            area = "대전 동구",
            city = "대전",
            lat = 36.3325,
            lng = 127.4340,
            peak = "21:30 ~ 01:30",
            tip = "대전역 상권·귀가 수요"
        ),
        Hotspot(
            id = "bongmyeong",
            name = "대전 궁동 · 봉명",
            area = "대전 유성구",
            city = "대전",
            lat = 36.3665,
            lng = 127.3455,
            peak = "22:00 ~ 02:30",
            tip = "충남대 앞 대학가, 젊은 층 심야 수요"
        ),
        Hotspot(
            id = "sangmu",
            name = "광주 상무지구",
            area = "광주 서구",
            city = "광주",
            lat = 35.1520,
            lng = 126.8500,
            peak = "22:00 ~ 02:30",
            tip = "광주 대표 유흥·회식 밀집 지역"
        ),
        Hotspot(
            id = "chungjangro",
            name = "광주 충장로",
            area = "광주 동구",
            city = "광주",
            lat = 35.1478,
            lng = 126.9120,
            peak = "22:00 ~ 03:00",
            tip = "광주 중심 번화가, 주말 심야 수요"
        ),
        Hotspot(
            id = "usuq",
            name = "광주 터미널 · 금남로",
            area = "광주 서구",
            city = "광주",
            lat = 35.1600,
            lng = 126.8840,
            peak = "21:30 ~ 01:30",
            tip = "유스퀘어 터미널·금남로 상권 수요"
        ),
        Hotspot(
            id = "suwan",
            name = "광주 수완지구",
            area = "광주 광산구",
            city = "광주",
            lat = 35.1880,
            lng = 126.8200,
            peak = "21:30 ~ 01:00",
            tip = "수완지구 상가·주거 수요"
        ),
        Hotspot(
            id = "samsan",
            name = "울산 삼산",
            area = "울산 남구",
            city = "울산",
            lat = 35.5390,
            lng = 129.3370,
            peak = "22:00 ~ 02:00",
            tip = "삼산지구 술집 밀집, 산업단지 야근 수요도 있음"
        ),
        Hotspot(
            id = "seongnam-ulsan",
            name = "울산 성남동",
            area = "울산 중구",
            city = "울산",
            lat = 35.5520,
            lng = 129.3160,
            peak = "22:00 ~ 03:00",
            tip = "성남동 문화의거리, 울산 대표 심야 상권"
        ),
        Hotspot(
            id = "okdong",
            name = "울산 옥동",
            area = "울산 남구",
            city = "울산",
            lat = 35.5239,
            lng = 129.2887,
            peak = "21:30 ~ 01:30",
            tip = "옥동 학원가·주거 수요"
        ),
        Hotspot(
            id = "sejong",
            name = "세종 나성동",
            area = "세종특별자치시",
            city = "세종",
            lat = 36.4864,
            lng = 127.2638,
            peak = "21:30 ~ 01:00",
            tip = "세종시청·정부청사 상권, 공무원 회식 수요"
        ),
        Hotspot(
            id = "jochiwon",
            name = "세종 조치원",
            area = "세종특별자치시",
            city = "세종",
            lat = 36.6005,
            lng = 127.2966,
            peak = "21:30 ~ 01:00",
            tip = "조치원역 대학가·전통시장 수요"
        ),
        Hotspot(
            id = "seongangil",
            name = "청주 성안길",
            area = "충북 청주시",
            city = "충북",
            lat = 36.6360,
            lng = 127.4880,
            peak = "22:00 ~ 02:00",
            tip = "청주 중심 상권, 주말 심야 수요"
        ),
        Hotspot(
            id = "chungju",
            name = "충주",
            area = "충북 충주시",
            city = "충북",
            lat = 36.9759,
            lng = 127.9091,
            peak = "21:30 ~ 01:30",
            tip = "충주역·시내 상권, 터미널 수요도 있음"
        ),
        Hotspot(
            id = "jecheon",
            name = "제천",
            area = "충북 제천시",
            city = "충북",
            lat = 37.1280,
            lng = 128.2053,
            peak = "21:30 ~ 01:00",
            tip = "제천역·중앙시장 상권 수요"
        ),
        Hotspot(
            id = "cheonan-station",
            name = "천안역",
            area = "충남 천안시",
            city = "충남",
            lat = 36.8101,
            lng = 127.1466,
            peak = "22:00 ~ 02:30",
            tip = "천안역 먹자골목·유흥가, 새벽까지 이어짐"
        ),
        Hotspot(
            id = "cheonan-buldang",
            name = "천안 불당",
            area = "충남 천안시",
            city = "충남",
            lat = 36.8091,
            lng = 127.1063,
            peak = "22:00 ~ 02:00",
            tip = "불당지구 상가·주거 수요, 귀가 콜 위주"
        ),
        Hotspot(
            id = "onyang",
            name = "아산 온양",
            area = "충남 아산시",
            city = "충남",
            lat = 36.7804,
            lng = 127.0160,
            peak = "21:30 ~ 01:30",
            tip = "온양온천역 상권·관광 수요"
        ),
        Hotspot(
            id = "seosan",
            name = "서산",
            area = "충남 서산시",
            city = "충남",
            lat = 36.7800,
            lng = 126.4500,
            peak = "21:30 ~ 01:00",
            tip = "서산시내 상권, 산단 야근 수요도 있음"
        ),
        Hotspot(
            id = "chuncheon",
            name = "춘천 명동",
            area = "강원 춘천시",
            city = "강원",
            lat = 37.8790,
            lng = 127.7280,
            peak = "22:00 ~ 02:00",
            tip = "명동 먹자골목·대학가 수요"
        ),
        Hotspot(
            id = "wonju",
            name = "원주 중앙로",
            area = "강원 원주시",
            city = "강원",
            lat = 37.3497,
            lng = 127.9510,
            peak = "22:00 ~ 02:00",
            tip = "원주 중앙로 상권, 군부대·학원가 수요"
        ),
        Hotspot(
            id = "gangneung",
            name = "강릉",
            area = "강원 강릉시",
            city = "강원",
            lat = 37.7640,
            lng = 128.9000,
            peak = "21:30 ~ 01:30",
            tip = "강릉역·중앙시장 상권, 관광 시즌 수요"
        ),
        Hotspot(
            id = "sokcho",
            name = "속초",
            area = "강원 속초시",
            city = "강원",
            lat = 38.2050,
            lng = 128.5940,
            peak = "21:30 ~ 01:30",
            tip = "속초 중앙시장·해변 상권, 관광객 수요"
        ),
        Hotspot(
            id = "jeonju-hanok",
            name = "전주 한옥마을 · 객리단",
            area = "전북 전주시",
            city = "전북",
            lat = 35.8150,
            lng = 127.1530,
            peak = "22:00 ~ 02:00",
            tip = "한옥마을·객리단길 상권, 관광 수요"
        ),
        Hotspot(
            id = "jeonju-daehak",
            name = "전주 대학로",
            area = "전북 전주시",
            city = "전북",
            lat = 35.8468,
            lng = 127.1299,
            peak = "22:00 ~ 03:00",
            tip = "대학가 술집 밀집, 젊은 층 심야 이동 많음"
        ),
        Hotspot(
            id = "iksan",
            name = "익산",
            area = "전북 익산시",
            city = "전북",
            lat = 35.9450,
            lng = 126.9570,
            peak = "21:30 ~ 01:30",
            tip = "익산역·시내 상권 수요"
        ),
        Hotspot(
            id = "gunsan",
            name = "군산 나운동",
            area = "전북 군산시",
            city = "전북",
            lat = 35.9624,
            lng = 126.6970,
            peak = "21:30 ~ 01:30",
            tip = "군산역·시내 상권, 산단 야근 수요도 있음"
        ),
        Hotspot(
            id = "suncheon",
            name = "순천",
            area = "전남 순천시",
            city = "전남",
            lat = 34.9450,
            lng = 127.5030,
            peak = "21:30 ~ 01:30",
            tip = "순천역·신대지구 상권 수요"
        ),
        Hotspot(
            id = "yeosu",
            name = "여수",
            area = "전남 여수시",
            city = "전남",
            lat = 34.7500,
            lng = 127.7450,
            peak = "22:00 ~ 02:30",
            tip = "여수엑스포·이순신광장 상권, 관광 수요"
        ),
        Hotspot(
            id = "mokpo",
            name = "목포",
            area = "전남 목포시",
            city = "전남",
            lat = 34.7930,
            lng = 126.3860,
            peak = "21:30 ~ 01:30",
            tip = "목포역·평화광장 상권 수요"
        ),
        Hotspot(
            id = "pohang",
            name = "포항",
            area = "경북 포항시",
            city = "경북",
            lat = 36.0350,
            lng = 129.3650,
            peak = "22:00 ~ 02:00",
            tip = "죽도시장·중앙상가 상권, 철강 야근 수요도 있음"
        ),
        Hotspot(
            id = "gyeongju",
            name = "경주 중앙시장",
            area = "경북 경주시",
            city = "경북",
            lat = 35.8440,
            lng = 129.2064,
            peak = "21:30 ~ 01:30",
            tip = "경주 시내 상권·관광 수요"
        ),
        Hotspot(
            id = "gumi",
            name = "구미",
            area = "경북 구미시",
            city = "경북",
            lat = 36.1280,
            lng = 128.3340,
            peak = "22:00 ~ 02:00",
            tip = "구미역·산단 배후 상권, 야근 콜 비중 높음"
        ),
        Hotspot(
            id = "andong",
            name = "안동",
            area = "경북 안동시",
            city = "경북",
            lat = 36.5680,
            lng = 128.7290,
            peak = "21:30 ~ 01:00",
            tip = "안동역·중앙시장 상권 수요"
        ),
        Hotspot(
            id = "changwon",
            name = "창원 상남동",
            area = "경남 창원시",
            city = "경남",
            lat = 35.2250,
            lng = 128.6810,
            peak = "22:00 ~ 02:30",
            tip = "상남동 먹자골목·유흥가, 창원 대표 콜밭"
        ),
        Hotspot(
            id = "gimhae",
            name = "김해",
            area = "경남 김해시",
            city = "경남",
            lat = 35.2340,
            lng = 128.8890,
            peak = "21:30 ~ 01:30",
            tip = "김해시청·내외동 상권 수요"
        ),
        Hotspot(
            id = "jinju",
            name = "진주",
            area = "경남 진주시",
            city = "경남",
            lat = 35.1960,
            lng = 128.0850,
            peak = "21:30 ~ 01:30",
            tip = "진주 중앙시장·대학가 상권 수요"
        ),
        Hotspot(
            id = "yangsan",
            name = "양산",
            area = "경남 양산시",
            city = "경남",
            lat = 35.3360,
            lng = 129.0370,
            peak = "21:30 ~ 01:30",
            tip = "양산역·물금 상권, 부산 경계 이동 수요"
        ),
        Hotspot(
            id = "jeju-yeon",
            name = "제주 연동 · 노형",
            area = "제주 제주시",
            city = "제주",
            lat = 33.4861,
            lng = 126.4907,
            peak = "22:00 ~ 03:00",
            tip = "연동 먹자골목·호텔가, 관광객 심야 수요"
        ),
        Hotspot(
            id = "seogwipo",
            name = "서귀포",
            area = "제주 서귀포시",
            city = "제주",
            lat = 33.2540,
            lng = 126.5600,
            peak = "21:30 ~ 02:00",
            tip = "서귀포 시내·올레시장 상권, 관광 수요"
        )
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
