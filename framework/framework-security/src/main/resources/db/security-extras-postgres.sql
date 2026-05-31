-- 보안 완성(ISMS-P) 용 테이블 (PostgreSQL).
-- store.type=jdbc 로 운영할 때만 필요. memory 구현은 테이블 불필요.
-- 서비스 스키마에 포함하거나 별도 실행.

-- 비밀번호 이력(직전 N개 재사용 금지) + 마지막 변경시각 판정
-- framework.security.password.history.store.type=jdbc
CREATE TABLE IF NOT EXISTS password_history (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       VARCHAR(100) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,   -- 항상 인코딩(BCrypt)된 값
    created_at    TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_pwhist_user ON password_history(user_id, created_at DESC);

-- 동시(중복) 로그인 제어용 활성 세션
-- framework.security.concurrent-session.store.type=jdbc
CREATE TABLE IF NOT EXISTS active_sessions (
    session_id    VARCHAR(200) PRIMARY KEY,  -- 로그인 1회당 고유(refresh token)
    user_id       VARCHAR(100) NOT NULL,
    access_jti    VARCHAR(100),              -- 강제 로그아웃 시 access token 블랙리스트 키
    refresh_token VARCHAR(200),
    issued_at     TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_active_user ON active_sessions(user_id, issued_at DESC);
