# 순수 로직 검증 하네스

안드로이드 없이 JVM에서 핵심 계산만 돌려보는 검사입니다.
영업일 기준(자정 컷오프), 플랫폼 수수료, 건당 보험료, 순수익, 금액 표기, 콜밭 거리 계산,
달력 그리드의 터치 좌표 → 날짜 판정(눌린 칸 = 그려진 칸), 대리 가계부 백업 변환 규칙을 검증합니다.

v1.0.12 부터는 **팁·경유비 역산**(카카오T·티맵·핸들: 받은 8,000원 → 총액 10,000원·수수료 2,000원),
비역산 플랫폼(로지·콜마너·아이콘·개인콜), **수정 화면 왕복**(다시 열어도 두 번 역산되지 않음),
`v1.0.11` 이전 기록(팁에 수수료가 없던 행) 호환까지 검사합니다.

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
