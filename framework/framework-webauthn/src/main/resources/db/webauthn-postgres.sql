-- 패스키/WebAuthn 자격증명 영속 저장소 테이블 (PostgreSQL).
-- framework.webauthn.store.type=jdbc 로 운영할 때만 필요. memory 구현은 테이블 불필요.
-- 컬럼 정의는 Spring Security 의 JdbcPublicKeyCredentialUserEntityRepository /
--   JdbcUserCredentialRepository 가 요구하는 스키마와 동일하다(원본:
--   classpath:org/springframework/security/user-entities-schema.sql,
--   classpath:org/springframework/security/user-credentials-schema.sql).
-- 원본은 BLOB 타입(H2/HSQLDB용)을 쓰므로, PostgreSQL 에서는 BLOB → BYTEA 로 치환했다.
-- 서비스 스키마에 포함하거나 별도 실행(Flyway 권장).

-- username ↔ user handle(PublicKeyCredentialUserEntity)
CREATE TABLE IF NOT EXISTS user_entities
(
    id           VARCHAR(1000) NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    display_name VARCHAR(200),
    PRIMARY KEY (id)
);

-- 사용자별 등록 자격증명(CredentialRecord)
CREATE TABLE IF NOT EXISTS user_credentials
(
    credential_id                VARCHAR(1000) NOT NULL,
    user_entity_user_id          VARCHAR(1000) NOT NULL,
    public_key                   BYTEA         NOT NULL,
    signature_count              BIGINT,
    uv_initialized               BOOLEAN,
    backup_eligible              BOOLEAN       NOT NULL,
    authenticator_transports     VARCHAR(1000),
    public_key_credential_type   VARCHAR(100),
    backup_state                 BOOLEAN       NOT NULL,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMP,
    last_used                    TIMESTAMP,
    label                        VARCHAR(1000) NOT NULL,
    PRIMARY KEY (credential_id)
);
CREATE INDEX IF NOT EXISTS idx_user_credentials_user ON user_credentials (user_entity_user_id);
