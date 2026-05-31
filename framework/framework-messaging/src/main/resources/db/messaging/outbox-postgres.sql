-- Transactional Outbox 테이블 (PostgreSQL). framework.messaging.enabled=true 일 때 필요.
-- 서비스의 Flyway 마이그레이션(db/migration/V*.sql)으로 복사해 적용할 것.
-- payload/headers 는 드라이버 비의존을 위해 TEXT(JSON 문자열). 보존정책은 운영에서 PUBLISHED 행 주기 정리/파티셔닝 권장.
CREATE TABLE IF NOT EXISTS outbox_event (
    id              BIGSERIAL     PRIMARY KEY,
    event_id        VARCHAR(36)   NOT NULL,          -- UUID. 소비자 멱등 처리 키(Kafka 헤더 x-event-id 로 전달)
    aggregate_type  VARCHAR(128)  NOT NULL,          -- 도메인 종류 (Order, AuditEvent ...)
    aggregate_id    VARCHAR(128)  NOT NULL,          -- 도메인 식별자 (메시지 키 미지정 시 파티션 키)
    event_type      VARCHAR(128)  NOT NULL,          -- 이벤트 종류 (OrderCreated ...)
    topic           VARCHAR(255)  NOT NULL,          -- 발행 대상 Kafka 토픽
    message_key     VARCHAR(255),                    -- 파티션 키(널이면 aggregate_id 사용)
    payload         TEXT          NOT NULL,          -- 직렬화된 JSON 본문
    headers         TEXT,                            -- 추가 헤더 JSON(널 허용)
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',  -- PENDING | PUBLISHED | FAILED
    attempts        INT           NOT NULL DEFAULT 0,
    last_error      VARCHAR(2000),
    trace_id        VARCHAR(64),
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    published_at    TIMESTAMP
);

-- 릴레이 폴링 최적화: 미발행 행만 created/id 순으로 빠르게 스캔(부분 인덱스).
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_event (id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_event_id ON outbox_event (event_id);
