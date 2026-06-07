-- =====================================================================================
-- V3 — batch-types 도메인. 공유 소스 txn + 잡별 결과 테이블.
--   txn 은 6개 잡이 공통으로 읽는 원천(미리 12행 적재). 잡마다 자기 결과 테이블에만 쓴다.
-- =====================================================================================

-- 공유 소스(원천 거래)
CREATE TABLE IF NOT EXISTS txn (
    id          BIGINT PRIMARY KEY,
    merchant_id VARCHAR(32)    NOT NULL,
    amount      NUMERIC(15, 2) NOT NULL,
    status      VARCHAR(16)    NOT NULL  -- APPROVED / CANCELED
);

-- 잡별 결과 테이블
CREATE TABLE IF NOT EXISTS txn_graded (
    id          BIGINT PRIMARY KEY,
    merchant_id VARCHAR(32) NOT NULL,
    grade       VARCHAR(2)  NOT NULL
);

CREATE TABLE IF NOT EXISTS txn_settled (
    id          BIGINT PRIMARY KEY,
    merchant_id VARCHAR(32)    NOT NULL,
    net_amount  NUMERIC(15, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS txn_enriched (
    id          BIGINT PRIMARY KEY,
    merchant_id VARCHAR(32)    NOT NULL,
    amount      NUMERIC(15, 2) NOT NULL,
    fee         NUMERIC(15, 2) NOT NULL,
    net_amount  NUMERIC(15, 2) NOT NULL,
    category    VARCHAR(16)    NOT NULL
);

CREATE TABLE IF NOT EXISTS txn_mybatis_out (
    id          BIGINT PRIMARY KEY,
    merchant_id VARCHAR(32)    NOT NULL,
    amount      NUMERIC(15, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS txn_proc_out (
    id          BIGINT PRIMARY KEY,
    merchant_id VARCHAR(32)    NOT NULL,
    amount      NUMERIC(15, 2) NOT NULL
);

-- 샘플 12행: 대형(>=1,000,000) 2건, 취소 2건 포함
INSERT INTO txn (id, merchant_id, amount, status) VALUES
    (1,  'M001',   12000.00, 'APPROVED'),
    (2,  'M001',  150000.00, 'APPROVED'),
    (3,  'M002',   89000.00, 'APPROVED'),
    (4,  'M002', 1200000.00, 'APPROVED'),
    (5,  'M003',     5000.00, 'CANCELED'),
    (6,  'M003',  240000.00, 'APPROVED'),
    (7,  'M004',   33000.00, 'APPROVED'),
    (8,  'M004',  990000.00, 'APPROVED'),
    (9,  'M005', 2500000.00, 'APPROVED'),
    (10, 'M005',    18000.00, 'CANCELED'),
    (11, 'M006',   76000.00, 'APPROVED'),
    (12, 'M006',  410000.00, 'APPROVED')
ON CONFLICT (id) DO NOTHING;
