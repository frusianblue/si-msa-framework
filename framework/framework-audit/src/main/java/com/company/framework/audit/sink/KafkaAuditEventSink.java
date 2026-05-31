package com.company.framework.audit.sink;

import com.company.framework.audit.model.AuditEvent;
import com.company.framework.messaging.outbox.OutboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 감사 이벤트를 framework-messaging 의 Transactional Outbox 로 발행하는 싱크.
 *
 * <p>직접 Kafka 로 쏘지 않고 outbox 에 적재한다 → 진행 중 비즈니스 트랜잭션과 같은 커밋 단위로 기록되어(메서드 감사처럼
 * {@code @Transactional} 안에서 발생하는 경우) "비즈니스 커밋 ⇔ 감사 이벤트" 원자성이 보장되고, 실제 발행은 릴레이가 한다.
 *
 * <p>감사 싱크 계약대로 적재 실패는 비즈니스 흐름을 깨지 않도록 삼키고 로깅만 한다. (outbox INSERT 예외를 sink 안에서
 * 잡으므로 트랜잭션이 rollback-only 로 더럽혀지지 않는다 — 감사는 best-effort.)
 *
 * <p>활성 조건: 서비스가 framework-audit 와 framework-messaging 을 함께 의존하고 {@code framework.audit.store.type=kafka}.
 */
public class KafkaAuditEventSink implements AuditEventSink {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditEventSink.class);

    private static final String AGGREGATE_TYPE = "AuditEvent";

    private final OutboxEventPublisher publisher;
    private final String topic;

    public KafkaAuditEventSink(OutboxEventPublisher publisher, String topic) {
        this.publisher = publisher;
        this.topic = topic;
    }

    @Override
    public void save(AuditEvent e) {
        try {
            // aggregate_id 는 NOT NULL → 미인증 등 actor 부재 시 "anonymous" 로 보정(같은 행위자 이벤트 순서 보장 키).
            String actor = (e.actor() != null && !e.actor().isBlank()) ? e.actor() : "anonymous";
            publisher.publish(topic, AGGREGATE_TYPE, actor, e.eventType(), e);
        } catch (RuntimeException ex) {
            log.warn(
                    "감사 이벤트 outbox 적재 실패(무시하고 진행): type={} action={} actor={}",
                    e.eventType(),
                    e.action(),
                    e.actor(),
                    ex);
        }
    }
}
