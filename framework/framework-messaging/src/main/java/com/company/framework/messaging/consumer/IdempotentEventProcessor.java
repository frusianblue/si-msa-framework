package com.company.framework.messaging.consumer;

import com.company.framework.idempotency.store.IdempotencyStore;
import java.time.Duration;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 소비자측 멱등 처리 헬퍼. 발행측이 실은 {@code x-event-id} 를 키로 {@link IdempotencyStore} 에 선점 시도하여
 * <b>같은 이벤트의 중복 배달(at-least-once)을 한 번만 처리</b>한다.
 *
 * <h3>동작</h3>
 * <ol>
 *   <li>{@code putIfAbsent(eventId)} 성공 → 이번 배달이 처리 주체 → 핸들러 실행.
 *   <li>실패(이미 처리됨) → 핸들러 스킵(중복).
 *   <li>핸들러가 예외를 던지면 선점 키를 {@code remove} 로 해제 → <b>재배달 시 재처리</b>(유실 방지).
 * </ol>
 *
 * <p>주의: 선점 직후~핸들러 완료 전 인스턴스가 비정상 종료하면 키가 TTL 까지 남아 그 사이 재배달이 스킵될 수 있다
 * (at-least-once 의 본질적 한계). TTL 을 재시도 주기보다 충분히 길게 잡되 무한은 피한다. {@code x-event-id} 가 없으면
 * 멱등 불가로 보고 그대로 처리한다(스킵보다 안전).
 *
 * <h3>사용</h3>
 * <pre>{@code
 * @KafkaListener(topics = "orders", groupId = "billing")
 * public void onMessage(ConsumerRecord<String, String> record) {
 *     eventProcessor.process(record, env -> {
 *         // env.payload() 로 실제 처리
 *     });
 * }
 * }</pre>
 */
public class IdempotentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentEventProcessor.class);

    private final IdempotencyStore store;
    private final Duration ttl;
    private final String keyPrefix;

    public IdempotentEventProcessor(IdempotencyStore store, Duration ttl, String keyPrefix) {
        this.store = store;
        this.ttl = ttl;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
    }

    /** Kafka 레코드의 {@code x-event-id} 헤더로 멱등 처리. @return 처리하면 true, 중복으로 스킵하면 false. */
    public boolean process(ConsumerRecord<?, ?> record, Consumer<EventEnvelope> handler) {
        EventEnvelope envelope = EventEnvelope.from(record);
        return processByEventId(envelope.eventId(), () -> handler.accept(envelope));
    }

    /** 이벤트 ID 를 직접 지정해 멱등 처리. @return 처리하면 true, 중복으로 스킵하면 false. */
    public boolean processByEventId(String eventId, Runnable handler) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("[messaging] x-event-id 없음 — 멱등 처리 불가, 그대로 실행");
            handler.run();
            return true;
        }
        String key = keyPrefix + eventId;
        if (!store.putIfAbsent(key, ttl)) {
            log.debug("[messaging] 중복 이벤트 스킵 eventId={}", eventId);
            return false;
        }
        try {
            handler.run();
            return true;
        } catch (RuntimeException e) {
            // 처리 실패 → 선점 해제하여 재배달 시 재처리 가능하게.
            store.remove(key);
            throw e;
        }
    }
}
