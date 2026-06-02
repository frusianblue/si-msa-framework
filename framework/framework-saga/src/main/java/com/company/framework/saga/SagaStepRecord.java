package com.company.framework.saga;

/**
 * 단계 실행 로그(액션/보상 각각 1행). (saga_id, step_index, phase) 유니크.
 *
 * @param commandEventId 발행한 커맨드의 x-event-id
 * @param replyEventId 수신한 리플라이의 x-event-id(완료 시 기록)
 */
public record SagaStepRecord(
        String sagaId,
        int stepIndex,
        String stepName,
        SagaPhase phase,
        SagaStepStatus status,
        String commandEventId,
        String replyEventId) {}
