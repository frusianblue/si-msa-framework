-- V4__framework_lock.sql
-- framework-lock 의 분산 락 테이블(type=jdbc). 서명키 회전(@SchedulerLock = auth-signing-key-rotation)의 리더 선출에 사용.
-- 다중 파드에서 한 파드만 회전하도록 lock_key 유니크(PK) + 만료 재획득으로 상호배제한다.
--
-- 이식성: framework/framework-lock/src/main/resources/db/lock-postgres.sql 과 동일 스키마(VARCHAR/TIMESTAMP 만).
-- H2(MODE=PostgreSQL)·PostgreSQL 공통. 운영은 Flyway 로 관리(본 마이그레이션), 로컬 H2 도 동일 DDL 로 생성된다.

CREATE TABLE IF NOT EXISTS framework_lock (
    lock_key    VARCHAR(200)  PRIMARY KEY,   -- 락 이름(업무 단위 유니크). 상호배제 기준. 회전 = 'auth-signing-key-rotation'
    lock_owner  VARCHAR(100)  NOT NULL,      -- 소유자 토큰(보통 UUID). 해제/연장은 이 값 일치 시에만
    expires_at  TIMESTAMP     NOT NULL,      -- 리스 만료 시각. 이 시각 이후면 재획득 허용(보유자 사망 대비 자동 해제)
    created_at  TIMESTAMP     NOT NULL       -- 최초(또는 재)획득 시각
);

CREATE INDEX IF NOT EXISTS idx_framework_lock_expires ON framework_lock(expires_at);
