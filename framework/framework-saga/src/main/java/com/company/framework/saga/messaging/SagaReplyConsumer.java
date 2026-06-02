package com.company.framework.saga.messaging;

import com.company.framework.messaging.MessagingHeaders;
import com.company.framework.saga.SagaHeaders;
import com.company.framework.saga.SagaOrchestrator;
import com.company.framework.saga.SagaOutcome;
import com.company.framework.saga.SagaPhase;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * 참여 서비스가 보낸 리플라이 레코드를 오케스트레이터로 전달하는 헬퍼. 앱이 리플라이 토픽의
 * {@code @KafkaListener} 에서 호출한다(컨테이너 구성은 앱 소유 — messaging 의 IdempotentEventProcessor 와 동일 패턴).
 *
 * <pre>{@code
 * @KafkaListener(topics = "saga-replies", groupId = "order-orchestrator")
 * public void onReply(ConsumerRecord<String, String> record) {
 *     sagaReplyConsumer.handle(record);
 * }
 * }</pre>
 *
 * <p>상관관계는 Outbox 가 실어 보낸 {@code x-headers} JSON 안의 saga 헤더에서 읽는다. 멱등(중복 리플라이)은
 * 오케스트레이터가 단계 상태로 보장하므로 여기서는 별도 처리하지 않는다.
 */
public class SagaReplyConsumer {

    private static final Logger log = LoggerFactory.getLogger(SagaReplyConsumer.class);

    private final SagaOrchestrator orchestrator;
    private final JsonMapper jsonMapper;

    public SagaReplyConsumer(SagaOrchestrator orchestrator, JsonMapper jsonMapper) {
        this.orchestrator = orchestrator;
        this.jsonMapper = jsonMapper;
    }

    public void handle(ConsumerRecord<?, ?> record) {
        String headersJson = header(record, MessagingHeaders.X_HEADERS);
        if (headersJson == null) {
            log.warn("saga 리플라이에 x-headers 없음 → 무시 topic={}", record.topic());
            return;
        }
        Map<?, ?> h = jsonMapper.readValue(headersJson, Map.class);
        String sagaId = str(h.get(SagaHeaders.X_SAGA_ID));
        String stepStr = str(h.get(SagaHeaders.X_SAGA_STEP));
        String phaseStr = str(h.get(SagaHeaders.X_SAGA_PHASE));
        String outcomeStr = str(h.get(SagaHeaders.X_SAGA_OUTCOME));
        if (sagaId == null || stepStr == null || phaseStr == null || outcomeStr == null) {
            log.warn(
                    "saga 리플라이 헤더 누락 → 무시 sagaId={} step={} phase={} outcome={}",
                    sagaId,
                    stepStr,
                    phaseStr,
                    outcomeStr);
            return;
        }
        String replyEventId = header(record, MessagingHeaders.X_EVENT_ID);
        String replyPayload = record.value() == null ? null : record.value().toString();
        orchestrator.onReply(
                sagaId,
                Integer.parseInt(stepStr),
                SagaPhase.valueOf(phaseStr),
                SagaOutcome.valueOf(outcomeStr),
                replyEventId,
                replyPayload);
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
