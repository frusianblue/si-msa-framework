package com.company.framework.saga;

import java.time.Instant;

/**
 * saga 인스턴스 영속 상태의 읽기 모델.
 *
 * @param sagaId 상관관계 ID(UUID). 커맨드/리플라이 헤더 x-saga-id 로 흐른다.
 * @param sagaType 정의 이름(SagaDefinition.name)
 * @param status 전체 상태
 * @param currentStep 진행 중 단계 인덱스(RUNNING=액션 대기, COMPENSATING=보상 대기)
 * @param contextJson 단계 간 공유 컨텍스트(JSON 문자열)
 * @param deadlineAt 현재 단계 응답 기한(초과 시 복구 폴러 재구동 대상)
 */
public record SagaInstance(
        String sagaId, String sagaType, SagaStatus status, int currentStep, String contextJson, Instant deadlineAt) {}
