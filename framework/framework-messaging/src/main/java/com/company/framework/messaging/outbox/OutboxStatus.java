package com.company.framework.messaging.outbox;

/**
 * Outbox 행 상태.
 *
 * <ul>
 *   <li>{@link #PENDING} — 적재 완료, Kafka 발행 대기. 릴레이가 집어가 발행한다.
 *   <li>{@link #PUBLISHED} — Kafka 발행 성공. (보존정책에 따라 추후 정리 대상)
 *   <li>{@link #FAILED} — 최대 재시도 초과. 운영자 확인/수동 재처리 대상.
 * </ul>
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
