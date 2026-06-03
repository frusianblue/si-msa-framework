-- V2__auth_signing_key.sql
-- 다중 파드 공유 서명키(JWKS 회전 오버랩). JdbcRotatingJwkSource 가 읽고 쓴다.
--
-- ⚠️ 운영: jwk_json 은 RSA 개인키 원문을 포함한다. 반드시 저장 시점 암호화(컬럼 암호화/KMS/Vault)할 것.
--    본 골격은 평문 — 데모/로컬 한정.

CREATE TABLE IF NOT EXISTS auth_signing_key (
    kid        VARCHAR(100) NOT NULL,
    jwk_json   TEXT         NOT NULL,   -- Nimbus RSAKey JSON(개인키 포함). 운영=암호화 필요.
    status     VARCHAR(20)  NOT NULL,   -- ACTIVE | RETIRED
    created_at TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (kid)
);
CREATE INDEX IF NOT EXISTS idx_signing_key_status_created ON auth_signing_key(status, created_at DESC);
