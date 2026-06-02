-- 2단계 인증(MFA) 등록 저장소 테이블 (PostgreSQL).
-- framework.mfa.enrollment.store.type=jdbc 로 운영할 때만 필요. memory 구현은 테이블 불필요.
-- 챌린지 저장소(challenge.store)는 단기 대기상태라 DB 테이블이 아니라 memory|redis 만 지원(멀티 인스턴스는 redis 필수).
-- 서비스 스키마에 포함하거나 별도 실행.

-- 사용자별 MFA 수단 등록(TOTP 시크릿 / OTP 활성화 + 복구코드 해시).
-- (user_id, method) 당 1행. recovery_hashes 는 일회용 복구코드의 SHA-256 hex 를 쉼표로 연결(해시는 hex 라 쉼표 충돌 없음).
-- secret 은 TOTP Base32 시크릿(OTP 수단은 비어 있을 수 있음). 미확정(confirmed=false) 행은 로그인에 사용되지 않는다.
CREATE TABLE IF NOT EXISTS mfa_enrollment (
    user_id         VARCHAR(100) NOT NULL,
    method          VARCHAR(20)  NOT NULL,   -- TOTP | OTP
    secret          VARCHAR(200),            -- TOTP Base32 시크릿
    recovery_hashes TEXT,                    -- 일회용 복구코드 SHA-256 hex, 쉼표 연결
    confirmed       BOOLEAN      NOT NULL,    -- 코드 검증으로 확정되었는지
    created_at      TIMESTAMP    NOT NULL,
    PRIMARY KEY (user_id, method)
);
CREATE INDEX IF NOT EXISTS idx_mfa_enrollment_user ON mfa_enrollment(user_id);
