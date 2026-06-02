-- Saga 오케스트레이션 상태 테이블 (PostgreSQL). framework.saga.enabled=true 일 때 필요.
-- 서비스의 Flyway 마이그레이션(db/migration/V*.sql)으로 복사해 적용할 것.
-- context 는 드라이버 비의존을 위해 TEXT(JSON 문자열). 종료(COMPLETED/COMPENSATED/FAILED) 행은 운영에서 주기 정리/파티셔닝 권장.

CREATE TABLE IF NOT EXISTS saga_instance (
    id            BIGSERIAL    PRIMARY KEY,
    saga_id       VARCHAR(36)  NOT NULL UNIQUE,        -- 상관관계 ID(UUID). 커맨드/리플라이 헤더 x-saga-id
    saga_type     VARCHAR(128) NOT NULL,               -- SagaDefinition.name (예: OrderSaga)
    status        VARCHAR(24)  NOT NULL DEFAULT 'RUNNING', -- RUNNING|COMPLETED|COMPENSATING|COMPENSATED|FAILED
    current_step  INT          NOT NULL DEFAULT 0,      -- 진행 중 단계 인덱스
    context       TEXT,                                 -- 단계 간 공유 컨텍스트(JSON)
    last_error    VARCHAR(2000),
    deadline_at   TIMESTAMP,                            -- 현재 단계 응답 기한(초과 시 복구 폴러 대상)
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- 복구 폴러 최적화: 진행 중 + 기한 초과 인스턴스만 빠르게 스캔(부분 인덱스).
CREATE INDEX IF NOT EXISTS idx_saga_stuck ON saga_instance (deadline_at)
    WHERE status IN ('RUNNING', 'COMPENSATING');

CREATE TABLE IF NOT EXISTS saga_step (
    id                BIGSERIAL    PRIMARY KEY,
    saga_id           VARCHAR(36)  NOT NULL,            -- saga_instance.saga_id
    step_index        INT          NOT NULL,
    step_name         VARCHAR(128) NOT NULL,
    phase             VARCHAR(16)  NOT NULL,            -- ACTION | COMPENSATION
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING | DONE | FAILED
    command_event_id  VARCHAR(36),                      -- 발행한 커맨드 x-event-id
    reply_event_id    VARCHAR(36),                      -- 수신한 리플라이 x-event-id
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (saga_id, step_index, phase)                 -- 단계 멱등(중복 리플라이 판정 기반)
);

CREATE INDEX IF NOT EXISTS idx_saga_step_saga ON saga_step (saga_id);
