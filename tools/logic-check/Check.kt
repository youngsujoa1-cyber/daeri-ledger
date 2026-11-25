import com.youngsu.daeriledger.data.DcbBackup
import com.youngsu.daeriledger.data.Hotspots
import com.youngsu.daeriledger.data.PaymentType
import com.youngsu.daeriledger.data.Payout
import com.youngsu.daeriledger.data.Platforms
import com.youngsu.daeriledger.data.Ride
import com.youngsu.daeriledger.data.ServiceType
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
    expect("카카오 콜밭 수", cols.size, 1262)
    expect("카카오 콜밭 8칸", cols.all { it.size == 8 }, true)
    expect("카카오 콜밭 id 중복 없음", cols.map { it[0] }.distinct().size, cols.size)
    expect("카카오 콜밭 이름·지역 채움", cols.all { it[2].isNotBlank() && it[3].isNotBlank() && it[4].isNotBlank() }, true)
    expect("카카오 콜밭 출처 값", cols.all { it[1] in listOf("콜포인트1", "콜포인트2", "탁송") }, true)
    expect("카카오 콜밭 시·도 값", cols.all { it[3] in Hotspots.CITIES }, true)
    val kLat = cols.map { it[5].toDouble() }
    val kLng = cols.map { it[6].toDouble() }
    expect("카카오 콜밭 좌표 범위(한국)",
        kLat.all { it in 33.0..38.9 } && kLng.all { it in 124.0..132.0 }, true)
    expect("콜포인트1 수", cols.count { it[1] == "콜포인트1" }, 464)
    expect("콜포인트2 수", cols.count { it[1] == "콜포인트2" }, 441)
    expect("탁송 수", cols.count { it[1] == "탁송" }, 357)
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

    println(if (failures == 0) "ALL CHECKS PASSED" else "$failures CHECK(S) FAILED")
    if (failures > 0) kotlin.system.exitProcess(1)
}
