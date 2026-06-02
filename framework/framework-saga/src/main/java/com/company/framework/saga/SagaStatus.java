package com.company.framework.saga;
/** saga 인스턴스 전체 상태. */
public enum SagaStatus {
    RUNNING,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
