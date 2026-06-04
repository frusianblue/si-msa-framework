-- V5__auth_signing_key_retired_at.sql
-- 서명키 회전 grace 정리 기준 컬럼. RETIRE 된 "시각"을 기록한다.
--
-- ⚠️ 왜 created_at 이 아니라 retired_at 인가:
--   grace 는 "폐기 후 보존기간"(오버랩 윈도)이다. created_at 기준으로 정리하면 grace(예 14d) < 회전주기(예 30d)일 때
--   직전 키가 RETIRE 되는 즉시(생성 30d 전 > 14d) 삭제돼 버려, 회전 직전 발급된 토큰을 검증할 키가 사라진다(오버랩 붕괴).
--   retired_at 기준이면 "폐기된 지 grace 가 지난" 키만 삭제돼 오버랩이 보장된다.
--
-- 이식성: H2(MODE=PostgreSQL)·PostgreSQL 모두 ADD COLUMN IF NOT EXISTS 지원. ACTIVE 행은 NULL(아직 폐기 전).

ALTER TABLE auth_signing_key ADD COLUMN IF NOT EXISTS retired_at TIMESTAMP NULL;
