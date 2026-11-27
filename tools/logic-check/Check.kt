import com.youngsu.daeriledger.data.DcbBackup
import com.youngsu.daeriledger.data.Hotspots
import com.youngsu.daeriledger.data.KakaoReverse
import com.youngsu.daeriledger.data.MySpot
import com.youngsu.daeriledger.data.PaymentType
import com.youngsu.daeriledger.data.Payout
import com.youngsu.daeriledger.data.PlaceNamer
import com.youngsu.daeriledger.data.PlaceTarget
import com.youngsu.daeriledger.data.Platforms
import com.youngsu.daeriledger.data.PublicSpot
import com.youngsu.daeriledger.data.Ride
import com.youngsu.daeriledger.data.RideSpotGate
import com.youngsu.daeriledger.data.ServiceType
import com.youngsu.daeriledger.data.SpotAggregator
import com.youngsu.daeriledger.data.SpotReport
import com.youngsu.daeriledger.data.SpotTimeBands
import com.youngsu.daeriledger.location.LocationPolicy
import com.youngsu.daeriledger.ui.widget.CalendarGrid
import com.youngsu.daeriledger.ui.widget.PressTracker
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.Money
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

var failures = 0
fun expect(name: String, actual: Any?, expected: Any?) {
    if (actual == expected) println("PASS  $name = $actual")
    else { failures++; println("FAIL  $name = $actual (expected $expected)") }
}

fun main() {
    val d = LocalDate.of(2026, 11, 23)
    fun ts(h: Int, m: Int) = BizTime.millisOf(d, LocalTime.of(h, m))

    // 영업일 기준 (기본 12시)
    expect("02:30@cutoff12", BizTime.businessDayOf(ts(2, 30), 12), d.minusDays(1))
    expect("11:59@cutoff12", BizTime.businessDayOf(ts(11, 59), 12), d.minusDays(1))
    expect("12:00@cutoff12", BizTime.businessDayOf(ts(12, 0), 12), d)
    expect("23:59@cutoff12", BizTime.businessDayOf(ts(23, 59), 12), d)
    expect("00:30@cutoff0", BizTime.businessDayOf(ts(0, 30), 0), d)
    expect("05:00@cutoff6", BizTime.businessDayOf(ts(5, 0), 6), d.minusDays(1))
    expect("06:00@cutoff6", BizTime.businessDayOf(ts(6, 0), 6), d)

    // 수수료 계산 — 카카오T·티맵·핸들은 입력값이 '수수료 차감 후 금액', 총매출은 역산
    val kakao = Platforms.byId("kakao")
    val tmap = Platforms.byId("tmap")
    val handle = Platforms.byId("handle")
    val roji = Platforms.byId("roji")
    expect("카카오T 기본수수료", kakao.defaultFeePercent, 20.0)
    expect("카카오T 차감후금액", kakao.defaultNetShown, true)
    expect("티맵 기본수수료", tmap.defaultFeePercent, 20.0)
    expect("티맵 차감후금액", tmap.defaultNetShown, true)
    expect("핸들 기본수수료", handle.defaultFeePercent, 20.0)
    expect("핸들 차감후금액", handle.defaultNetShown, true)
    expect("로지 기본수수료", roji.defaultFeePercent, 20.0)
    expect("로지 차감후금액", roji.defaultNetShown, false)

    // 20,000원 입력(받은 금액) → 총매출 25,000 / 수수료 5,000 / 순수익 18,500
    expect("grossFromNet(20000,20)", Payout.grossFromNet(20_000, 20.0), 25_000)
    for (p in listOf(kakao, tmap, handle)) {
        val r = Payout.calculate(
            input = 20_000,
            tip = 0,
            feePercent = p.defaultFeePercent,
            tipFeePercent = p.defaultFeePercent,
            insurancePerCall = 1_500,
            employmentInsurancePercent = 0.0,
            netShown = p.defaultNetShown
        )
        expect("${p.label} 20,000 총매출", r.gross, 25_000)
        expect("${p.label} 20,000 총액", r.total, 25_000)
        expect("${p.label} 20,000 수수료", r.fee, 5_000)
        expect("${p.label} 20,000 순수익", r.net, 18_500)
    }

    // 팁도 받은 금액을 역산한다 (v1.0.13 부터 플랫폼 구분 없이 같은 규칙 — 아래 블록)
    val tippedLegacy = Payout.calculate(20_000, 3_000, 20.0, 20.0, 1_500, 0.0 , netShown = true)
    expect("역산+팁 총액", tippedLegacy.total, 28_750)
    expect("역산+팁 수수료", tippedLegacy.fee, 5_750)
    expect("역산+팁 순수익", tippedLegacy.net, 21_500)

    // 역산 반올림 (33,333 / 0.8 = 41,666.25)
    val rounded = Payout.calculate(33_333, 0, 20.0, 20.0, 1_500, 0.0 , netShown = true)
    expect("역산 반올림 총매출", rounded.gross, 41_666)
    expect("역산 반올림 수수료", rounded.fee, 8_333)
    expect("역산 반올림 순수익", rounded.net, 31_833)

    // 수수료율 0% 면 역산해도 입력값 그대로
    val noFee = Payout.calculate(20_000, 0, 0.0, 0.0, 1_500, 0.0 , netShown = true)
    expect("0% 역산 총매출", noFee.gross, 20_000)
    expect("0% 역산 수수료", noFee.fee, 0)
    expect("0% 역산 순수익", noFee.net, 18_500)

    // 고용·산재 1% 는 총매출 기준: 25,000 × 1% = 250
    val emp = Payout.calculate(20_000, 0, 20.0, 20.0, 1_500, 1.0 , netShown = true)
    expect("역산 고용·산재 보험", emp.insurance, 1_750)
    expect("역산 고용·산재 순수익", emp.net, 18_250)

    // 총매출을 직접 입력하는 플랫폼(로지·콜마너·아이콘·개인콜)
    val grossMode = Payout.calculate(25_000, 0, 20.0, 20.0, 1_500, 0.0)
    expect("로지 25,000 총매출", grossMode.gross, 25_000)
    expect("로지 25,000 수수료", grossMode.fee, 5_000)
    expect("로지 25,000 순수익", grossMode.net, 18_500)

    val a = Payout.calculate(25_000, 0, 0.0, 0.0, 1_500, 0.0)
    expect("개인콜 25,000 수수료", a.fee, 0)
    expect("개인콜 25,000 보험", a.insurance, 1_500)
    expect("개인콜 25,000 순수익", a.net, 23_500)

    val c = Payout.calculate(25_000, 3_000, 20.0, 20.0, 1_500, 0.0)
    expect("팁 포함 총액(팁 3,000→3,750)", c.total, 28_750)
    expect("팁 포함 수수료(5,000+750)", c.fee, 5_750)
    expect("팁 포함 순수익", c.net, 21_500)

    val e = Payout.calculate(25_000, 0, 0.0, 0.0, 1_500, 1.0 )
    expect("고용·산재 1% 보험", e.insurance, 1_750)
    expect("고용·산재 1% 순수익", e.net, 23_250)

    // v1.0.20 — 건당 보험료는 카카오T·티맵에 붙지 않는다 (3.3% 세금만)
    expect("카카오T 건당보험 안 뗌", Platforms.chargesPerCallInsurance("kakao"), false)
    expect("티맵 건당보험 안 뗌", Platforms.chargesPerCallInsurance("tmap"), false)
    expect("핸들 건당보험 뗌", Platforms.chargesPerCallInsurance("handle"), true)
    expect("로지 건당보험 뗌", Platforms.chargesPerCallInsurance("roji"), true)
    expect("콜마너 건당보험 뗌", Platforms.chargesPerCallInsurance("colmaner"), true)
    expect("아이콘 건당보험 뗌", Platforms.chargesPerCallInsurance("icon"), true)
    expect("개인콜 건당보험 뗌", Platforms.chargesPerCallInsurance("personal"), true)
    expect("카카오T 적용 건당 0", Payout.perCallInsuranceFor("kakao", 1_168), 0)
    expect("티맵 적용 건당 0", Payout.perCallInsuranceFor("tmap", 2_500), 0)
    expect("로지 적용 건당 설정값", Payout.perCallInsuranceFor("roji", 1_168), 1_168)

    // ======================================== v1.0.32 — 프로그램 7종 추가 + 체크 필터
    // (1) 프로그램 순서 — 아이콘과 개인콜 사이에 신규 7종
    val newIds = listOf(
        "daegusarang", "daegucitizen", "daegusejong", "trincall", "superdrive", "openmile", "byuldaeri"
    )
    expect("프로그램 수", Platforms.ALL.size, 14)
    expect(
        "프로그램 순서",
        Platforms.ALL_IDS,
        listOf(
            "kakao", "tmap", "handle", "roji", "colmaner", "icon",
            "daegusarang", "daegucitizen", "daegusejong", "trincall", "superdrive", "openmile", "byuldaeri",
            "personal"
        )
    )
    expect("아이콘 다음이 대구사랑", Platforms.ALL_IDS[Platforms.ALL_IDS.indexOf("icon") + 1], "daegusarang")
    expect("개인콜 바로 앞이 별대리", Platforms.ALL_IDS[Platforms.ALL_IDS.indexOf("personal") - 1], "byuldaeri")
    expect("신규 7종 이름", newIds.map { Platforms.label(it) },
        listOf("대구사랑", "대구시민", "대구세종", "트리콜", "수퍼드라이버", "오픈마일", "별대리"))
    expect("프로그램 id 중복 없음", Platforms.ALL_IDS.distinct().size, Platforms.ALL.size)

    // (2) 신규 7종의 기본 정산 규칙 = 로지·콜마너·아이콘과 같은 20% 차감(총매출 모드)
    for (id in newIds) {
        val p = Platforms.byId(id)
        expect("${p.label} 기본수수료", p.defaultFeePercent, 20.0)
        expect("${p.label} 총매출 모드", p.defaultNetShown, false)
        expect("${p.label} 건당보험 뗌", Platforms.chargesPerCallInsurance(id), true)
        val r = Payout.calculate(25_000, 0, p.defaultFeePercent, 20.0, 1_500, 0.0, netShown = p.defaultNetShown)
        expect("${p.label} 25,000 수수료", r.fee, 5_000)
        expect("${p.label} 25,000 순수익", r.net, 18_500)
    }

    // (3) 체크한 프로그램만 보인다 — 순서는 그대로
    val defaults = Platforms.DEFAULT_ENABLED_IDS.toSet()
    expect("기본 체크 7종", Platforms.DEFAULT_ENABLED_IDS,
        listOf("kakao", "tmap", "handle", "roji", "colmaner", "icon", "personal"))
    expect("기본 체크 화면 순서", Platforms.visible(defaults).map { it.id }, Platforms.DEFAULT_ENABLED_IDS)
    expect("기본 체크 개수", Platforms.visible(defaults).size, 7)
    expect("신규 7종은 기본 미체크", Platforms.visible(defaults).none { it.id in newIds }, true)
    val withDaegu = defaults + "daegusarang"
    val visibleDaegu = Platforms.visible(withDaegu).map { it.id }
    expect("대구사랑 체크 개수", visibleDaegu.size, 8)
    expect("대구사랑이 아이콘과 개인콜 사이",
        visibleDaegu[visibleDaegu.indexOf("icon") + 1], "daegusarang")
    expect("전체 체크 = 14종 순서 그대로", Platforms.visible(Platforms.ALL_IDS.toSet()).map { it.id }, Platforms.ALL_IDS)
    expect("한 개만 체크", Platforms.visible(setOf("personal")).map { it.label }, listOf("개인콜"))

    // (4) 체크를 꺼도 과거 기록·통계 이름은 그대로 남는다 (저장 id 로 이름을 읽는다)
    val hiddenRide = Ride(
        businessDay = d, timeMillis = ts(20, 0), platformId = "daegusarang",
        service = ServiceType.DAERI, payment = PaymentType.CASH,
        gross = 25_000, feePercent = 20.0, feeAmount = 5_000, tip = 0, insuranceAmount = 1_500
    )
    expect("체크 끈 프로그램 기록 이름", hiddenRide.platformLabel, "대구사랑")
    expect("체크 끈 프로그램 기록 순수익", hiddenRide.net, 18_500)
    expect("모르는 옛 id 라벨", Platforms.label("legacy-app"), "카카오T")

    // (5) 수정 화면 — 체크를 끈 프로그램도 그 기록을 열면 칩에 함께 보인다
    expect("꺼진 프로그램 임시 포함",
        Platforms.visibleWith("daegusarang", defaults).map { it.id },
        listOf("kakao", "tmap", "handle", "roji", "colmaner", "icon", "daegusarang", "personal"))
    expect("새 기록엔 임시 포함 없음", Platforms.visibleWith(null, defaults).size, 7)
    expect("켜진 프로그램은 한 번만", Platforms.visibleWith("kakao", defaults).size, 7)
    expect("모르는 id 는 임시 포함 안 함", Platforms.visibleWith("??", defaults).size, 7)

    // (6) 마지막 한 개는 끌 수 없다
    expect("여러 개일 때 해제", Platforms.toggleEnabled(defaults, "tmap", false)?.contains("tmap"), false)
    expect("마지막 한 개 해제 불가", Platforms.toggleEnabled(setOf("kakao"), "kakao", false), null)
    expect("꺼진 것 다시 켜기", Platforms.toggleEnabled(defaults, "daegusarang", true)?.contains("daegusarang"), true)
    expect("모르는 id 토글 무시", Platforms.toggleEnabled(defaults, "??", true), defaults)

    // (7) 저장값 정리 — 빈 값·모르는 값뿐이면 기본 7종으로 되돌린다
    expect("저장값 없음 → 기본 7종", Platforms.normalizeEnabled(null), defaults)
    expect("저장값 비어 있음 → 기본 7종", Platforms.normalizeEnabled(emptySet()), defaults)
    expect("모르는 id 만 있으면 기본 7종", Platforms.normalizeEnabled(setOf("???")), defaults)
    expect("신규 id 저장값 유지", Platforms.normalizeEnabled(setOf("personal", "superdrive")),
        setOf("personal", "superdrive"))

    // 사용자 캡처(카카오T 3:06) — 받은 36,000 → 총매출 45,000 · 수수료 9,000 · 보험&세금 1,485(3.3%) · 순수익 34,515
    val kakaoShot = Payout.calculate(
        input = 36_000, tip = 0, feePercent = 20.0, tipFeePercent = 20.0,
        insurancePerCall = Payout.perCallInsuranceFor("kakao", 1_168),
        employmentInsurancePercent = 3.3, netShown = true
    )
    expect("캡처 카카오 총매출", kakaoShot.gross, 45_000)
    expect("캡처 카카오 수수료", kakaoShot.fee, 9_000)
    expect("캡처 카카오 보험&세금", kakaoShot.insurance, 1_485)
    expect("캡처 카카오 순수익", kakaoShot.net, 34_515)
    expect("캡처 세금 몫", Payout.employmentOf(45_000, 3.3), 1_485)

    // 같은 금액을 로지(건당 보험 있음)로 입력하면 건당 1,168 + 3.3% 1,485 가 함께 빠진다
    val rojiShot = Payout.calculate(
        input = 45_000, tip = 0, feePercent = 20.0, tipFeePercent = 20.0,
        insurancePerCall = Payout.perCallInsuranceFor("roji", 1_168),
        employmentInsurancePercent = 3.3
    )
    expect("로지 보험&세금 합계", rojiShot.insurance, 2_653)
    expect("로지 순수익", rojiShot.net, 33_347)

    // 수정 화면 왕복 — 저장된 합계에서 건당 몫만 되돌린다 (3.3% 두 번 붙지 않게)
    expect("건당 되돌리기 카카오", Payout.perCallFromStored(45_000, 1_485, 3.3), 0)
    expect("건당 되돌리기 로지", Payout.perCallFromStored(45_000, 2_653, 3.3), 1_168)
    expect("건당 되돌리기 세금 0%", Payout.perCallFromStored(45_000, 1_485, 0.0), 1_485)
    val rojiPerCall = Payout.perCallFromStored(rojiShot.gross, rojiShot.insurance, 3.3)
    val rojiAgain = Payout.calculate(
        input = rojiShot.gross, tip = 0, feePercent = 20.0, tipFeePercent = 20.0,
        insurancePerCall = Payout.perCallInsuranceFor("roji", rojiPerCall),
        employmentInsurancePercent = 3.3
    )
    expect("로지 수정 왕복 보험&세금 유지", rojiAgain.insurance, rojiShot.insurance)
    expect("로지 수정 왕복 순수익 유지", rojiAgain.net, rojiShot.net)

    val f = Payout.calculate(33_333, 0, 20.0, 20.0, 1_500, 0.0)
    expect("33,333 수수료 반올림", f.fee, 6_667)
    expect("33,333 순수익", f.net, 25_166)

    // 저장된 총매출·수수료로 순수익이 그대로 유지되는지 (마이그레이션 전후)
    val before = Payout.calculate(20_000, 0, 0.0, 0.0, 1_500, 0.0)
    val after = Payout.calculate(20_000, 0, 20.0, 20.0, 1_500, 0.0 , netShown = true)
    expect("마이그레이션 후 순수익 유지", after.net, before.net)
    val migratedNet = after.gross + 0 - after.fee - after.insurance
    expect("저장값 기준 순수익", migratedNet, 18_500)

    // 금액 표기
    expect("won(184500)", Money.won(184500), "184,500원")
    expect("won(-5000)", Money.won(-5000), "-5,000원")
    expect("compact(184500)", Money.compact(184500), "18.5만")
    expect("compact(12000)", Money.compact(12000), "1.2만")
    expect("parseAmount('25,000원')", Money.parseAmount("25,000원"), 25000)

    // 거리 (강남 ↔ 해운대 약 330km)
    val km = Hotspots.distanceKm(37.4979, 127.0276, 35.1631, 129.1636)
    println("INFO  강남-해운대 = ${"%.1f".format(km)}km")
    expect("거리 범위", km in 300.0..360.0, true)
    expect("formatDistance(0.4)", Hotspots.formatDistance(0.4), "400m")
    expect("formatDistance(12.34)", Hotspots.formatDistance(12.34), "12.3km")

    // 콜밭 목록 자체 점검 — v1.0.9: 수도권 세부 콜밭까지 318곳 (직접 정리한 기본 목록)
    val spots = Hotspots.CURATED
    expect("콜밭 수", spots.size, 318)
    expect("콜밭 id 중복 없음", spots.map { it.id }.distinct().size, spots.size)
    expect("콜밭 이름 중복 없음", spots.map { it.name }.distinct().size, spots.size)
    expect("모든 콜밭 피크 표기", spots.all { it.peak.isNotBlank() }, true)
    expect("모든 콜밭 tip 표기", spots.all { it.tip.isNotBlank() }, true)
    expect("서울 콜밭 수", spots.count { it.city == "서울" }, 118)
    expect("경기 콜밭 수", spots.count { it.city == "경기" }, 120)
    expect("인천 콜밭 수", spots.count { it.city == "인천" }, 22)
    expect("수도권 콜밭 수", spots.count { it.city in listOf("서울", "경기", "인천") }, 260)
    expect("좌표 범위(한국)", spots.all { it.lat in 33.0..38.9 && it.lng in 124.0..132.0 }, true)
    val hannam = spots.firstOrNull { it.id == "hannam-ogeori" }
    expect("한남오거리 이름", hannam?.name, "한남오거리")
    expect("한남오거리 좌표", hannam?.let { "%.4f,%.4f".format(it.lat, it.lng) }, "37.5334,127.0076")
    expect("한남오거리 검색", spots.count { it.matches("한남오거리") }, 1)

    // 300m 안에 겹치는 콜밭이 없어야 한다
    var closestM = Double.MAX_VALUE
    var closestPair = ""
    for (i in spots.indices) for (j in i + 1 until spots.size) {
        val m = Hotspots.distanceKm(spots[i].lat, spots[i].lng, spots[j].lat, spots[j].lng) * 1000
        if (m < closestM) {
            closestM = m
            closestPair = "${spots[i].name} ↔ ${spots[j].name}"
        }
    }
    println("INFO  가장 가까운 콜밭 = ${closestM.toInt()}m ($closestPair)")
    expect("300m 이내 중복 없음", closestM >= 300, true)

    // v1.0.14 — 카카오맵 즐겨찾기에서 옮긴 콜밭(res/raw/callspots.tsv) 점검
    //   Android Context 없이 파일을 직접 읽어 데이터 자체를 검사한다.
    val tsv = java.io.File("app/src/main/res/raw/callspots.tsv")
    expect("callspots.tsv 존재", tsv.isFile, true)
    val rows = if (tsv.isFile) tsv.readLines(Charsets.UTF_8).filter { it.isNotBlank() } else emptyList()
    val cols = rows.map { it.split('\t') }
    expect("카카오 콜밭 수(콜포인트1 464 + 2 441)", cols.size, 905)
    expect("카카오 콜밭 8칸", cols.all { it.size == 8 }, true)
    expect("카카오 콜밭 id 중복 없음", cols.map { it[0] }.distinct().size, cols.size)
    expect("카카오 콜밭 이름·지역 채움", cols.all { it[2].isNotBlank() && it[3].isNotBlank() && it[4].isNotBlank() }, true)
    expect("카카오 콜밭 출처 값", cols.all { it[1] == "기사 추천" }, true)
    expect("카카오 콜밭 시·도 값", cols.all { it[3] in Hotspots.CITIES }, true)
    val kLat = cols.map { it[5].toDouble() }
    val kLng = cols.map { it[6].toDouble() }
    expect("카카오 콜밭 좌표 범위(한국)",
        kLat.all { it in 33.0..38.9 } && kLng.all { it in 124.0..132.0 }, true)
    // v1.0.15 — 탁송 콜밭은 목록에서 뺐다 (기본 318 + 기사 추천 905 = 1,223곳)
    expect("탁송 콜밭 없음", cols.none { it[1] == "탁송" }, true)
    expect("콜밭 전체 수(기본 318 + 905)", spots.size + cols.size, 1223)
    // 같은 이름이 200m 안에 두 번 있으면 안 된다
    var nearDup = ""
    var nearMin = Double.MAX_VALUE
    for (i in cols.indices) for (j in i + 1 until cols.size) {
        val a = cols[i][2].replace(" ", "")
        val b = cols[j][2].replace(" ", "")
        if (!(a == b || a.contains(b) || b.contains(a))) continue
        val m = Hotspots.distanceKm(kLat[i], kLng[i], kLat[j], kLng[j]) * 1000
        if (m < nearMin) { nearMin = m; nearDup = "${cols[i][2]} ↔ ${cols[j][2]}" }
    }
    println("INFO  같은 이름 최근접 = ${if (nearMin == Double.MAX_VALUE) "없음" else "${nearMin.toInt()}m ($nearDup)"}")
    expect("카카오 콜밭 200m 같은 이름 중복 없음", nearMin >= 200, true)
    // 기본 콜밭과도 300m 안에 같은 이름으로 겹치지 않아야 한다
    var clash = ""
    for (k in cols.indices) for (c in spots) {
        val a = cols[k][2].replace(" ", "")
        val b = c.name.replace(" ", "")
        if (!(a == b || a.contains(b) || b.contains(a))) continue
        if (Hotspots.distanceKm(kLat[k], kLng[k], c.lat, c.lng) * 1000 < 300) clash = "${cols[k][2]} ↔ ${c.name}"
    }
    expect("기본 콜밭과 300m 같은 이름 겹침 없음", clash, "")

    // v1.0.15 — `콜포인트1`·`콜포인트2` 를 `기사 추천` 하나로 합치고, 출처 칩은 없애고 한 목록으로
    expect("예전 출처 이름 정리(콜포인트1)", Hotspots.normalizeSource("콜포인트1"), "기사 추천")
    expect("예전 출처 이름 정리(콜포인트2)", Hotspots.normalizeSource("콜포인트2"), "기사 추천")
    expect("중간 이름 정리(기사님들 포인트)", Hotspots.normalizeSource("기사님들 포인트"), "기사 추천")
    expect("출처 이름 그대로", Hotspots.normalizeSource("기사 추천"), "기사 추천")
    expect("탁송 출처는 그대로", Hotspots.normalizeSource("탁송"), "탁송")
    expect("구분 대리 → 출처", Hotspots.sourceFor(ServiceType.DAERI), "기사 추천")
    expect("구분 탁송 → 출처", Hotspots.sourceFor(ServiceType.TAKSONG), "탁송")
    expect("시·도 찾기(서울)", Hotspots.cityOfArea("서울 강남구 역삼동"), "서울")
    expect("시·도 찾기(경기)", Hotspots.cityOfArea("경기 성남시 분당구 정자동"), "경기")
    expect("시·도 찾기(정식 이름)", Hotspots.cityOfArea("경상남도 창원시"), "경남")
    expect("시·도 찾기(없음)", Hotspots.cityOfArea("정자역"), "")

    // v1.0.15 — 내가 추가한 콜밭이 콜밭 목록 항목으로 바뀌는지
    val myDaeri = Hotspots.fromMySpot(
        MySpot(
            id = 7, name = "정자역 사거리", lat = 37.3665, lng = 127.1085,
            peak = "23:00~02:00", memo = "2차 골목", createdAt = 1,
            service = ServiceType.DAERI, area = "경기 성남시 분당구 정자동"
        )
    )
    expect("내 콜밭 id", myDaeri.id, "my-7")
    expect("내 콜밭 출처(대리)", myDaeri.source, "기사 추천")
    expect("내 콜밭 표시", myDaeri.mine, true)
    expect("내 콜밭 이름", myDaeri.name, "정자역 사거리")
    expect("내 콜밭 시·도", myDaeri.city, "경기")
    expect("내 콜밭 피크", myDaeri.peak, "23:00~02:00")
    expect("내 콜밭 메모", myDaeri.tip, "2차 골목")
    expect("내 콜밭 좌표 있음", myDaeri.hasLocation, true)
    expect("내 콜밭 검색(이름)", myDaeri.matches("정자"), true)
    expect("내 콜밭 검색(배지)", myDaeri.matches("내 콜밭"), true)
    val myTaksong = Hotspots.fromMySpot(
        MySpot(id = 8, name = "KCar 분당", service = ServiceType.TAKSONG)
    )
    expect("내 콜밭 출처(탁송)", myTaksong.source, "탁송")
    expect("좌표 없는 내 콜밭", myTaksong.hasLocation, false)

    // 달력 그리드 터치 판정 (CalendarMonthView 가 쓰는 계산 — 눌린 칸 = 그려진 칸)
    val nov = YearMonth.of(2026, 11)
    expect("2026-11-01 요일", nov.atDay(1).dayOfWeek.value, 7) // 일요일
    expect("11월 앞 빈칸 수", CalendarGrid.leadingDays(nov), 0)
    expect("그리드 첫 칸", CalendarGrid.startDate(nov), nov.atDay(1))
    expect("2026-08-01 요일(토)", YearMonth.of(2026, 8).atDay(1).dayOfWeek.value, 6)
    expect("8월 앞 빈칸 수(토요일 시작)", CalendarGrid.leadingDays(YearMonth.of(2026, 8)), 6)
    expect("2026-07-01 요일(수)", YearMonth.of(2026, 7).atDay(1).dayOfWeek.value, 3)
    expect("7월 앞 빈칸 수", CalendarGrid.leadingDays(YearMonth.of(2026, 7)), 3)
    expect("7월 그리드 첫 칸", CalendarGrid.startDate(YearMonth.of(2026, 7)), LocalDate.of(2026, 6, 28))
    expect("11월 6번째 줄 첫 칸", CalendarGrid.dateAt(35, nov), LocalDate.of(2026, 12, 6))
    expect("12월 6일은 11월 아님", CalendarGrid.isInMonth(LocalDate.of(2026, 12, 6), nov), false)

    // 그리드 크기: 헤더 26dp · 칸 54dp · 6줄 (density 2 기준 1080px 폭)
    val gridW = 1080
    val header = 78f
    val cellH = 162f

    // 1) 칸 한가운데를 누르면 그 칸의 날짜가 나온다 (모든 칸 · 좌우 패딩 있는 경우 포함)
    var cellMismatch = 0
    var tapMiss = 0
    for (padLeft in listOf(0, 36)) {
        for (padTop in listOf(0, 24)) {
            for (index in 0 until CalendarGrid.CELLS) {
                val b = CalendarGrid.cellBounds(
                    index, gridW, padLeft, padTop, header, cellH, 4f
                )
                val cx = (b[0] + b[2]) / 2f
                val cy = (b[1] + b[3]) / 2f
                val tapped = CalendarGrid.dateAt(
                    cx, cy, nov, gridW, padLeft, padTop, header, cellH
                )
                if (tapped != CalendarGrid.dateAt(index, nov)) cellMismatch++
                val tappedIndex = CalendarGrid.indexAt(
                    cx, cy, gridW, padLeft, padTop, header, cellH
                )
                if (tappedIndex != index) tapMiss++
            }
        }
    }
    expect("모든 칸 가운데 → 같은 날짜", cellMismatch, 0)
    expect("모든 칸 가운데 → 같은 칸 번호", tapMiss, 0)

    // 2) 칸 경계 바로 안쪽도 그 칸으로 잡힌다 (칸 사이 4dp 인셋에도 터치는 넓게)
    val b0 = CalendarGrid.cellBounds(0, gridW, 0, 0, header, cellH, 4f)
    expect(
        "0번 칸 왼쪽 위 안쪽",
        CalendarGrid.dateAt(b0[0] + 0.5f, b0[1] + 0.5f, nov, gridW, 0, 0, header, cellH),
        LocalDate.of(2026, 11, 1)
    )
    expect(
        "0번 칸 오른쪽 아래 안쪽",
        CalendarGrid.dateAt(b0[2] - 0.5f, b0[3] - 0.5f, nov, gridW, 0, 0, header, cellH),
        LocalDate.of(2026, 11, 1)
    )
    // 3) 칸 바깥(헤더 줄 · 그리드 아래 · 오른쪽 끝)은 날짜가 선택되지 않는다
    expect("요일 헤더 누름 → null", CalendarGrid.dateAt(100f, header / 2f, nov, gridW, 0, 0, header, cellH), null)
    expect(
        "6줄 아래 누름 → null",
        CalendarGrid.dateAt(100f, header + cellH * 6 + 1f, nov, gridW, 0, 0, header, cellH),
        null
    )
    expect("오른쪽 끝 밖 → null", CalendarGrid.dateAt(gridW + 1f, header + 10f, nov, gridW, 0, 0, header, cellH), null)
    expect("음수 x → null", CalendarGrid.dateAt(-1f, header + 10f, nov, gridW, 0, 0, header, cellH), null)
    // 4) 마지막 줄 마지막 칸(오른쪽 아래)까지 정확히 잡힌다
    expect(
        "41번 칸 = 12월 12일",
        CalendarGrid.dateAt(41, nov),
        LocalDate.of(2026, 12, 12)
    )
    expect(
        "41번 칸 가운데 터치",
        CalendarGrid.indexAt(gridW * 6.5f / 7f, header + cellH * 5.5f, gridW, 0, 0, header, cellH),
        41
    )
    // 5) 2026년 모든 달 · 모든 칸이 자기 날짜로 되돌아온다
    var monthMismatch = 0
    for (m in 1..12) {
        val ym = YearMonth.of(2026, m)
        for (index in 0 until CalendarGrid.CELLS) {
            val b = CalendarGrid.cellBounds(index, gridW, 0, 0, header, cellH, 4f)
            val tapped = CalendarGrid.dateAt(
                (b[0] + b[2]) / 2f, (b[1] + b[3]) / 2f, ym, gridW, 0, 0, header, cellH
            )
            if (tapped != CalendarGrid.dateAt(index, ym)) monthMismatch++
        }
    }
    expect("2026년 12달 × 42칸 터치 왕복", monthMismatch, 0)
    // 6) 그 달 날짜는 모두 그리드 안에 한 번씩 들어 있다
    val novDays = (0 until CalendarGrid.CELLS).map { CalendarGrid.dateAt(it, nov) }
    expect("11월 30일 전부 포함", novDays.count { CalendarGrid.isInMonth(it, nov) }, 30)
    expect("11월 날짜 중복 없음", novDays.distinct().size, CalendarGrid.CELLS)


    // 제스처 판정 (PressTracker) — 누르면 소비되고, 떼면 그 칸이 선택된다
    val slop = 16f
    run {
        val pt = PressTracker(slop)
        val bb = CalendarGrid.cellBounds(5, gridW, 0, 0, header, cellH, 4f)
        val gx = (bb[0] + bb[2]) / 2f
        val gy = (bb[1] + bb[3]) / 2f
        val hit = CalendarGrid.indexAt(gx, gy, gridW, 0, 0, header, cellH)
        expect("DOWN 소비(그리드 안)", pt.down(gx, gy, hit), true)
        expect("누른 칸 표시", pt.pressedIndex, 5)
        expect("제자리 UP = 탭", pt.up(gx, gy), true)
        expect("UP 뒤 눌림 해제", pt.pressedIndex, -1)
        expect("탭한 칸 날짜", CalendarGrid.dateAt(hit!!, nov), LocalDate.of(2026, 11, 6))
    }
    run {
        val pt = PressTracker(slop)
        expect("DOWN(스크롤 시작)", pt.down(500f, 200f, 9), true)
        expect("슬롭 넘는 MOVE = 스크롤", pt.move(500f, 240f), true)
        expect("스크롤 중 눌림 해제", pt.pressedIndex, -1)
        expect("스크롤 뒤 UP 은 탭 아님", pt.up(500f, 240f), false)
    }
    run {
        val pt = PressTracker(slop)
        pt.down(500f, 200f, 9)
        expect("슬롭 안 MOVE", pt.move(508f, 206f), false)
        expect("슬롭 안 UP = 탭", pt.up(508f, 206f), true)
    }
    run {
        val pt = PressTracker(slop)
        expect("요일 헤더 DOWN 미소비", pt.down(100f, header / 2f, null), false)
        expect("요일 헤더 UP 탭 아님", pt.up(100f, header / 2f), false)
    }
    run {
        val pt = PressTracker(slop)
        pt.down(500f, 200f, 9)
        pt.cancel()
        expect("취소 뒤 UP 탭 아님", pt.up(500f, 200f), false)
    }
    run {
        val pt = PressTracker(slop)
        expect("DOWN 없이 UP 은 무시", pt.up(500f, 200f), false)
    }
    // 42칸 모두: 눌렀다 떼면 그 칸의 날짜가 선택된다 (그리기와 같은 좌표)
    var gestureMismatch = 0
    for (index in 0 until CalendarGrid.CELLS) {
        val bb = CalendarGrid.cellBounds(index, gridW, 0, 0, header, cellH, 4f)
        val gx = (bb[0] + bb[2]) / 2f
        val gy = (bb[1] + bb[3]) / 2f
        val pt = PressTracker(slop)
        val hit = CalendarGrid.indexAt(gx, gy, gridW, 0, 0, header, cellH)
        if (!pt.down(gx, gy, hit) || !pt.up(gx, gy)) { gestureMismatch++; continue }
        if (CalendarGrid.dateAt(hit!!, nov) != CalendarGrid.dateAt(index, nov)) gestureMismatch++
    }
    expect("42칸 누르고 떼기 → 42칸 선택", gestureMismatch, 0)


    // 금액 넘침(Int) 회귀 검사 — v1.0.4 통계 화면에서 '수익 비중 -3%' 로 보이던 문제
    val lifeNet = 517_830_730L          // 전체 기간 실수익
    val personalNet = 416_493_580L      // 그중 개인콜 실수익
    expect("Long 비중", personalNet * 100 / lifeNet, 80L)
    val intShare = (personalNet.toInt() * 100 / lifeNet.toInt())
    expect("Int 로 계산하면 음수로 뒤집힘(예전 버그)", intShare < 0, true)
    expect("Long 금액 표기", Money.won(lifeNet), "517,830,730원")
    expect("Int 금액 표기", Money.won(lifeNet.toInt()), "517,830,730원")
    expect("Long 축약 표기", Money.compact(lifeNet), "5.2억")
    expect("Long 부호 표기", Money.signed(-lifeNet), "-517,830,730원")
    expect("콜당 평균(Long)", lifeNet / 11_867L, 43_636L)
    // 플랫폼 막대 비율도 같은 식으로 계산한다 (0..100 으로 잘림)
    expect("플랫폼 막대 %", (personalNet * 100 / lifeNet).toInt().coerceIn(2, 100), 80)


    // 대리 가계부(dcb_teras) 백업 → 대리장부 변환 규칙
    expect("플랫폼: 기타", DcbBackup.platformId("기타"), "personal")
    expect("플랫폼: 카카오T", DcbBackup.platformId("카카오T"), "kakao")
    expect("플랫폼: 티맵", DcbBackup.platformId("티맵"), "tmap")
    expect("플랫폼: 핸들", DcbBackup.platformId("핸들"), "handle")
    expect("플랫폼: 로지", DcbBackup.platformId("로지"), "roji")
    expect("플랫폼: 콜마너", DcbBackup.platformId("콜마너"), "colmaner")
    expect("플랫폼: 아이콘", DcbBackup.platformId("아이콘"), "icon")
    expect("플랫폼: 빈값", DcbBackup.platformId(null), "personal")
    expect("수수료율 65,000/13,000", DcbBackup.feePercent(65_000, 13_000), 20.0)
    expect("수수료율 25,000/5,000", DcbBackup.feePercent(25_000, 5_000), 20.0)
    expect("수수료율 33,333/6,667", DcbBackup.feePercent(33_333, 6_667), 20.0)
    expect("수수료율 10,000/2,500", DcbBackup.feePercent(10_000, 2_500), 25.0)
    expect("수수료율 요금0", DcbBackup.feePercent(0, 0), 0.0)
    expect("메모 둘 다", DcbBackup.memo("역삼", "카드결제"), "역삼 · 카드결제")
    expect("메모 하나", DcbBackup.memo("역삼", "  "), "역삼")
    expect("메모 없음", DcbBackup.memo("", null), "")
    // 새벽 콜: 원본 작업일(전날)을 그대로 영업일로 쓴다
    expect("영업일 = CALL_WORK_DATE", DcbBackup.businessDay("2021-04-07", "2021-04-08 00:02"), LocalDate.of(2021, 4, 7))
    expect("주문시각 → millis", DcbBackup.timeMillis("2021-04-08 00:02", LocalDate.of(2021, 4, 7)),
        BizTime.millisOf(LocalDate.of(2021, 4, 8), LocalTime.of(0, 2)))
    expect("시각 없으면 20시", DcbBackup.timeMillis("", LocalDate.of(2021, 4, 7)),
        BizTime.millisOf(LocalDate.of(2021, 4, 7), LocalTime.of(20, 0)))
    // 원본 한 콜(65,000 / 수수료 13,000) 이 대리장부 기록으로 그대로 옮겨지는지
    val importedNet = 65_000 + 0 - DcbBackup.feePercent(65_000, 13_000).let { _ -> 13_000 }
    expect("원본 콜 순수익", importedNet, 52_000)

    // 휴무일 — CALL_OFF_CHK = 1 인 행은 운행이 아니라 ‘그날 일하지 않았다’는 표시
    expect("휴무 표시 1", DcbBackup.isOffDay(1), true)
    expect("휴무 표시 0", DcbBackup.isOffDay(0), false)
    expect("휴무 표시 없음", DcbBackup.isOffDay(null), false)
    // 휴무 행은 날짜만 있고 주문시각이 비어 있다 → 그 작업일을 그대로 쓴다
    expect("휴무일 날짜", DcbBackup.businessDay("2023-01-05", null), LocalDate.of(2023, 1, 5))

    // v1.0.11 — 출발지 · 경유 · 도착지 경로 표기, 결제 이름(카드·포인트 → 후불 / 후불·미수 → 미수금)
    fun ride(from: String, via: String, to: String) = Ride(
        businessDay = d,
        timeMillis = ts(20, 0),
        platformId = "kakao",
        service = ServiceType.DAERI,
        payment = PaymentType.CASH,
        gross = 25_000,
        feePercent = 20.0,
        feeAmount = 5_000,
        tip = 0,
        insuranceAmount = 1_500,
        fromPlace = from,
        viaPlace = via,
        toPlace = to
    )
    expect("경로 출발→도착", ride("정자동", "", "수지구청역").routeLabel, "정자동 → 수지구청역")
    expect("경로 출발→경유→도착", ride("정자동", "광교", "수지구청역").routeLabel, "정자동 → 광교 → 수지구청역")
    expect("경로 도착만", ride("", "", "수지구청역").routeLabel, "수지구청역")
    expect("경로 출발만", ride("정자동", "", "").routeLabel, "정자동")
    expect("경로 경유만", ride("", "광교", "").routeLabel, "광교")
    expect("경로 전부 빈값", ride("", "", "").routeLabel, "")
    expect("경로 공백만", ride("  ", " ", "\t").routeLabel, "")
    expect("결제 현금", PaymentType.CASH.label, "현금")
    expect("결제 후불(카드·포인트 자리)", PaymentType.CARD.label, "후불")
    expect("결제 미수금(후불·미수 자리)", PaymentType.UNPAID.label, "미수금")
    // 저장된 id 는 그대로 — 예전 기록도 같은 결제 수단으로 읽힌다
    expect("결제 id 호환 card", PaymentType.from("card"), PaymentType.CARD)
    expect("결제 id 호환 unpaid", PaymentType.from("unpaid"), PaymentType.UNPAID)
    expect("결제 id 모르면 현금", PaymentType.from("??"), PaymentType.CASH)


    // ===== v1.0.13 — 팁·경유비는 플랫폼과 상관없이 '받은 금액'을 설정 %로 역산한다 =====
    // (1) 사용자 예시: 로지(요금 20% 차감) 45,000 + 받은 팁 10,000 (팁 20%)
    //     → 총액 57,500 · 수수료 11,500 · 순수익 46,000 (36,000 + 10,000)
    val rojiTip = Payout.calculate(45_000, 10_000, 20.0, 20.0, 0, 0.0, netShown = false)
    expect("로지+팁 요금 총매출", rojiTip.gross, 45_000)
    expect("로지+팁 팁 총액", rojiTip.tipGross, 12_500)
    expect("로지+팁 총액(45,000+12,500)", rojiTip.total, 57_500)
    expect("로지+팁 요금 수수료", rojiTip.fareFee, 9_000)
    expect("로지+팁 팁 수수료", rojiTip.tipFee, 2_500)
    expect("로지+팁 수수료 합", rojiTip.fee, 11_500)
    expect("로지+팁 순수익(36,000+10,000)", rojiTip.net, 46_000)
    val rojiTipRide = ride("신사", "", "다산").copy(
        gross = rojiTip.gross, tip = rojiTip.tipGross,
        feeAmount = rojiTip.fee, insuranceAmount = rojiTip.insurance
    )
    expect("로지+팁 Ride 총수입", rojiTipRide.total, 57_500)
    expect("로지+팁 Ride 순수익", rojiTipRide.net, 46_000)

    // (2) 설정에서 팁 %를 0%로 두면 팁에 수수료를 떼지 않는다 — 총액 55,000 · 수수료 9,000 · 순수익은 그대로 46,000
    val tipZero = Payout.calculate(45_000, 10_000, 20.0, 0.0, 0, 0.0, netShown = false)
    expect("팁 0% 총액", tipZero.total, 55_000)
    expect("팁 0% 팁 총액", tipZero.tipGross, 10_000)
    expect("팁 0% 수수료", tipZero.fee, 9_000)
    expect("팁 0% 순수익", tipZero.net, 46_000)

    // (3) 역산 플랫폼(카카오T) — 요금도 팁도 받은 금액으로 넣는다 (36,900 · 10,000 / 20%)
    val kakaoTip = Payout.calculate(36_900, 10_000, 20.0, 20.0, 0, 0.0, netShown = true)
    expect("카카오T+팁 요금 총매출", kakaoTip.gross, 46_125)
    expect("카카오T+팁 팁 총액", kakaoTip.tipGross, 12_500)
    expect("카카오T+팁 총액", kakaoTip.total, 58_625)
    expect("카카오T+팁 수수료(9,225+2,500)", kakaoTip.fee, 11_725)
    expect("카카오T+팁 순수익(36,900+10,000)", kakaoTip.net, 46_900)

    // (4) 요금 0 + 팁만 받은 콜 (개인콜) — 팁만 역산하고 보험료만 뺀다
    val tipOnly = Payout.calculate(0, 10_000, 0.0, 20.0, 1_500, 0.0, netShown = false)
    expect("팁만 총매출", tipOnly.gross, 0)
    expect("팁만 팁 총액", tipOnly.tipGross, 12_500)
    expect("팁만 수수료", tipOnly.fee, 2_500)
    expect("팁만 순수익(10,000−1,500)", tipOnly.net, 8_500)

    // (5) 요금 %와 팁 %가 다를 때 — 개인콜 요금 0% · 팁 20%
    val mixed = Payout.calculate(30_000, 10_000, 0.0, 20.0, 1_500, 0.0, netShown = false)
    expect("개인콜+팁 총액", mixed.total, 42_500)
    expect("개인콜+팁 요금 수수료", mixed.fareFee, 0)
    expect("개인콜+팁 팁 수수료", mixed.tipFee, 2_500)
    expect("개인콜+팁 순수익(30,000+받은 팁 10,000−1,500)", mixed.net, 38_500)

    // (6) 반올림 — 받은 팁 3,333 · 20% → 총액 4,166 · 수수료 833
    val tipOdd = Payout.calculate(25_000, 3_333, 20.0, 20.0, 1_500, 0.0, netShown = false)
    expect("팁 반올림 총액", tipOdd.tipGross, 4_166)
    expect("팁 반올림 팁 수수료", tipOdd.tipFee, 833)
    expect("팁 반올림 수수료 합(5,000+833)", tipOdd.fee, 5_833)

    // (7) 수정 화면 왕복 — 로지: 저장된 값을 다시 열면 적었던 '받은 팁'으로 돌아온다
    val rojiBack = Payout.inputsFrom(rojiTip.gross, rojiTip.tipGross, rojiTip.fee, 20.0, netShown = false)
    expect("로지 왕복 요금", rojiBack.amount, 45_000)
    expect("로지 왕복 팁", rojiBack.tip, 10_000)
    val rojiResaved = Payout.calculate(rojiBack.amount, rojiBack.tip, 20.0, 20.0, 0, 0.0, netShown = false)
    expect("로지 재저장 총액", rojiResaved.total, rojiTip.total)
    expect("로지 재저장 수수료", rojiResaved.fee, rojiTip.fee)
    expect("로지 재저장 순수익", rojiResaved.net, rojiTip.net)

    // (8) 수정 화면 왕복 — 카카오T(역산)
    val kakaoBack = Payout.inputsFrom(kakaoTip.gross, kakaoTip.tipGross, kakaoTip.fee, 20.0, netShown = true)
    expect("카카오T 왕복 요금", kakaoBack.amount, 36_900)
    expect("카카오T 왕복 팁", kakaoBack.tip, 10_000)
    val kakaoResaved = Payout.calculate(kakaoBack.amount, kakaoBack.tip, 20.0, 20.0, 0, 0.0, netShown = true)
    expect("카카오T 재저장 총액", kakaoResaved.total, kakaoTip.total)
    expect("카카오T 재저장 수수료", kakaoResaved.fee, kakaoTip.fee)
    expect("카카오T 재저장 순수익", kakaoResaved.net, kakaoTip.net)

    // (9) 왕복 — 개인콜(요금 0% + 팁 20%) / 반올림이 끼는 팁
    val mixedBack = Payout.inputsFrom(mixed.gross, mixed.tipGross, mixed.fee, 0.0, netShown = false)
    expect("개인콜 왕복 요금", mixedBack.amount, 30_000)
    expect("개인콜 왕복 팁", mixedBack.tip, 10_000)
    val oddBack = Payout.inputsFrom(tipOdd.gross, tipOdd.tipGross, tipOdd.fee, 20.0, netShown = false)
    expect("팁 반올림 왕복 팁", oddBack.tip, 3_333)
    val oddResaved = Payout.calculate(oddBack.amount, oddBack.tip, 20.0, 20.0, 1_500, 0.0, netShown = false)
    expect("팁 반올림 재저장 총액", oddResaved.total, tipOdd.total)
    expect("팁 반올림 재저장 수수료", oddResaved.fee, tipOdd.fee)

    // (10) 옛 기록 호환(팁에 수수료가 없던 행) — 열어도 팁이 줄지 않는다
    val legacyGrossTip = Payout.inputsFrom(45_000, 10_000, 9_000, 20.0, netShown = false)
    expect("옛 로지 요금", legacyGrossTip.amount, 45_000)
    expect("옛 로지 팁", legacyGrossTip.tip, 10_000)
    val legacyNetTip = Payout.inputsFrom(25_000, 3_000, 5_000, 20.0, netShown = true)
    expect("옛 카카오T 요금", legacyNetTip.amount, 20_000)
    expect("옛 카카오T 팁", legacyNetTip.tip, 3_000)
    expect("팁 0 왕복", Payout.inputsFrom(25_000, 0, 5_000, 20.0, netShown = true).tip, 0)
    expect("팁 0 왕복(로지)", Payout.inputsFrom(25_000, 0, 5_000, 20.0, netShown = false).tip, 0)

    // (11) 수수료율이 바뀌어도 저장값만으로 팁 수수료를 되짚는다 (저장 수수료 − 요금 몫)
    expect("팁 수수료 되짚기", Payout.tipInputFrom(45_000, 12_500, 11_500, 20.0), 10_000)
    expect("팁 수수료 되짚기(옛 행)", Payout.tipInputFrom(45_000, 10_000, 9_000, 20.0), 10_000)

    // ============================================================ 콜밭 공용 제보 (v1.0.16 → v1.0.30: 3명·50m)
    // 사용자가 정한 기준 (2026-11-27 정정): 직접 제보 등록은 **서로 다른 기기 3명 이상 · 반경 50m**,
    // 없어진 곳 신고는 v1.0.26 의 **2명** 그대로 둔다.
    val lat0 = 37.5000
    val lng0 = 127.0000
    fun report(
        device: Int,
        lat: Double = lat0,
        lng: Double = lng0,
        kind: String = SpotAggregator.KIND_SPOT,
        name: String = "테스트 콜밭",
        minutesAgo: Long = 0L,
        address: String = "서울 강남구 테헤란로",
        note: String = "",
        createdAt: Long = 1_700_000_000_000L - minutesAgo * 60_000L
    ) = SpotReport(
        name = name,
        lat = lat,
        lng = lng,
        address = address,
        note = note,
        deviceHash = "device-$device",
        createdAt = createdAt,
        kind = kind
    )
    fun removals(n: Int, lat: Double = lat0, lng: Double = lng0) =
        (1..n).map { report(100 + it, lat, lng, SpotAggregator.KIND_REMOVED, name = "테스트 콜밭") }
    fun spots(n: Int, lat: Double = lat0, lng: Double = lng0) =
        (1..n).map { report(it, lat, lng) }

    // 기준값 자체 — v1.0.30: 직접 제보·핫스팟 모두 3명, 없어진 곳 신고만 2명
    expect("직접 제보 기준 인원", SpotAggregator.MIN_REPORTERS, 3)
    expect("핫스팟 기준 인원", SpotAggregator.MIN_HOTSPOT_REPORTERS, 3)
    expect("없어진 곳 신고 기준 인원", SpotAggregator.MIN_REMOVERS, 2)
    expect("제보 묶음 반경(m)", SpotAggregator.GROUP_RADIUS_M, 50.0)
    expect("핫스팟 묶음 반경(m)", SpotAggregator.HOTSPOT_RADIUS_M, 500.0)
    expect("등록 kind", SpotAggregator.KIND_SPOT, "콜밭")
    expect("삭제 kind", SpotAggregator.KIND_REMOVED, "삭제")

    // (1) 직접 제보 — 3명이면 노출(🟡 노란 핀), 2명·1명은 아직 미노출
    val three = SpotAggregator.aggregate(spots(3))
    expect("3명 직접 제보 노출 수", three.added.size, 1)
    expect("3명 직접 제보 reporters", three.added.firstOrNull()?.reporters, 3)
    expect("3명 직접 제보 = 노란 핀 표식", three.added.firstOrNull()?.direct, true)
    expect("3명 직접 제보는 핫스팟 아님", three.added.firstOrNull()?.hotspot, false)
    expect("직접 제보 수", three.directCount, 1)
    expect("핫스팟 수 0", three.hotspotCount, 0)
    expect("2명 등록은 아직 미노출", SpotAggregator.aggregate(spots(2)).added.size, 0)
    expect("1명 등록은 미노출", SpotAggregator.aggregate(spots(1)).added.size, 0)

    // (2) 같은 기기가 여러 번 올려도 1명 — 노출되지 않는다
    val sameDevice = (1..3).map { report(1, minutesAgo = it.toLong()) }
    expect("같은 기기 3회 = 1명", SpotAggregator.aggregate(sameDevice).added.size, 0)
    // 빈 deviceHash 는 세지 않는다
    val blankHash = spots(3).map { it.copy(deviceHash = "  ") }
    expect("빈 기기 해시 제외", SpotAggregator.aggregate(blankHash).added.size, 0)

    // (3) 50m 안이면 한 자리로, 50m 밖이면 다른 자리로 (0.00013 ≒ 15m, 0.0003 ≒ 33m, 0.00036 ≒ 40m, 0.00054 ≒ 60m)
    expect("반경 15m 거리", SpotAggregator.distanceM(lat0, lng0, lat0 + 0.00013, lng0) < SpotAggregator.GROUP_RADIUS_M, true)
    expect("반경 33m 거리", SpotAggregator.distanceM(lat0, lng0, lat0 + 0.0003, lng0) < SpotAggregator.GROUP_RADIUS_M, true)
    expect("반경 40m 거리", SpotAggregator.distanceM(lat0, lng0, lat0 + 0.00036, lng0) < SpotAggregator.GROUP_RADIUS_M, true)
    expect("반경 60m 거리", SpotAggregator.distanceM(lat0, lng0, lat0 + 0.00054, lng0) > SpotAggregator.GROUP_RADIUS_M, true)
    // 40m 안쪽 3명 = 한 자리로 합쳐짐
    val near = listOf(report(1), report(2, lat = lat0 + 0.00036), report(3, lat = lat0 + 0.00036))
    expect("40m 안 3명 = 한 자리", SpotAggregator.aggregate(near).added.size, 1)
    expect("40m 안 3명 reporters", SpotAggregator.aggregate(near).added.firstOrNull()?.reporters, 3)
    // 60m 밖이면 별도 자리 — 3명 중 2명만 떨어져 있으면 미노출
    val apart = listOf(report(1), report(2, lat = lat0 + 0.00054), report(3, lat = lat0 + 0.00054))
    expect("60m 밖 1명+2명 = 미노출", SpotAggregator.aggregate(apart).added.size, 0)
    // 33m 는 v1.0.26 부터 같은 자리 — 3명이 합쳐져 노출
    val thirtyThree = listOf(report(1), report(2, lat = lat0 + 0.0003), report(3, lat = lat0 + 0.0003))
    expect("33m 안 3명 = 한 자리", SpotAggregator.aggregate(thirtyThree).added.size, 1)

    // (4) 이름·좌표·주소·메모 — 가장 많이 나온 이름, 같으면 긴 이름, 좌표는 평균
    val named = listOf(
        report(1, name = "역삼역 2번출구"),
        report(2, name = "역삼역"),
        report(3, name = "역삼역")
    )
    val namedSpot = SpotAggregator.aggregate(named).added.firstOrNull()
    expect("많이 나온 이름", namedSpot?.name, "역삼역")
    expect("좌표 평균", namedSpot?.lat, lat0)
    expect("주소 유지", namedSpot?.address, "서울 강남구 테헤란로")

    // (5) 공용 삭제 — 2명이면 없어진 자리, 1명이면 아무 일도 없다
    val removedTwo = SpotAggregator.aggregate(removals(2))
    expect("2명 삭제 = 없어진 자리 1곳", removedTwo.removed.size, 1)
    expect("2명 삭제 reporters", removedTwo.removed.firstOrNull()?.reporters, 2)
    expect("1명 삭제 = 없어진 자리 없음", SpotAggregator.aggregate(removals(1)).removed.size, 0)

    // (6) 3명 등록 + 1명 삭제 → 공용 콜밭은 그대로 보인다
    val keep = SpotAggregator.aggregate(spots(3) + removals(1))
    expect("1명 삭제는 콜밭 유지", keep.added.size, 1)
    expect("1명 삭제는 삭제 목록 없음", keep.removed.size, 0)

    // (7) 3명 등록 + 2명 삭제 → 공용 콜밭도 숨긴다
    val gone = SpotAggregator.aggregate(spots(3) + removals(2))
    expect("2명 삭제 시 콜밭 미노출", gone.added.size, 0)
    expect("2명 삭제 시 없어진 자리 1곳", gone.removed.size, 1)

    // (8) 기본 콜밭(1,223곳) 숨김 판정 — 반경 50m 안이면 숨김, 50m 밖은 그대로
    expect("없어진 자리 반경 안", SpotAggregator.isRemoved(lat0, lng0, gone.removed), true)
    expect("없어진 자리 15m", SpotAggregator.isRemoved(lat0 + 0.00013, lng0, gone.removed), true)
    expect("없어진 자리 40m", SpotAggregator.isRemoved(lat0 + 0.00036, lng0, gone.removed), true)
    expect("없어진 자리 60m", SpotAggregator.isRemoved(lat0 + 0.00054, lng0, gone.removed), false)
    expect("없어진 자리 없으면 유지", SpotAggregator.isRemoved(lat0, lng0, keep.removed), false)

    // (9) 반경 안의 삭제 기기 수 계산 (묶음 경계와 무관하게 같은 자리 신고로 본다)
    expect("반경 안 삭제 기기 2", SpotAggregator.devicesNear(removals(2), lat0, lng0), 2)
    expect("반경 밖 삭제 기기 0", SpotAggregator.devicesNear(removals(2, lat0 + 0.01, lng0), lat0, lng0), 0)

    // (10) 들어온 순서가 달라도 결과가 같다 (최신순 정렬 후 묶음)
    val shuffled = (spots(3) + removals(2)).reversed()
    val shuffledState = SpotAggregator.aggregate(shuffled)
    expect("순서 무관 콜밭 수", shuffledState.added.size, gone.added.size)
    expect("순서 무관 삭제 수", shuffledState.removed.size, gone.removed.size)
    expect("순서 무관 이름", shuffledState.removed.firstOrNull()?.name, gone.removed.firstOrNull()?.name)

    // (11) kind 가 비어 있거나 모르는 값이면 콜밭 등록으로 본다
    val unknownKind = (1..3).map { report(it).copy(kind = "") }
    expect("kind 빈 값 = 등록으로 집계", SpotAggregator.aggregate(unknownKind).added.size, 1)

    // (11-2) 직접 제보와 운행 GPS는 따로 묶인다 — 같은 자리라도 노란 핀 1곳 + 빨간 핀 1곳
    val mixedReports = spots(3) + (1..3).map { report(200 + it, note = "#t:22", name = "역삼역") }
    val mixedState = SpotAggregator.aggregate(mixedReports)
    expect("직접+GPS 따로 = 2곳", mixedState.added.size, 2)
    expect("직접 제보 1곳", mixedState.directCount, 1)
    expect("핫스팟 1곳", mixedState.hotspotCount, 1)

    // ============================================================ 👑 운영자 모드 (v1.0.23)
    // 운영자 해시가 달린 제보 한 건은 기준 인원(3명)을 기다리지 않고 바로 공개·삭제된다
    val adminHash = SpotAggregator.adminDeviceHash("test-pass")
    expect("운영자 해시 길이", adminHash.length, 32)
    expect("운영자 해시는 16진수", adminHash.all { it in "0123456789abcdef" }, true)
    expect("운영자 해시 고정", adminHash, SpotAggregator.adminDeviceHash("test-pass"))
    expect("다른 암호 = 다른 해시", adminHash == SpotAggregator.adminDeviceHash("test-pass2"), false)
    fun adminReport(
        kind: String = SpotAggregator.KIND_SPOT,
        lat: Double = lat0,
        lng: Double = lng0,
        name: String = "운영자 콜밭",
        minutesAgo: Long = 0L
    ) = SpotReport(
        name = name,
        lat = lat,
        lng = lng,
        address = "서울 강남구 테헤란로",
        note = "",
        deviceHash = adminHash,
        createdAt = 1_700_000_000_000L - minutesAgo * 60_000L,
        kind = kind
    )

    // (1) 운영자 등록 1건 = 바로 공개 (기사 수 1)
    val adminSpot = SpotAggregator.aggregate(listOf(adminReport()), null)
    expect("운영자 등록인데 암호 없는 빌드는 못 알아봄", adminSpot.added.size, 0)
    val adminPublic = SpotAggregator.aggregate(listOf(adminReport()), adminHash)
    expect("운영자 1건 바로 공개", adminPublic.added.size, 1)
    expect("운영자 공개 표식", adminPublic.added.firstOrNull()?.admin, true)
    expect("운영자 공개 기사 수", adminPublic.added.firstOrNull()?.reporters, 1)
    expect("운영자 공개도 삭제 목록에는 안 들어감", adminPublic.removed.size, 0)

    // (2) 운영자 1 + 일반 2 = 3명, 운영자 표식 유지
    val adminMixed = SpotAggregator.aggregate(spots(2) + adminReport(minutesAgo = 1), adminHash)
    expect("운영자+일반2 공개 수", adminMixed.added.size, 1)
    expect("운영자+일반2 기사 수", adminMixed.added.firstOrNull()?.reporters, 3)
    expect("운영자+일반2 표식", adminMixed.added.firstOrNull()?.admin, true)

    // (3) 운영자 삭제 1건 = 바로 사라짐 (기준 인원 대기 없음)
    val adminGone = SpotAggregator.aggregate(listOf(adminReport(SpotAggregator.KIND_REMOVED)), adminHash)
    expect("운영자 1건 바로 삭제", adminGone.removed.size, 1)
    expect("운영자 삭제 표식", adminGone.removed.firstOrNull()?.admin, true)
    expect("운영자 삭제 자리 숨김", SpotAggregator.isRemoved(lat0, lng0, adminGone.removed), true)
    expect("암호 없는 빌드는 운영자 삭제도 못 알아봄",
        SpotAggregator.aggregate(listOf(adminReport(SpotAggregator.KIND_REMOVED)), null).removed.size, 0)

    // (4) 운영자 삭제가 일반 3명 등록과 같거나 더 새로우면 삭제가 이긴다 (반경 50m)
    val adminOverTwo = SpotAggregator.aggregate(
        spots(3) + adminReport(SpotAggregator.KIND_REMOVED), adminHash
    )
    expect("운영자 삭제 vs 3명 등록 → 공개 0", adminOverTwo.added.size, 0)
    expect("운영자 삭제 vs 3명 등록 → 삭제 1", adminOverTwo.removed.size, 1)

    // (5) 운영자 삭제도 반경 50m 밖이면 등록 유지 / 40m 안이면 숨긴다
    val adminFar = SpotAggregator.aggregate(
        spots(3) + adminReport(SpotAggregator.KIND_REMOVED, lat = lat0 + 0.00054), adminHash
    )
    expect("60m 밖 운영자 삭제 → 공개 유지", adminFar.added.size, 1)
    expect("60m 밖 운영자 삭제 → 삭제 1", adminFar.removed.size, 1)
    val adminNear = SpotAggregator.aggregate(
        spots(3) + adminReport(SpotAggregator.KIND_REMOVED, lat = lat0 + 0.00036), adminHash
    )
    expect("40m 안 운영자 삭제 → 3명 등록도 숨김", adminNear.added.size, 0)
    expect("40m 안 운영자 삭제 → 삭제 1", adminNear.removed.size, 1)

    // (6) 2명 삭제보다 운영자 공개가 더 새로우면 공개가 이긴다
    val oldRemovals = removals(2).map { it.copy(createdAt = it.createdAt - 600_000L) }
    val adminBeat = SpotAggregator.aggregate(oldRemovals + adminReport(), adminHash)
    expect("2명 삭제 vs 새 운영자 공개 → 공개 1", adminBeat.added.size, 1)
    expect("2명 삭제 vs 새 운영자 공개 → 표식", adminBeat.added.firstOrNull()?.admin, true)
    expect("2명 삭제 vs 새 운영자 공개 → 삭제 풀림", adminBeat.removed.size, 0)

    // (7) 운영자 삭제 + 운영자 공개가 같은 자리 → 더 새 공개가 이긴다
    val adminBoth = SpotAggregator.aggregate(
        listOf(adminReport(SpotAggregator.KIND_REMOVED, minutesAgo = 5), adminReport()), adminHash
    )
    expect("운영자 삭제+공개 → 공개 1", adminBoth.added.size, 1)
    expect("운영자 삭제+공개 → 삭제 0", adminBoth.removed.size, 0)

    // (7-2) 오래된 운영자 삭제는 그 뒤에 온 기사 3명 등록을 막지 않는다
    val revived = SpotAggregator.aggregate(
        listOf(adminReport(SpotAggregator.KIND_REMOVED, minutesAgo = 60)) + spots(3), adminHash
    )
    expect("1시간 전 운영자 삭제 → 새 3명 공개", revived.added.size, 1)
    expect("1시간 전 운영자 삭제 → 삭제 풀림", revived.removed.size, 0)
    // 반대로 운영자 삭제가 3명 등록보다 새로우면 공개되지 않는다
    val killedAgain = SpotAggregator.aggregate(
        spots(3).map { it.copy(createdAt = it.createdAt - 600_000L) } +
            adminReport(SpotAggregator.KIND_REMOVED), adminHash
    )
    expect("새 운영자 삭제 → 3명 공개 0", killedAgain.added.size, 0)
    expect("새 운영자 삭제 → 삭제 1", killedAgain.removed.size, 1)

    // (8) 암호를 아는 집계에서도 일반 3명 기준은 그대로
    val normalWithAdmin = SpotAggregator.aggregate(spots(3), adminHash)
    expect("암호 있는 집계의 일반 3명", normalWithAdmin.added.size, 1)
    expect("암호 있는 집계의 일반 3명 표식", normalWithAdmin.added.firstOrNull()?.admin, false)
    expect("일반 1명은 여전히 미노출", SpotAggregator.aggregate(spots(1), adminHash).added.size, 0)
    expect("일반 2명도 여전히 미노출", SpotAggregator.aggregate(spots(2), adminHash).added.size, 0)

    // (9) 운영자 해시 판정 도우미
    expect("운영자 제보 판정", SpotAggregator.isAdminReport(adminReport(), adminHash), true)
    expect("일반 제보는 운영자 아님", SpotAggregator.isAdminReport(report(1), adminHash), false)
    expect("암호 없으면 아님", SpotAggregator.isAdminReport(adminReport(), null), false)
    expect("반경 50m 안 운영자 삭제",
        SpotAggregator.adminRemovalNear(
            listOf(adminReport(SpotAggregator.KIND_REMOVED)),
            lat0 + 0.00036, lng0, adminHash
        ), true)
    expect("반경 60m 밖 운영자 삭제",
        SpotAggregator.adminRemovalNear(
            listOf(adminReport(SpotAggregator.KIND_REMOVED)),
            lat0 + 0.00054, lng0, adminHash
        ), false)

    // ============================================================ 잘 뜨는 시간대 · 지금 활발 (v1.0.26 · v1.0.29)
    // 서버 8칸 규칙을 바꾸지 않으려고 note 첫머리에 `#t:22` 표식을 싣는다
    expect("시간대 표식", SpotTimeBands.encodeNote("", 22), "#t:22")
    expect("시간대 표식 + 메모", SpotTimeBands.encodeNote("강남역 앞", 1), "#t:01 강남역 앞")
    expect("표식 해석 시", SpotTimeBands.decodeNote("#t:22").second, 22)
    expect("표식 해석 메모", SpotTimeBands.decodeNote("#t:22 강남역 앞").first, "강남역 앞")
    expect("표식 없는 메모 그대로", SpotTimeBands.decodeNote("12시 넘어 잘 서요").first, "12시 넘어 잘 서요")
    expect("표식 없는 메모 시간대", SpotTimeBands.decodeNote("12시 넘어 잘 서요").second, null)
    expect("깨진 표식은 메모로", SpotTimeBands.decodeNote("#t:").first, "#t:")
    expect("운행 제보 판별", SpotTimeBands.isRideBand("#t:22"), true)
    expect("직접 제보는 운행 아님", SpotTimeBands.isRideBand("역삼역"), false)
    expect("22·23·0·1시 피크 창", SpotTimeBands.peakWindow(listOf(22, 23, 0, 1)), 22)
    expect("피크 표기", SpotTimeBands.peakLabel(listOf(22, 23, 0, 1)), "22시~01시 활발")
    expect("저녁 2건 피크", SpotTimeBands.peakWindow(listOf(20, 21)), 20)
    expect("23시 2건 피크", SpotTimeBands.peakWindow(listOf(23, 23)), 23)
    expect("한 건은 시간대 없음", SpotTimeBands.peakLabel(listOf(22)), "")
    expect("떨어진 두 시간은 시간대 없음", SpotTimeBands.peakLabel(listOf(3, 20)), "")
    expect("3시간 창 라벨", SpotTimeBands.label(1), "01시~04시")

    // ============================================================ 지금 활발한 핫스팟 (v1.0.29)
    // 화면 위 시간대 필터 칩을 없애고, 앱이 지금 시각(±1시간)으로 스스로 찾아 강조한다
    expect("시 거리 22→0", SpotTimeBands.hourDistance(22, 0), 2)
    expect("시 거리 23→1", SpotTimeBands.hourDistance(23, 1), 2)
    expect("시 거리 5→5", SpotTimeBands.hourDistance(5, 5), 0)
    expect("시 거리 3→21", SpotTimeBands.hourDistance(3, 21), 6)
    expect("22시 창에 22시", SpotTimeBands.inWindow(22, 22), true)
    expect("22시 창에 0시", SpotTimeBands.inWindow(0, 22), true)
    expect("22시 창에 1시 밖", SpotTimeBands.inWindow(1, 22), false)
    expect("지금(22시) 근처 2건", SpotTimeBands.activeCount(listOf(22, 23, 4), 22), 2)
    expect("지금(0시) 근처 23시 1건", SpotTimeBands.activeCount(listOf(22, 23, 4), 0), 1)
    expect("지금(14시) 근처 없음", SpotTimeBands.activeCount(listOf(22, 23, 4), 14), 0)
    val nowFit = SpotTimeBands.nowFit(listOf(22, 23), 22)
    expect("지금 활발", nowFit.active, true)
    expect("지금 상태 전체 건수", nowFit.total, 2)
    expect("지금 상태 피크", nowFit.peakStart, 22)
    expect("지금 활발 줄", nowFit.label, "🟢 지금 활발 · 주로 22시~01시 · 2건")
    expect("지금 짧은 표기", nowFit.badge, "🟢 지금 활발")
    val nowIdle = SpotTimeBands.nowFit(listOf(22, 23), 14)
    expect("지금 한산", nowIdle.active, false)
    expect("지금 한산 줄", nowIdle.label, "지금은 한산 · 주로 22시~01시 · 2건")
    expect("시간대 모르면 빈 줄", SpotTimeBands.nowFit(emptyList(), 22).label, "")
    expect("시간대 모르면 활발 아님", SpotTimeBands.nowFit(emptyList(), 22).active, false)
    expect("지금 활발 점수가 높다",
        SpotTimeBands.nowFit(listOf(22, 23), 22).score > SpotTimeBands.nowFit(listOf(4, 5), 22).score,
        true)
    expect("같은 지금이면 제보 많은 쪽",
        SpotTimeBands.nowFit(listOf(22, 22, 22), 22).score > SpotTimeBands.nowFit(listOf(22), 22).score,
        true)

    // (12) 운행 GPS 집계 (v1.0.30) — 반경 500m · 서로 다른 기기 3명 이상 · 지금 시간대(±1시간)
    val rideReports = listOf(
        report(1, note = "#t:22 강남역 2번출구", name = "강남역 2번출구"),
        report(2, note = "#t:23", name = "강남역 2번출구", minutesAgo = 5),
        report(3, note = "#t:22", name = "강남역 2번출구", minutesAgo = 10)
    )
    val rideSpot = SpotAggregator.aggregate(rideReports).added.firstOrNull()
    expect("운행 GPS 3명 = 핫스팟", rideSpot?.hotspot, true)
    expect("핫스팟은 노란 핀 아님", rideSpot?.direct, false)
    expect("핫스팟 기사 수", rideSpot?.reporters, 3)
    expect("핫스팟 피크", rideSpot?.peak, "22시~01시 활발")
    expect("핫스팟 시간대 목록", rideSpot?.hours, listOf(22, 23))
    expect("운행 GPS 2명은 미노출", SpotAggregator.aggregate(rideReports.take(2)).added.size, 0)
    expect("하나의 기기가 3번 올려도 미노출",
        SpotAggregator.aggregate((1..3).map { report(1, note = "#t:22", minutesAgo = it.toLong()) }).added.size, 0)

    // 반경 500m — 400m 떨어진 자리는 한 핫스팟, 700m 떨어지면 따로 (0.0009 ≒ 100m, 0.0036 ≒ 400m, 0.0063 ≒ 700m)
    expect("400m 거리 < 500m", SpotAggregator.distanceM(lat0, lng0, lat0 + 0.0036, lng0) < SpotAggregator.HOTSPOT_RADIUS_M, true)
    expect("700m 거리 > 500m", SpotAggregator.distanceM(lat0, lng0, lat0 + 0.0063, lng0) > SpotAggregator.HOTSPOT_RADIUS_M, true)
    val wide = listOf(
        report(1, note = "#t:22"),
        report(2, note = "#t:22", lat = lat0 + 0.0036),
        report(3, note = "#t:22", lat = lat0 + 0.0036)
    )
    expect("400m 안 3명 = 한 핫스팟", SpotAggregator.aggregate(wide).added.size, 1)
    expect("400m 안 3명 reporters", SpotAggregator.aggregate(wide).added.firstOrNull()?.reporters, 3)
    val farAway = listOf(
        report(1, note = "#t:22"),
        report(2, note = "#t:22", lat = lat0 + 0.0063),
        report(3, note = "#t:22", lat = lat0 + 0.0063)
    )
    expect("700m 밖은 따로 — 1명+2명 = 미노출", SpotAggregator.aggregate(farAway).added.size, 0)

    // 지금 시간대(±1시간) — 22시에 보면 보이고, 14시에 보면 빠진다
    expect("지금 22시 = 핫스팟 보임", SpotAggregator.aggregate(rideReports, nowHour = 22).added.size, 1)
    expect("지금 23시 = 핫스팟 보임", SpotAggregator.aggregate(rideReports, nowHour = 23).added.size, 1)
    expect("지금 0시 = 23시 제보가 ±1시간 안이라 보임", SpotAggregator.aggregate(rideReports, nowHour = 0).added.size, 1)
    expect("지금 2시 = 22·23시에서 멀어 빠짐", SpotAggregator.aggregate(rideReports, nowHour = 2).added.size, 0)
    expect("지금 14시 = 빠짐", SpotAggregator.aggregate(rideReports, nowHour = 14).added.size, 0)
    // 직접 제보(시간대 없는 고정 콜밭)는 지금 시간대와 상관없이 그대로 보인다
    expect("직접 제보는 시간대 무관", SpotAggregator.aggregate(spots(3), nowHour = 14).added.size, 1)

    // 직접 제보 3명은 핫스팟 아님 (빨간 핀 아님)
    expect("직접 제보 3명은 핫스팟 아님",
        SpotAggregator.aggregate(spots(3)).added.firstOrNull()?.hotspot, false)
    // 표식 없는 옛 제보는 제보 시각에서 시간대를 읽는다 (23시 10분 세 건 → 23시 창)
    val lateNight = BizTime.millisOf(LocalDate.of(2025, 1, 1), LocalTime.of(23, 50))
    val lateSpot = SpotAggregator.aggregate(
        listOf(
            report(1, createdAt = lateNight),
            report(2, createdAt = lateNight - 600_000L),
            report(3, createdAt = lateNight - 1_200_000L)
        )
    ).added.firstOrNull()
    expect("제보 시각에서 읽은 피크", lateSpot?.peak, "23시~02시 활발")
    expect("옛 제보는 핫스팟 아님", lateSpot?.hotspot, false)

    // (13) 공용 콜밭 -> 목록 항목 (배지·기사 수·시간대·핫스팟)
    val publicSpot = PublicSpot(
        name = "테헤란로 콜밭",
        lat = 37.5,
        lng = 127.0,
        address = "서울 강남구 테헤란로",
        reporters = 3,
        lastAt = 1_700_000_000_000L,
        note = "#t:22 강남역 앞",
        peak = "22시~01시 활발",
        hours = listOf(22, 23),
        hotspot = true
    )
    val cloudItem = Hotspots.fromPublicSpot(publicSpot)
    expect("제보 콜밭 출처", cloudItem.source, Hotspots.SOURCE_CLOUD)
    expect("제보 콜밭 reporters", cloudItem.reporters, 3)
    expect("제보 콜밭 lastAt", cloudItem.lastAt, 1_700_000_000_000L)
    expect("제보 콜밭 시·도", cloudItem.city, "서울")
    expect("제보 콜밭 좌표 있음", cloudItem.hasLocation, true)
    expect("제보 콜밭 이름 검색", cloudItem.matches("테헤란로"), true)
    expect("핫스팟 표식", cloudItem.hotspot, true)
    expect("핫스팟은 노란 핀 아님", cloudItem.direct, false)
    expect("핫스팟 카드 피크", cloudItem.peakLabel, "🔥 22시~01시 활발")
    expect("메모에서 시간대 표식 제거", cloudItem.tip, "강남역 앞")
    expect("목록의 시간대 목록", cloudItem.hours, listOf(22, 23))
    expect("제보 콜밭도 지금 활발", cloudItem.nowFit(22).active, true)
    expect("제보 콜밭 지금 줄", cloudItem.timeLine(22), "🟢 지금 활발 · 주로 22시~01시 · 2건")
    expect("낮에는 한산", cloudItem.nowFit(14).active, false)
    expect("시간대 없는 기본 콜밭은 피크 줄", Hotspots.CURATED.first().timeLine(22), "피크 " + Hotspots.CURATED.first().peak)
    // 운영자·직접 제보는 핫스팟 아님 — 피크 줄은 `피크`
    val manualItem = Hotspots.fromPublicSpot(
        publicSpot.copy(admin = true, hotspot = false, peak = "20시~23시 활발", note = "")
    )
    expect("운영자 콜밭은 핫스팟 아님", manualItem.hotspot, false)
    expect("일반 콜밭 피크 줄", manualItem.peakLabel, "피크 20시~23시 활발")
    // 🟡 직접 제보로 올라온 자리는 노란 핀 표식이 남는다 (v1.0.30)
    val directItem = Hotspots.fromPublicSpot(
        publicSpot.copy(hotspot = false, direct = true, note = "", peak = "22시~01시 활발")
    )
    expect("직접 제보 콜밭 = 노란 핀", directItem.direct, true)
    expect("직접 제보 콜밭은 핫스팟 아님", directItem.hotspot, false)
    expect("직접 제보 콜밭 피크 줄은 피크", directItem.peakLabel, "피크 22시~01시 활발")

    // ============================================================ 몰아 올리기 제외 (v1.0.26)
    // 사용자 지적: "가끔 한 자리에서 밀린거 싹다 올리는 사람도 있거든" → 시간차·연속 입력 필터
    val now = 1_800_000_000_000L
    fun decideRide(
        rideMinutesAgo: Long,
        lastAttemptMinutesAgo: Long = 0L,
        lastPing: RideSpotGate.LastPing? = null,
        lat: Double = lat0,
        lng: Double = lng0,
        nowMillis: Long = now
    ) = RideSpotGate.decide(
        rideTimeMillis = nowMillis - rideMinutesAgo * 60_000L,
        nowMillis = nowMillis,
        lat = lat,
        lng = lng,
        lastAttemptAt = if (lastAttemptMinutesAgo > 0) nowMillis - lastAttemptMinutesAgo * 60_000L else 0L,
        lastPing = lastPing
    )

    expect("운행 5분 뒤 저장 = 집계", decideRide(5).report, true)
    expect("운행 20분 뒤 저장 = 집계", decideRide(20).report, true)
    expect("운행 21분 뒤 저장 = 제외", decideRide(21).verdict, RideSpotGate.Verdict.RIDE_TOO_OLD)
    expect("과거 날짜 기록 = 제외", decideRide(24 * 60).verdict, RideSpotGate.Verdict.RIDE_TOO_OLD)
    expect("미래 5분 = 제외", decideRide(-5).verdict, RideSpotGate.Verdict.RIDE_IN_FUTURE)
    expect("미래 1분(시계 오차) = 집계", decideRide(-1).report, true)
    expect("15분 안 두 번째 = 제외", decideRide(1, lastAttemptMinutesAgo = 3).verdict,
        RideSpotGate.Verdict.BURST_INPUT)
    expect("16분 전 시도 뒤 = 집계", decideRide(1, lastAttemptMinutesAgo = 16).report, true)
    val samePlace = RideSpotGate.LastPing(now - 30 * 60_000L, lat0, lng0)
    expect("한 시간 안 같은 자리 = 제외",
        decideRide(1, lastAttemptMinutesAgo = 20, lastPing = samePlace).verdict,
        RideSpotGate.Verdict.SAME_PLACE_AGAIN)
    val farPlace = RideSpotGate.LastPing(now - 10 * 60_000L, lat0 + 0.00054, lng0)
    expect("한 시간 안 다른 자리(60m) = 집계",
        decideRide(1, lastAttemptMinutesAgo = 20, lastPing = farPlace).report, true)
    val oldPlace = RideSpotGate.LastPing(now - 2 * 60 * 60_000L, lat0, lng0)
    expect("두 시간 전 같은 자리 = 집계",
        decideRide(1, lastAttemptMinutesAgo = 20, lastPing = oldPlace).report, true)
    expect("운행 시각 0 = 제외",
        RideSpotGate.decide(0L, now, lat0, lng0, 0L, null).verdict, RideSpotGate.Verdict.RIDE_TOO_OLD)

    // 출발지 요약명 — 여러 칸 공백 정리·40자 제한·빈 값 제외
    expect("출발지 공백 정리", RideSpotGate.departureName("  강남역   2번출구 "), "강남역 2번출구")
    expect("출발지 빈 값", RideSpotGate.departureName("   "), null)
    expect("출발지 40자 제한", RideSpotGate.departureName("가".repeat(50))?.length, 40)

    // 제보 note 에 들어가는 시간대 — 운행 시각의 현지 시
    val rideTime = BizTime.millisOf(LocalDate.of(2025, 1, 1), LocalTime.of(22, 40))
    expect("운행 시각 시간대", SpotTimeBands.peakLabel(listOf(SpotTimeBands.hourOf(rideTime))), "")
    expect("운행 시간대 시", SpotTimeBands.hourOf(rideTime), 22)
    expect("운행 제보 note", SpotTimeBands.encodeNote("", SpotTimeBands.hourOf(rideTime)), "#t:22")

    // ============================================================ 📍 현위치 → 동네 이름 (v1.0.27)
    // 사용자 지적: 운행 입력 때 출발지를 매번 타이핑하지 않게 현위치를 바로 넣어 달라
    // → 도로명·건물명보다 동/읍/면을 먼저, 없으면 구·시
    expect(
        "현위치: 주소 줄에서 동 우선",
        PlaceNamer.shortName(
            locality = "성남시",
            subLocality = "분당구",
            addressLines = listOf("대한민국 경기도 성남시 분당구 정자동 7")
        ),
        "정자동"
    )
    expect("현위치: subLocality 가 동이면 그대로",
        PlaceNamer.shortName(locality = "성남시", subLocality = "정자동"), "정자동")
    expect("현위치: 읍",
        PlaceNamer.shortName(locality = "가평군", addressLines = listOf("경기도 가평군 청평면")), "청평면")
    expect("현위치: 면",
        PlaceNamer.shortName(locality = "화성시", subLocality = "동탄면"), "동탄면")
    expect("현위치: 번지가 붙어도 동 이름만",
        PlaceNamer.shortName(addressLines = listOf("서울특별시 강남구 역삼동 123-4")), "역삼동")
    expect("현위치: 번지 토큰은 동네가 아니다",
        PlaceNamer.shortName(addressLines = listOf("경기도 성남시 123-4동")), "성남시")
    expect("현위치: 동이 없으면 구",
        PlaceNamer.shortName(locality = "성남시", subLocality = "분당구"), "분당구")
    expect("현위치: 서울특별시 → 서울", PlaceNamer.shortName(locality = "서울특별시"), "서울")
    expect("현위치: 시·구가 한 덩이면 구", PlaceNamer.shortName(subAdminArea = "성남시 분당구"), "분당구")
    expect("현위치: 도로명은 동네가 아니다",
        PlaceNamer.shortName(
            thoroughfare = "정자일로", locality = "성남시", subLocality = "분당구"
        ), "분당구")
    expect("현위치: 한 줄만 줘도 된다",
        PlaceNamer.shortName("대한민국 경기도 수원시 팔달구 우만동 12-3"), "우만동")
    expect("현위치: 아무것도 없으면 null", PlaceNamer.shortName(), null)
    expect("현위치: 공백만 있으면 null", PlaceNamer.shortName(locality = "   "), null)
    expect("현위치: 20자 제한",
        PlaceNamer.shortName(subLocality = "가".repeat(30) + "동")?.length, PlaceNamer.MAX_LEN)

    // ===== v1.0.28 — 사용자: "출발지 등록할 때 현위치 누르면 동이나 리까지 등록해달라고" =====
    // 리·가를 동네 단위로 추가하고, 읍·면 안의 리를 읍·면보다 먼저 고른다
    expect("현위치: 리", PlaceNamer.shortName(locality = "보은군", addressLines = listOf("충북 보은군 속리산면 갈목리")), "갈목리")
    expect("현위치: 읍+리 → 리",
        PlaceNamer.shortName(locality = "가평군", subAdminArea = "청평읍",
            addressLines = listOf("경기도 가평군 청평읍 청평리")), "청평리")
    expect("현위치: 면+리 → 리",
        PlaceNamer.shortName(subLocality = "백암면", addressLines = listOf("경기 용인시 처인구 백암면 박곡리 산 14")), "박곡리")
    expect("현위치: 리 한 이름에 읍·면과 붙어 와도 리만",
        PlaceNamer.shortName(candidates = listOf("백암면 박곡리", "경기 용인시 처인구 백암면 박곡리 산 14")), "박곡리")
    expect("현위치: 리가 번지로 오면 읍",
        PlaceNamer.shortName(addressLines = listOf("경기 용인시 처인구 백암면 123-4리")), "백암면")
    expect("현위치: 번지 토큰은 리가 아니다",
        PlaceNamer.shortName(addressLines = listOf("경기 용인시 처인구 123-4리")), "처인구")
    expect("현위치: 리는 동보다 먼저",
        PlaceNamer.shortName(candidates = listOf("화성시 동탄면 신리")), "신리")
    expect("현위치: 가(번지 붙은 자리)", PlaceNamer.shortName(candidates = listOf("종로1가")), "종로1가")
    expect("현위치: 가 — 주소 줄에서",
        PlaceNamer.shortName(addressLines = listOf("서울특별시 종로구 종로1가 50")), "종로1가")
    expect("현위치: 가 — 을지로3가",
        PlaceNamer.shortName(addressLines = listOf("서울 중구 을지로3가 65-8")), "을지로3가")
    expect("현위치: 번지가 없는 '가' 낱말은 동네가 아니다",
        PlaceNamer.shortName(addressLines = listOf("서울특별시 중구 국가로 1")), "중구")
    expect("현위치: 리도 동도 없으면 면",
        PlaceNamer.shortName(locality = "가평군", addressLines = listOf("경기도 가평군 청평면")), "청평면")
    expect("현위치: 리·동·가·읍·면 후보 우선순위",
        PlaceNamer.shortName(candidates = listOf("박곡리", "백암면")), "박곡리")
    expect("현위치: 동을 함께 주면 동이 먼저",
        PlaceNamer.shortName(candidates = listOf("정자동", "성남시 분당구")), "정자동")

    // ===== 카카오 coord2address (v1.0.28) =====
    // 실기기에서 현위치가 '성남시' 로 떨어지던 문제 → 카카오가 주는 region_3depth_name 을 먼저 쓴다
    expect("카카오 요청 좌표 순서(x=경도,y=위도)",
        KakaoReverse.url(37.3665, 127.1085),
        "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=127.1085000&y=37.3665000&input_coord=WGS84")
    expect("카카오 KA 헤더 origin", KakaoReverse.kaHeader(),
        "sdk/1.0 os/javascript origin/https://youngsujoa1-cyber.github.io lang/ko-KR")
    expect("카카오 Referer", KakaoReverse.REFERER,
        "https://youngsujoa1-cyber.github.io/daeri-ledger/kakaomap.html")
    // 2026-11-27 실제 응답 그대로 (정자동 · 백암면 박곡리 · 종로1가 · 을지로3가)
    val kakaoJeongja = """{"meta":{"total_count":1},"documents":[{"road_address":null,"address":{"address_name":"경기 성남시 분당구 정자동 100-3","region_1depth_name":"경기","region_2depth_name":"성남시 분당구","region_3depth_name":"정자동","mountain_yn":"N","main_address_no":"100","sub_address_no":"3","zip_code":""}}]}"""
    expect("카카오 응답 → 정자동", KakaoReverse.nameFromJson(kakaoJeongja), "정자동")
    val kakaoBaegam = """{"meta":{"total_count":1},"documents":[{"road_address":null,"address":{"address_name":"경기 용인시 처인구 백암면 박곡리 산 14","region_1depth_name":"경기","region_2depth_name":"용인시 처인구","region_3depth_name":"백암면 박곡리","mountain_yn":"Y","main_address_no":"14","sub_address_no":"","zip_code":""}}]}"""
    expect("카카오 응답 → 박곡리", KakaoReverse.nameFromJson(kakaoBaegam), "박곡리")
    val kakaoJongno = """{"meta":{"total_count":1},"documents":[{"road_address":null,"address":{"address_name":"서울 종로구 종로1가 50","region_1depth_name":"서울","region_2depth_name":"종로구","region_3depth_name":"종로1가","mountain_yn":"N","main_address_no":"50","sub_address_no":"","zip_code":""}}]}"""
    expect("카카오 응답 → 종로1가", KakaoReverse.nameFromJson(kakaoJongno), "종로1가")
    val kakaoEuljiro = """{"meta":{"total_count":1},"documents":[{"road_address":{"address_name":"서울특별시 중구 을지로 117","region_1depth_name":"서울특별시","region_2depth_name":"중구","region_3depth_name":"","road_name":"을지로","building_name":"","zone_no":"04561"},"address":{"address_name":"서울 중구 을지로3가 65-8","region_1depth_name":"서울","region_2depth_name":"중구","region_3depth_name":"을지로3가","mountain_yn":"N","main_address_no":"65","sub_address_no":"8","zip_code":""}}]}"""
    expect("카카오 응답 → 을지로3가(도로명 말고 지번)", KakaoReverse.nameFromJson(kakaoEuljiro), "을지로3가")
    expect("카카오: 결과 없음", KakaoReverse.nameFromJson("""{"meta":{"total_count":0},"documents":[]}"""), null)
    expect("카카오: 빈 본문", KakaoReverse.nameFromJson(""), null)
    expect("카카오: 깨진 본문", KakaoReverse.nameFromJson("<html>401</html>"), null)
    expect("카카오: 오류 응답",
        KakaoReverse.nameFromJson("""{"errorType":"AccessDeniedError","message":"domain mismatched"}"""), null)
    expect("카카오: \\uXXXX 이스케이프 주소",
        KakaoReverse.nameFromJson("""{"documents":[{"address":{"address_name":"경기 성남시 분당구 정자동 7","region_3depth_name":"\uC815\uC790\uB3D9"}}]}"""), "정자동")
    expect("카카오: region_3depth 가 없으면 행정동",
        KakaoReverse.nameOf(region3 = null, region3h = "정자동"), "정자동")
    expect("카카오: 동·리가 없으면 구",
        KakaoReverse.nameOf(region3 = "분당구", addressName = "경기 성남시 분당구"), "분당구")
    expect("카카오: 아무것도 없으면 null", KakaoReverse.nameOf(null, null, null, null), null)
    expect("카카오: 도로명만 있으면 시·도",
        KakaoReverse.nameOf(region3 = null, roadName = "경기도 성남시 분당구 성남대로 393"), "분당구")

    // ===== 현위치가 들어갈 칸 (v1.0.28) — 마지막으로 손댄 칸, 한 번도 안 골랐으면 출발지 =====
    expect("현위치 칸: 아무것도 안 고름 → 출발지", PlaceTarget.choose(null, null), PlaceTarget.FROM)
    expect("현위치 칸: 포커스가 이김", PlaceTarget.choose(PlaceTarget.TO, PlaceTarget.FROM), PlaceTarget.TO)
    expect("현위치 칸: 포커스가 없으면 기억한 칸",
        PlaceTarget.choose(null, PlaceTarget.VIA), PlaceTarget.VIA)
    expect("현위치 칸: 버튼을 처음 누르면 출발지", PlaceTarget.choose(null, PlaceTarget.FROM), PlaceTarget.FROM)
    expect("현위치 칸: 도착지에서 또 누르면 도착지", PlaceTarget.choose(null, PlaceTarget.TO), PlaceTarget.TO)
    // 콜밭 집계 좌표는 출발지에 넣었을 때만 남긴다
    expect("출발지 = 콜밭 좌표 남김", PlaceTarget.FROM.keepsSpotFix, true)
    expect("도착지 = 콜밭 좌표 안 남김", PlaceTarget.TO.keepsSpotFix, false)
    expect("경유 = 콜밭 좌표 안 남김", PlaceTarget.VIA.keepsSpotFix, false)

    // 잡아 둔 좌표를 저장할 때 그대로 쓰는 기준 — 30분이 지나면 새로 잡는다
    expect("현위치 좌표 10분 전 = 그대로 씀", RideSpotGate.fixUsable(now - 10 * 60_000L, now), true)
    expect("현위치 좌표 30분 전 = 그대로 씀", RideSpotGate.fixUsable(now - 30 * 60_000L, now), true)
    expect("현위치 좌표 31분 전 = 다시 잡음", RideSpotGate.fixUsable(now - 31 * 60_000L, now), false)
    expect("현위치 좌표 없음 = 다시 잡음", RideSpotGate.fixUsable(0L, now), false)

    // ===== 콜밭 실시간 위치 갱신 (v1.0.31) =====
    // ① 오래된 캐시(정자동)를 지금 위치로 믿지 않는다 — 기본 20초
    expect("캐시 10초 전 = 지금 위치로 씀", LocationPolicy.isFresh(now - 10_000, now, LocationPolicy.DEFAULT_MAX_CACHE_AGE_MS), true)
    expect("캐시 19초 전 = 지금 위치로 씀", LocationPolicy.isFresh(now - 19_000, now, LocationPolicy.DEFAULT_MAX_CACHE_AGE_MS), true)
    expect("캐시 21초 전 = 새로 잰다", LocationPolicy.isFresh(now - 21_000, now, LocationPolicy.DEFAULT_MAX_CACHE_AGE_MS), false)
    expect("캐시 3시간 전(정자동) = 새로 잰다", LocationPolicy.isFresh(now - 3 * 3_600_000L, now, LocationPolicy.DEFAULT_MAX_CACHE_AGE_MS), false)
    expect("캐시 나이 제한 없음(-1) = 예전 방식대로 그대로 씀", LocationPolicy.isFresh(now - 3_600_000L, now, -1L), true)
    expect("새로고침(0ms) = 캐시 안 믿음", LocationPolicy.isFresh(now - 1_000, now, 0L), false)
    expect("시각 없는 좌표 = 안 씀", LocationPolicy.isFresh(0L, now, LocationPolicy.DEFAULT_MAX_CACHE_AGE_MS), false)
    expect("기기 시계보다 5분 앞선 좌표 = 안 씀", LocationPolicy.isFresh(now + 300_000L, now, LocationPolicy.DEFAULT_MAX_CACHE_AGE_MS), false)
    expect("좌표 나이(미래도 0)", LocationPolicy.ageMs(now + 5_000L, now), 0L)

    // ② 제공자 하나만 고르지 않는다 — GPS·네트워크·융합을 함께
    expect("제공자: GPS+네트워크", LocationPolicy.providers(gps = true, network = true), listOf("gps", "network"))
    expect("제공자: 융합+GPS+네트워크", LocationPolicy.providers(gps = true, network = true, fused = true), listOf("fused", "gps", "network"))
    expect("제공자: 네트워크만 켜짐", LocationPolicy.providers(gps = false, network = true), listOf("network"))
    expect("제공자: 위치 서비스 꺼짐", LocationPolicy.providers(gps = false, network = false), emptyList<String>())

    // ③ 새 측정 받기 — 늦게 온 옛 측정은 버리고, 부정확한 네트워크 좌표가 GPS 를 덮지 않게
    expect("새 측정: 기존 없음 → 받는다", LocationPolicy.acceptFix(now, 20f, 0L, 0f), true)
    expect("새 측정: 더 새 좌표 → 받는다", LocationPolicy.acceptFix(now, 30f, now - 5_000, 20f), true)
    expect("새 측정: 1분 넘게 오래된 측정 → 버린다", LocationPolicy.acceptFix(now - 120_000, 15f, now, 20f), false)
    expect("새 측정: 같은 순간 + 더 정확 → 받는다", LocationPolicy.acceptFix(now, 8f, now, 25f), true)
    expect("새 측정: 같은 순간 + 조금 더 부정확(60m) → 허용(50m 여유)", LocationPolicy.acceptFix(now, 60f, now, 25f), true)
    expect("새 측정: 같은 순간 + 많이 더 부정확(400m) → 버린다", LocationPolicy.acceptFix(now, 400f, now, 25f), false)
    expect("새 측정: 5초 뒤 네트워크 800m(부정확) → 버린다", LocationPolicy.acceptFix(now + 5_000, 800f, now, 10f), false)
    expect("새 측정: 40초 뒤 조금 부정확 → 받는다(정확한 옛 좌표에 머무르지 않음)", LocationPolicy.acceptFix(now + 40_000, 120f, now, 10f), true)
    expect("정확도 모름(0) = 아주 나쁨", LocationPolicy.accuracyOf(0f), 999f)

    // ④ 목록을 다시 그리는 기준 — 30m·30초
    expect("다시 그리기: 첫 좌표", LocationPolicy.shouldRerender(37.5, 127.0, 0L, 37.5, 127.0, now), true)
    expect("다시 그리기: 10m 이동 → 그대로", LocationPolicy.shouldRerender(37.5, 127.0, now, 37.50009, 127.0, now + 2_000), false)
    expect("다시 그리기: 40m 이동 → 다시 그림", LocationPolicy.shouldRerender(37.5, 127.0, now, 37.50036, 127.0, now + 2_000), true)
    expect("다시 그리기: 안 움직여도 31초 → 다시 그림", LocationPolicy.shouldRerender(37.5, 127.0, now, 37.5, 127.0, now + 31_000), true)
    expect("지도 따라가기: 100m → 그대로", LocationPolicy.shouldFollowMap(37.5, 127.0, 37.5009, 127.0), false)
    expect("지도 따라가기: 200m(동네를 넘어감) → 옮긴다", LocationPolicy.shouldFollowMap(37.5, 127.0, 37.5018, 127.0), true)
    expect("거리 0.01도 위도 ≈ 1.1km",
        (LocationPolicy.distanceM(37.5, 127.0, 37.51, 127.0) / 100).toInt(), 11)
    // 정자동 ↔ 동탄이면 지도가 반드시 따라가야 한다
    expect("정자동→동탄 = 따라간다",
        LocationPolicy.shouldFollowMap(37.366, 127.108, 37.200, 127.095), true)

    // ⑤ 감시자 재등록 기준 — 20초 조용하면 다시
    expect("감시자: 등록 실패 → 다시 건다", LocationPolicy.shouldRestartWatchdog(0, now, now), true)
    expect("감시자: 측정 10초 전 → 그대로", LocationPolicy.shouldRestartWatchdog(2, now - 10_000, now), false)
    expect("감시자: 측정 25초 전 → 다시 잰다", LocationPolicy.shouldRestartWatchdog(2, now - 25_000, now), true)
    expect("감시자: 한 번도 측정 없음 → 다시 잰다", LocationPolicy.shouldRestartWatchdog(2, 0L, now), true)

    // ⑥ 오래된 좌표 표시
    expect("좌표 30초 전 = 최신", LocationPolicy.staleLabel(30_000), null)
    expect("좌표 80초 전 = 최신", LocationPolicy.staleLabel(80_000), null)
    expect("좌표 5분 전 = 표시", LocationPolicy.staleLabel(5 * 60_000L), "5분 전 위치")
    expect("좌표 70분 전 = 시 단위", LocationPolicy.staleLabel(70 * 60_000L), "1시간 전 위치")
    expect("좌표 하루 넘음", LocationPolicy.staleLabel(30 * 3_600_000L), "하루 넘게 전 위치")

    println(if (failures == 0) "ALL CHECKS PASSED" else "$failures CHECK(S) FAILED")
    if (failures > 0) kotlin.system.exitProcess(1)
}
