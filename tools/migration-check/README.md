# DB 마이그레이션 검사

앱은 Room 대신 표준 SQLite(`SQLiteOpenHelper`)를 씁니다. `v1.0.11`(DB v3)에서 운행 테이블에
경유지(`via_place`) 칸이 늘었습니다. 이 검사는 **예전 버전 스키마 그대로** DB를 만들어
(실사용 규모 11,876건 + 지출 412 + 휴무일 45) 앱의 `onUpgrade`가 하는 일을 적용한 뒤,
기존 값이 하나도 바뀌지 않았는지 확인합니다.

```bash
python3 tools/migration-check/check_migration.py
```

확인하는 것:

- `LedgerDb.kt`의 `DB_VERSION`(3)·칸 이름(`via_place`)·`CREATE TABLE`·`onUpgrade`의 `ALTER TABLE`이 검사와 같은지
- v2 → v3: 운행 11,876건·지출·휴무일 건수 그대로, 기존 칸 순서 그대로, 값 지문(sha256) 동일,
  새 칸은 전부 빈 문자열, `PRAGMA integrity_check` = ok
- 앱이 읽는 순서(`LedgerRepository.toRide`)대로 18칸을 다시 읽어 경유 칸 저장·유지 확인
- v1 → v3: 휴무일 테이블 생성 + 경유 칸 추가, 건수·값 유지
- 칸이 이미 있는 DB에 또 `ALTER` 하면 실패하므로 `hasColumn` 확인이 필요하다는 것

마지막 줄이 `ALL CHECKS PASSED` 이면 통과입니다.
