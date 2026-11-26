#!/usr/bin/env bash
# DB 마이그레이션 검사 — 예전 스키마 DB 로 onUpgrade 를 적용하고 값이 그대로인지 확인한다.
# 마지막 줄이 "ALL CHECKS PASSED" 이면 통과.
set -euo pipefail

cd "$(dirname "$0")/../.."

command -v python3 >/dev/null || { echo "python3 가 필요합니다"; exit 1; }
python3 tools/migration-check/check_migration.py
