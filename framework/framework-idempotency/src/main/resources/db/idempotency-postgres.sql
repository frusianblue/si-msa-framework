-- 멱등 결과 저장 테이블. framework.idempotency.store.type=jdbc 일 때만 필요.
-- DB 비종속(H2/PostgreSQL/Oracle 공통): VARCHAR/TIMESTAMP 만 사용. 운영은 Flyway(db/migration)로 관리 권장.
-- 만료행 정리는 putIfAbsent 가 동일 키 한정으로 수행하므로, 전체 정리는 운영 잡(DELETE ... WHERE expires_at <= now)으로 별도 권장.
CREATE TABLE IF NOT EXISTS framework_idempotency (
    idem_key    VARCHAR(200)  PRIMARY KEY,   -- Idempotency-Key 헤더 값(요청 단위 유니크). 선점/중복 판정의 기준
    result      VARCHAR(4000),               -- 처리 결과 스냅샷(JSON 등). 선점만 된 상태면 NULL. 대용량이면 TEXT/CLOB 로 확장
    expires_at  TIMESTAMP     NOT NULL,      -- TTL 만료 시각. 이 시각 이후면 재선점 허용/결과 미반환
    created_at  TIMESTAMP     NOT NULL       -- 최초(또는 재)선점 시각
);

-- 만료 청소 잡/조회 가속용 인덱스.
CREATE INDEX IF NOT EXISTS idx_framework_idempotency_expires ON framework_idempotency(expires_at);
