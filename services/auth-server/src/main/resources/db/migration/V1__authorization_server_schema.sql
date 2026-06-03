-- V1__authorization_server_schema.sql
-- Spring Authorization Server 정본 스키마의 PostgreSQL 적응본.
--
-- ⚠️ 정본 출처(반드시 대조): 실행 중인 SS7/SAS 버전의 jar 안
--    org/springframework/security/oauth2/server/authorization/
--      oauth2-registered-client-schema.sql
--      oauth2-authorization-schema.sql
--      oauth2-authorization-consent-schema.sql
--    SAS 버전에 따라 컬럼이 추가될 수 있다(device_code/user_code, DPoP 등).
--    Jdbc*Service 는 컬럼이 정확히 맞아야 동작하므로, 받는 쪽에서 jar 정본과 대조 후 확정할 것.
--    본 골격은 채택 그랜트(authorization_code + PKCE, client_credentials)에 필요한 핵심 컬럼만 둔다(device 흐름 미채택).

-- ===== 등록 클라이언트 =====
CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id                            VARCHAR(100)  NOT NULL,
    client_id                     VARCHAR(100)  NOT NULL,
    client_id_issued_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    client_secret                 VARCHAR(200)  DEFAULT NULL,
    client_secret_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    client_name                   VARCHAR(200)  NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL,
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id)
);

-- ===== 인가/토큰 상태 =====
-- attributes/*_metadata/*_value 는 JSON·토큰 원문이라 길이 제한 없는 TEXT.
CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                            VARCHAR(100) NOT NULL,
    registered_client_id          VARCHAR(100) NOT NULL,
    principal_name                VARCHAR(200) NOT NULL,
    authorization_grant_type      VARCHAR(100) NOT NULL,
    authorized_scopes             VARCHAR(1000) DEFAULT NULL,
    attributes                    TEXT          DEFAULT NULL,
    state                         VARCHAR(500)  DEFAULT NULL,
    authorization_code_value      TEXT          DEFAULT NULL,
    authorization_code_issued_at  TIMESTAMPTZ   DEFAULT NULL,
    authorization_code_expires_at TIMESTAMPTZ   DEFAULT NULL,
    authorization_code_metadata   TEXT          DEFAULT NULL,
    access_token_value            TEXT          DEFAULT NULL,
    access_token_issued_at        TIMESTAMPTZ   DEFAULT NULL,
    access_token_expires_at       TIMESTAMPTZ   DEFAULT NULL,
    access_token_metadata         TEXT          DEFAULT NULL,
    access_token_type             VARCHAR(100)  DEFAULT NULL,
    access_token_scopes           VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value           TEXT          DEFAULT NULL,
    oidc_id_token_issued_at       TIMESTAMPTZ   DEFAULT NULL,
    oidc_id_token_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    oidc_id_token_metadata        TEXT          DEFAULT NULL,
    refresh_token_value           TEXT          DEFAULT NULL,
    refresh_token_issued_at       TIMESTAMPTZ   DEFAULT NULL,
    refresh_token_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    refresh_token_metadata        TEXT          DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_principal ON oauth2_authorization(principal_name);

-- ===== 동의 =====
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
