package com.company.framework.messaging.outbox;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비즈니스 코드가 호출하는 신뢰성 발행 진입점(Transactional Outbox).
 *
 * <p>{@code @Transactional} 메서드 안에서 호출하면 비즈니스 데이터와 같은 커밋 단위로 outbox 에 적재되어,
 * 트랜잭션이 커밋돼야만 이벤트가 남는다(롤백 시 유령 이벤트 없음). 실제 Kafka 발행은 {@link OutboxRelay} 가 비동기로 한다.
 *
 * <pre>{@code
 * @Transactional
 * public void placeOrder(Order o) {
 *     orderMapper.insert(o);
 *     outboxPublisher.publish("order-events", "Order", o.getId(), "OrderCreated", o);
 * }
 * }</pre>
 *
 * <p>직렬화 실패(JSON 변환 불가)는 의도적으로 호출자에게 전파되어 비즈니스 트랜잭션을 롤백시킨다.
 */
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxRepository repository;
    private final JsonMapper jsonMapper;

    public OutboxEventPublisher(OutboxRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    /** 기본 발행: 파티션 키는 aggregateId 로, 커스텀 헤더 없이. */
    public String publish(String topic, String aggregateType, String aggregateId, String eventType, Object payload) {
        return publish(topic, aggregateType, aggregateId, eventType, null, payload, null);
    }

    /**
     * 전체 발행.
     *
     * @param messageKey 파티션 키(널이면 aggregateId 사용)
     * @param headers 메시지 헤더(널 허용). JSON 으로 직렬화되어 헤더로 함께 전달된다.
     * @return 생성된 eventId(UUID 문자열) — 소비자 멱등 처리 키
     */
    public String publish(
            String topic,
            String aggregateType,
            String aggregateId,
            String eventType,
            String messageKey,
            Object payload,
            Map<String, String> headers) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug(
                    "Outbox 적재가 트랜잭션 밖에서 호출됨(원자성 미보장): topic={} type={}. @Transactional 안에서 호출 권장.", topic, eventType);
        }
        String eventId = UUID.randomUUID().toString();
        String payloadJson = jsonMapper.writeValueAsString(payload); // Jackson 3: 실패 시 unchecked 전파(트랜잭션 롤백)
        String headersJson = (headers == null || headers.isEmpty()) ? null : jsonMapper.writeValueAsString(headers);
        repository.append(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                messageKey,
                payloadJson,
                headersJson,
                MDC.get("traceId"));
        return eventId;
    }
}
