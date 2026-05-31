-- 감사/접속 로그 적재 테이블 (PostgreSQL). framework.audit.store.type=jdbc 일 때만 필요.
-- 보존기간(예: 1년) 은 운영 정책에 맞춰 파티셔닝/아카이브 권장.
CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    event_time  TIMESTAMP    NOT NULL,
    event_type  VARCHAR(40)  NOT NULL,     -- METHOD_AUDIT | LOGIN_SUCCESS | LOGIN_FAILURE | LOGOUT
    actor       VARCHAR(100),              -- 행위자(사용자 ID), 미인증은 anonymous
    action      VARCHAR(100),              -- @AuditLog.action 또는 AUTH
    target      VARCHAR(100),              -- @AuditLog.target 또는 SESSION
    result      VARCHAR(20),               -- SUCCESS | FAILURE
    client_ip   VARCHAR(64),
    trace_id    VARCHAR(64),
    detail      VARCHAR(1000),
    elapsed_ms  BIGINT
);
CREATE INDEX IF NOT EXISTS idx_audit_time  ON audit_log(event_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_log(actor, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_type  ON audit_log(event_type, event_time DESC);
