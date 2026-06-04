-- V6__auth_authorization_device_user_code.sql
-- oauth2_authorization 를 SS7(Spring Security 7.0 흡수 SAS) 정본 스키마와 정합시킨다.
--
-- 배경(이 테스트가 잡은 실제 버그):
--   SS7 의 JdbcOAuth2AuthorizationService 는 INSERT/UPDATE/SELECT 문에 device_code/user_code 컬럼을
--   "채택 그랜트와 무관하게 항상" 고정 포함한다(서비스 내부 고정 컬럼 목록). V1 은 SAS 1.0 시절 핵심 컬럼만
--   두어 아래 8개가 빠져 있었다. 기동(토큰 미발급) 시에는 드러나지 않다가, 첫 토큰 발급(client_credentials /
--   authorization_code 모두) INSERT 에서 H2 가 미존재 컬럼을 grammar 오류로 보고 →
--   BadSqlGrammarException(JdbcSQLSyntaxErrorException) 으로 터졌다.
--
-- 정본 대조: spring-security 7.0.0
--   org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql
--   정본은 blob/timestamp 로 정의하고, 파일 상단 주석이 "PostgreSQL 은 blob→text 로 바꾸라"고 명시한다.
--   → V1 규약 그대로 *_value/*_metadata = TEXT, *_issued_at/*_expires_at = TIMESTAMP 로 둔다
--     (H2(MODE=PostgreSQL) 호환 우선 — V1 이 TIMESTAMPTZ 를 피한 것과 동일한 이유).
--
-- V1 을 직접 고치지 않는 이유: 이미 적용된 마이그레이션은 불변이어야 Flyway 체크섬 검증이 깨지지 않는다(dev/prod).
-- ALTER ... ADD COLUMN IF NOT EXISTS 는 H2 와 PostgreSQL(9.6+) 모두 지원 → 멱등.

-- ===== Device Authorization Grant: user_code (사용자 코드) =====
ALTER TABLE oauth2_authorization ADD COLUMN IF NOT EXISTS user_code_value      TEXT      DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD COLUMN IF NOT EXISTS user_code_issued_at  TIMESTAMP DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD COLUMN IF NOT EXISTS user_code_expires_at TIMESTAMP DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD COLUMN IF NOT EXISTS user_code_metadata   TEXT      DEFAULT NULL;

-- ===== Device Authorization Grant: device_code (디바이스 코드) =====
ALTER TABLE oauth2_authorization ADD COLUMN IF NOT EXISTS device_code_value      TEXT      DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD COLUMN IF NOT EXISTS device_code_issued_at  TIMESTAMP DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD COLUMN IF NOT EXISTS device_code_expires_at TIMESTAMP DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD COLUMN IF NOT EXISTS device_code_metadata   TEXT      DEFAULT NULL;
