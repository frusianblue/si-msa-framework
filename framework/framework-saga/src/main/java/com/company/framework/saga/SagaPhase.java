package com.company.framework.saga;
/** 단계 위상: 정방향 실행(ACTION) 또는 보상(COMPENSATION). */
public enum SagaPhase {
    ACTION,
    COMPENSATION
}
