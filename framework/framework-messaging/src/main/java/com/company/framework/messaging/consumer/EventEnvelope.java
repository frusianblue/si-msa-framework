package com.company.framework.messaging.consumer;

import com.company.framework.messaging.MessagingHeaders;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

/**
 * 소비한 Kafka 레코드를 표준 헤더({@link MessagingHeaders}) 기준으로 풀어 놓은 편의 모델.
 *
 * @param eventId 멱등 키(헤더 {@code x-event-id}, 없을 수 있음)
 * @param eventType 이벤트 종류(헤더 {@code x-event-type})
 * @param aggregateType 애그리거트 종류(헤더 {@code x-aggregate-type})
 * @param key 레코드 키(파티션 키)
 * @param payload 레코드 값(JSON 문자열 등 원본 그대로)
 */
public record EventEnvelope(String eventId, String eventType, String aggregateType, String key, String payload) {

    public static EventEnvelope from(ConsumerRecord<?, ?> record) {
        return new EventEnvelope(
                header(record, MessagingHeaders.X_EVENT_ID),
                header(record, MessagingHeaders.X_EVENT_TYPE),
                header(record, MessagingHeaders.X_AGGREGATE_TYPE),
                record.key() == null ? null : record.key().toString(),
                record.value() == null ? null : record.value().toString());
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
