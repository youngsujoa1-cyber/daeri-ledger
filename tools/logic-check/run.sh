#!/usr/bin/env bash
# 순수 로직 검증 하네스 실행 — 안드로이드 없이 JVM 에서 핵심 계산만 돌린다.
# 마지막 줄이 "ALL CHECKS PASSED" 이면 통과.
set -euo pipefail

cd "$(dirname "$0")/../.."

# JDK 17 (없으면 PATH 의 java)
if [ -z "${JAVA_HOME:-}" ] && [ -x /workspace/buildenv/jdk-17.0.21.1+2/bin/java ]; then
  JAVA_HOME=/workspace/buildenv/jdk-17.0.21.1+2
fi
if [ -n "${JAVA_HOME:-}" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

# Gradle 캐시 위치 (없으면 기본 ~/.gradle)
if [ -z "${GRADLE_USER_HOME:-}" ] && [ -d /workspace/buildenv/gradle-home ]; then
  GRADLE_USER_HOME=/workspace/buildenv/gradle-home
fi
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

find_jar() { find "$GRADLE_USER_HOME/caches/modules-2" -name "$1" | head -1; }

KJAR=$(find_jar 'kotlin-compiler-embeddable-2.0.21.jar')
STDLIB=$(find_jar 'kotlin-stdlib-2.0.21.jar')
COROUT=$(find_jar 'kotlinx-coroutines-core-jvm-1.7.3.jar')
TROVE=$(find_jar 'trove4j-1.0.20200330.jar')
ANNOT=$(find_jar 'annotations-23.0.0.jar')

SRC=app/src/main/java/com/youngsu/daeriledger
OUT=tools/logic-check/out
rm -rf "$OUT"

"$JAVA" -cp "$KJAR:$STDLIB:$COROUT:$TROVE:$ANNOT" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -cp "$STDLIB" -d "$OUT" \
  "$SRC/data/Models.kt" "$SRC/data/Hotspots.kt" "$SRC/data/SpotAggregate.kt" "$SRC/data/SpotTimeBands.kt" "$SRC/data/RideSpotGate.kt" "$SRC/data/PlaceNamer.kt" "$SRC/data/Payout.kt" \
  "$SRC/data/DcbBackup.kt" "$SRC/util/TimeUtils.kt" "$SRC/ui/widget/CalendarGrid.kt" \
  "$SRC/ui/widget/PressTracker.kt" tools/logic-check/Check.kt

"$JAVA" -Duser.timezone=Asia/Seoul -cp "$OUT:$STDLIB" CheckKt
