# 순수 로직 검증 하네스

안드로이드 없이 JVM에서 핵심 계산만 돌려보는 검사입니다.
영업일 기준(자정 컷오프), 플랫폼 수수료, 건당 보험료, 순수익, 금액 표기, 콜밭 거리 계산,
달력 그리드의 터치 좌표 → 날짜 판정(눌린 칸 = 그려진 칸), 대리 가계부 백업 변환 규칙을 검증합니다.

v1.0.13 부터는 **팁·경유비 역산이 플랫폼 공통**입니다 — 받은 팁을 설정의 `팁·경유비 %`(기본 20%)로 역산합니다.
로지 45,000원 + 받은 팁 10,000원(20%) → 팁 총액 12,500원 · 총액 57,500원 · 수수료 11,500원 · 순수익 46,000원,
`0%` 로 두면 팁에 수수료가 붙지 않습니다. 역산 플랫폼(카카오T·티맵·핸들)·비역산 플랫폼(로지·콜마너·아이콘·개인콜)
양쪽의 **수정 화면 왕복**(다시 열어도 두 번 역산되지 않음), `v1.0.12` 이전 기록(팁에 수수료가 없던 행) 호환,
반올림이 끼는 금액(33,333 / 3,333)까지 검사합니다.

v1.0.14 부터는 콜밭 데이터도 검사합니다 — 직접 정리한 기본 목록(`Hotspots.CURATED` 318곳)과
카카오맵 즐겨찾기에서 옮긴 `app/src/main/res/raw/callspots.tsv`(`기사 추천` 905곳 · 탁송은 v1.0.15 에서 제외)를
행 수·칸 수·id 중복·시·도 값·좌표 범위·200m 같은 이름 중복까지 확인합니다.

v1.0.15 부터는 콜밭 출처와 내 콜밭을 검사합니다 — `콜포인트1`·`콜포인트2` 를 `기사 추천` 하나로 합치고
출처 칩을 없앤 한 목록(기본 318 + `기사 추천` 905 = 1,223곳, 탁송 콜밭 없음)과 예전 이름(`콜포인트1`·`콜포인트2`·`기사님들 포인트`) 호환,
지역·주소 글에서 시·도 찾기(`cityOfArea`), `+ 콜밭 추가` 로 저장한 콜밭(`MySpot`)이 콜밭 목록 항목으로
바뀌는지(`fromMySpot`: id·출처·내 콜밭 표시·시·도·피크·메모·좌표 유무·검색)를 확인합니다.

v1.0.16 부터는 공용 제보 집계(`SpotAggregate.kt`)를 검사합니다 — **공용 등록 5명 · 공용 삭제 5명** 기준,
4명은 노출·삭제 모두 안 됨, 같은 기기가 5번 올려도 1명, 빈 기기 해시 제외, 150m 묶음(111m는 묶이고 334m는 갈라짐),
5명 등록 + 4명 삭제는 콜밭 유지 / 5명 등록 + 5명 삭제는 콜밭 미노출 + 없어진 자리 1곳, 없어진 자리 반경 150m 안(130m)은 숨김·330m는 유지,
제보 순서가 바뀌어도 같은 결과, `kind` 빈 값은 등록으로 집계, `Hotspots.fromPublicSpot` 의 `기사 제보` 배지·기사 수·마지막 제보·시·도·검색.

v1.0.20 부터는 **보험&세금**을 검사합니다 — 건당 보험료가 붙지 않는 플랫폼은 카카오T·티맵 둘뿐이고
(핸들·로지·콜마너·아이콘·개인콜은 설정값대로 뗌), `Payout.perCallInsuranceFor` 가 카카오T·티맵이면 설정값이 있어도 0원입니다.
사용자 캡처(카카오T 받은 36,000 → 총매출 45,000 · 수수료 9,000 · 보험&세금 1,485(3.3%) · 순수익 34,515),
같은 45,000 을 로지로 넣으면 건당 1,168 + 1,485 = 2,653, 수정 화면 왕복 시 `perCallFromStored` 가 건당 몫만 되돌려
3.3% 가 두 번 붙지 않는지(로지 보험&세금·순수익 유지)까지 확인합니다.

v1.0.23 부터는 **👑 운영자 모드**를 검사합니다 — `SpotAggregator.adminDeviceHash` 가 암호로 **일반 기기 해시와 같은 형식(16진수 32자)** 고정 해시를 만드는지,
운영자 제보 한 건만으로 5명 대기 없이 바로 공개·삭제되는지, 암호를 모르는 집계(`adminHash = null`)에서는
운영자 제보를 일반 제보처럼(1건이라 미노출) 보는지, 운영자 1 + 일반 4 = 5명 표식 유지,
운영자 삭제가 5명 등록을 이기는지 · 330m 밖이면 유지되는지, **더 새 운영자 공개/새 5명 등록이 오래된 삭제를 풀고**
더 새 운영자 삭제는 5명 등록도 막는지(시각 순서), 암호가 있는 집계에서도 일반 5명 기준이 그대로인지 확인합니다.

```bash
# Kotlin 컴파일러/스탠다드 라이브러리는 Gradle 캐시에서 찾습니다.
KJAR=$(find "$GRADLE_USER_HOME/caches/modules-2" -name 'kotlin-compiler-embeddable-2.0.21.jar' | head -1)
STDLIB=$(find "$GRADLE_USER_HOME/caches/modules-2" -name 'kotlin-stdlib-2.0.21.jar' | head -1)
COROUT=$(find "$GRADLE_USER_HOME/caches/modules-2" -name 'kotlinx-coroutines-core-jvm-1.7.3.jar' | head -1)
TROVE=$(find "$GRADLE_USER_HOME/caches/modules-2" -name 'trove4j-1.0.20200330.jar' | head -1)
ANNOT=$(find "$GRADLE_USER_HOME/caches/modules-2" -name 'annotations-23.0.0.jar' | head -1)

SRC=app/src/main/java/com/youngsu/daeriledger
java -cp "$KJAR:$STDLIB:$COROUT:$TROVE:$ANNOT" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -cp "$STDLIB" -d out \
  $SRC/data/Models.kt $SRC/data/Hotspots.kt $SRC/data/SpotAggregate.kt $SRC/data/Payout.kt $SRC/data/DcbBackup.kt $SRC/util/TimeUtils.kt \
  $SRC/ui/widget/CalendarGrid.kt $SRC/ui/widget/PressTracker.kt tools/logic-check/Check.kt

java -Duser.timezone=Asia/Seoul -cp "out:$STDLIB" CheckKt
```

마지막 줄이 `ALL CHECKS PASSED` 이면 통과입니다.
