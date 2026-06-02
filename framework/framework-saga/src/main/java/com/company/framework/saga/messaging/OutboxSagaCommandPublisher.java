package com.company.framework.saga.messaging;

import com.company.framework.messaging.outbox.OutboxEventPublisher;
import com.company.framework.saga.SagaCommandPublisher;
import com.company.framework.saga.SagaHeaders;
import com.company.framework.saga.SagaPhase;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * saga 커맨드를 기존 Transactional Outbox 로 발행한다. 오케스트레이터의 상태변경과 같은 트랜잭션 안에서
 * 호출되어 원자적으로 적재되고, 실제 Kafka 발행은 OutboxRelay 가 비동기로 한다(유실/롤백 안전).
 *
 * <p>상관관계 필드({@link SagaHeaders})는 Outbox 헤더로 실려 단일 {@code x-headers} JSON 으로 전달된다.
 * 참여 서비스는 이를 읽어 같은 saga-id/step/reply-topic 으로 회신한다.
 *
 * <p>컨텍스트(JSON 문자열)는 {@code readValue(.., Object.class)} 로 풀어 페이로드로 넘긴다(이중 따옴표 방지).
 */
public class OutboxSagaCommandPublisher implements SagaCommandPublisher {

    private final OutboxEventPublisher outbox;
    private final JsonMapper jsonMapper;

    public OutboxSagaCommandPublisher(OutboxEventPublisher outbox, JsonMapper jsonMapper) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String publish(
            String topic,
            String type,
            String sagaId,
            int stepIndex,
            SagaPhase phase,
            String replyTopic,
            String contextJson) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(SagaHeaders.X_SAGA_ID, sagaId);
        headers.put(SagaHeaders.X_SAGA_STEP, Integer.toString(stepIndex));
        headers.put(SagaHeaders.X_SAGA_PHASE, phase.name());
        if (replyTopic != null && !replyTopic.isBlank()) {
            headers.put(SagaHeaders.X_SAGA_REPLY_TOPIC, replyTopic);
        }
        Object payload = (contextJson == null || contextJson.isBlank())
                ? Map.of()
                : jsonMapper.readValue(contextJson, Object.class);
        // 파티션 키 = sagaId → 같은 saga 의 커맨드 순서 보장.
        return outbox.publish(topic, "Saga", sagaId, type, sagaId, payload, headers);
    }
}
