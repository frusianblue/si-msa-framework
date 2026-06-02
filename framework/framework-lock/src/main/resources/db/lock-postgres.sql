-- 분산 락 테이블. framework.lock.type=jdbc 일 때만 필요.
-- DB 비종속(H2/PostgreSQL/Oracle 공통): VARCHAR/TIMESTAMP 만 사용. 운영은 Flyway(db/migration)로 관리 권장.
-- 만료 락 정리는 tryLock 이 동일 키 한정으로 수행한다(획득 직전 DELETE ... WHERE lock_key=? AND expires_at<=now).
-- 전체 청소가 필요하면 운영 잡(DELETE ... WHERE expires_at <= now)으로 별도 권장.
CREATE TABLE IF NOT EXISTS framework_lock (
    lock_key    VARCHAR(200)  PRIMARY KEY,   -- 락 이름(업무 단위 유니크). 상호배제의 기준
    lock_owner  VARCHAR(100)  NOT NULL,      -- 소유자 토큰(보통 UUID). 해제/연장은 이 값이 일치할 때만
    expires_at  TIMESTAMP     NOT NULL,      -- 리스 만료 시각. 이 시각 이후면 재획득 허용(보유자 사망 대비 자동 해제)
    created_at  TIMESTAMP     NOT NULL       -- 최초(또는 재)획득 시각
);

-- 만료 청소/조회 가속용 인덱스.
CREATE INDEX IF NOT EXISTS idx_framework_lock_expires ON framework_lock(expires_at);
