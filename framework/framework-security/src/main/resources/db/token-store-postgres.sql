-- TokenStore(jdbc) 용 테이블 (PostgreSQL). 서비스 스키마에 포함하거나 별도 실행.
CREATE TABLE IF NOT EXISTS refresh_tokens (
    token       VARCHAR(200) PRIMARY KEY,
    user_id     VARCHAR(100) NOT NULL,
    roles       VARCHAR(500),
    expires_at  TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_exp  ON refresh_tokens(expires_at);

CREATE TABLE IF NOT EXISTS token_blacklist (
    jti         VARCHAR(100) PRIMARY KEY,
    expires_at  TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_blacklist_exp ON token_blacklist(expires_at);
