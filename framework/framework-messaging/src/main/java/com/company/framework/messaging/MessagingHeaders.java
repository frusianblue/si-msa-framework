package com.company.framework.messaging;

/**
 * Outbox 릴레이가 싣고 소비자가 읽는 Kafka 헤더 이름(단일 소스). 발행/소비 양측이 이 상수를 공유해 드리프트를 막는다.
 */
public final class MessagingHeaders {

    private MessagingHeaders() {}

    /** 이벤트 고유 ID(UUID). 소비자 멱등 처리 키. */
    public static final String X_EVENT_ID = "x-event-id";
    /** 이벤트 종류(예: OrderCreated). */
    public static final String X_EVENT_TYPE = "x-event-type";
    /** 애그리거트 종류(예: Order). */
    public static final String X_AGGREGATE_TYPE = "x-aggregate-type";
    /** 추가 헤더 JSON(선택). */
    public static final String X_HEADERS = "x-headers";
}
