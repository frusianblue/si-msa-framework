-- =====================================================================================
-- batch-cookbook 도메인 스키마 + 샘플 데이터 — 5개 Job 이 각각 독립 실행되도록 소스 테이블을 미리 채운다.
-- (운영에선 도메인 스키마도 별도 마이그레이션/형상으로 관리. 여기선 데모 편의를 위해 한 파일로.)
-- =====================================================================================

-- [Job1 fileIngestJob] 파일 적재 대상(스테이징). 시작 시 비어 있고, CSV 를 읽어 채운다.
CREATE TABLE incoming_transaction (
    id          BIGINT         NOT NULL PRIMARY KEY,
    merchant_id VARCHAR(40)    NOT NULL,
    amount      NUMERIC(18, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    trade_date  DATE           NOT NULL
);

-- [Job2 reportExportJob] 추출(export) 소스. 미리 채워 둬 단독 실행 시에도 CSV 가 나오게 한다.
CREATE TABLE settlement_result (
    merchant_id VARCHAR(40)    NOT NULL,
    trade_date  DATE           NOT NULL,
    net_amount  NUMERIC(18, 2) NOT NULL
);
INSERT INTO settlement_result (merchant_id, trade_date, net_amount) VALUES
    ('M001', DATE '2026-06-01', 985000.00),
    ('M001', DATE '2026-06-02', 1200500.00),
    ('M002', DATE '2026-06-01', 47250.00),
    ('M003', DATE '2026-06-02', 3310000.00);

-- [Job3 dormantAccountJob] 휴면 전환 대상. last_active_date 가 1년 넘은 ACTIVE 계좌가 후보가 된다.
CREATE TABLE account (
    id                BIGINT      NOT NULL PRIMARY KEY,
    owner             VARCHAR(60) NOT NULL,
    last_active_date  DATE        NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    dormant_candidate BOOLEAN     NOT NULL DEFAULT false
);
INSERT INTO account (id, owner, last_active_date, status) VALUES
    (1, '김활동', CURRENT_DATE - INTERVAL '10 day', 'ACTIVE'),    -- 최근 활동 → 후보 아님
    (2, '이오랜', CURRENT_DATE - INTERVAL '400 day', 'ACTIVE'),   -- 1년 초과 → 후보
    (3, '박장기', CURRENT_DATE - INTERVAL '800 day', 'ACTIVE'),   -- 1년 초과 → 후보
    (4, '최이미', CURRENT_DATE - INTERVAL '900 day', 'DORMANT');  -- 이미 휴면 → 제외

-- [Job4 purgeJob] 정리 대상 감사 로그. 보관기한(기본 90일) 초과분이 삭제된다.
CREATE TABLE audit_log (
    id         BIGINT       NOT NULL PRIMARY KEY,
    event      VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL
);
INSERT INTO audit_log (id, event, created_at) VALUES
    (1, 'LOGIN',  CURRENT_TIMESTAMP - INTERVAL '5 day'),    -- 보존
    (2, 'LOGIN',  CURRENT_TIMESTAMP - INTERVAL '100 day'),  -- 삭제 대상
    (3, 'EXPORT', CURRENT_TIMESTAMP - INTERVAL '200 day'),  -- 삭제 대상
    (4, 'LOGOUT', CURRENT_TIMESTAMP - INTERVAL '1 day');    -- 보존

-- [Job5 interestAccrualJob] 이자 계산 소스. 음수 잔액 1건을 섞어 skip 동작을 보여 준다.
CREATE TABLE deposit (
    id         BIGINT         NOT NULL PRIMARY KEY,
    account_no VARCHAR(34)    NOT NULL,
    balance    NUMERIC(18, 2) NOT NULL,
    rate       NUMERIC(6, 4)  NOT NULL
);
INSERT INTO deposit (id, account_no, balance, rate) VALUES
    (1, '110-001', 1000000.00, 0.0250),   -- 정상 → 이자 25000
    (2, '110-002', 5000000.00, 0.0310),   -- 정상 → 이자 155000
    (3, '110-003',       0.00, 0.0250),   -- 잔액 0 → 필터(null)
    (4, '110-004',  -50000.00, 0.0250),   -- 음수 → IllegalArgumentException → skip
    (5, '110-005',  250000.00, 0.0150);   -- 정상 → 이자 3750

-- [Job5] 이자 계산 결과 적재 대상.
CREATE TABLE interest_accrual (
    id           BIGSERIAL      NOT NULL PRIMARY KEY,
    account_no   VARCHAR(34)    NOT NULL,
    base_balance NUMERIC(18, 2) NOT NULL,
    interest     NUMERIC(18, 2) NOT NULL
);
