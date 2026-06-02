package com.company.framework.saga;
/** 개별 단계(액션/보상) 처리 상태. */
public enum SagaStepStatus {
    PENDING,
    DONE,
    FAILED
}
