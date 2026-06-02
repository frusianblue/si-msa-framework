package com.company.framework.saga;

/** saga 커맨드 발행 SPI. 기본 구현은 Outbox 기반(OutboxSagaCommandPublisher) — 상태변경과 한 트랜잭션. */
public interface SagaCommandPublisher {

    /**
     * 참여 서비스로 커맨드 발행.
     *
     * @return 발행 이벤트 ID(x-event-id)
     */
    String publish(
            String topic,
            String type,
            String sagaId,
            int stepIndex,
            SagaPhase phase,
            String replyTopic,
            String contextJson);
}
