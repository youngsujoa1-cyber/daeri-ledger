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
카카오맵 즐겨찾기에서 옮긴 `app/src/main/res/raw/callspots.tsv`(1,262곳: 콜포인트1 464 · 콜포인트2 441 · 탁송 357)를
행 수·칸 수·id 중복·시·도 값·좌표 범위·200m 같은 이름 중복까지 확인합니다.

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
  $SRC/data/Models.kt $SRC/data/Hotspots.kt $SRC/data/Payout.kt $SRC/data/DcbBackup.kt $SRC/util/TimeUtils.kt \
  $SRC/ui/widget/CalendarGrid.kt $SRC/ui/widget/PressTracker.kt tools/logic-check/Check.kt

java -Duser.timezone=Asia/Seoul -cp "out:$STDLIB" CheckKt
```

마지막 줄이 `ALL CHECKS PASSED` 이면 통과입니다.
