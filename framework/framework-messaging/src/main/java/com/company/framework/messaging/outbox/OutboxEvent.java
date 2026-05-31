package com.company.framework.messaging.outbox;

/**
 * Outbox 행의 릴레이 읽기 모델(발행에 필요한 컬럼만).
 *
 * @param id 내부 PK(BIGSERIAL)
 * @param eventId 이벤트 고유 ID(UUID 문자열). Kafka 헤더로 실려 소비자 멱등 처리 키로 쓰인다.
 * @param aggregateType 도메인 종류(예: "Order", "AuditEvent")
 * @param aggregateId 도메인 식별자(메시지 키 미지정 시 파티션 키 후보)
 * @param eventType 이벤트 종류(예: "OrderCreated")
 * @param topic 발행 대상 Kafka 토픽
 * @param messageKey 파티션 키(널이면 aggregateId 사용)
 * @param payload 직렬화된 JSON 본문
 * @param headers 추가 헤더 JSON(널 허용)
 * @param attempts 현재까지 발행 시도 횟수
 */
public record OutboxEvent(
        long id,
        String eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String messageKey,
        String payload,
        String headers,
        int attempts) {

    /** Kafka 파티션 키: 명시 키가 없으면 aggregateId 로 폴백(같은 애그리거트 순서 보장). */
    public String partitionKey() {
        return (messageKey != null && !messageKey.isBlank()) ? messageKey : aggregateId;
    }
}
