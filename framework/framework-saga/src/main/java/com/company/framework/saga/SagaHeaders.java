package com.company.framework.saga;

/**
 * saga 상관관계 헤더 이름. Outbox 릴레이가 커스텀 헤더를 단일 {@code x-headers} JSON 으로 싣고
 * 소비자가 파싱하므로, 이 키들은 그 JSON 안에 담긴다(발행/소비 양측 단일 소스).
 */
public final class SagaHeaders {

    private SagaHeaders() {}

    public static final String X_SAGA_ID = "x-saga-id";
    public static final String X_SAGA_STEP = "x-saga-step";
    public static final String X_SAGA_PHASE = "x-saga-phase"; // ACTION | COMPENSATION
    public static final String X_SAGA_OUTCOME = "x-saga-outcome"; // SUCCESS | FAILURE
    public static final String X_SAGA_REPLY_TOPIC = "x-saga-reply-topic";
}
