-- 패스키/WebAuthn 자격증명 영속 저장소 테이블 (H2 / HSQLDB — 로컬·테스트).
-- Spring Security 가 클래스패스로 제공하는 원본 스키마와 동일하다(BLOB 타입). 따라서 로컬/H2 에서는
--   별도로 이 파일 대신 SS 원본 리소스를 직접 로드해도 된다:
--     classpath:org/springframework/security/user-entities-schema.sql
--     classpath:org/springframework/security/user-credentials-schema.sql
-- (PostgreSQL 운영은 BYTEA 가 필요하므로 webauthn-postgres.sql 사용)

CREATE TABLE IF NOT EXISTS user_entities
(
    id           VARCHAR(1000) NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    display_name VARCHAR(200),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS user_credentials
(
    credential_id                VARCHAR(1000) NOT NULL,
    user_entity_user_id          VARCHAR(1000) NOT NULL,
    public_key                   BLOB          NOT NULL,
    signature_count              BIGINT,
    uv_initialized               BOOLEAN,
    backup_eligible              BOOLEAN       NOT NULL,
    authenticator_transports     VARCHAR(1000),
    public_key_credential_type   VARCHAR(100),
    backup_state                 BOOLEAN       NOT NULL,
    attestation_object           BLOB,
    attestation_client_data_json BLOB,
    created                      TIMESTAMP,
    last_used                    TIMESTAMP,
    label                        VARCHAR(1000) NOT NULL,
    PRIMARY KEY (credential_id)
);
