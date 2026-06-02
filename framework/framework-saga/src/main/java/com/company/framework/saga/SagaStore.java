package com.company.framework.saga;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** saga 상태 영속 SPI. 기본 구현은 JDBC(JdbcSagaStore). 호출자는 같은 트랜잭션 안에서 사용한다. */
public interface SagaStore {

    void createInstance(String sagaId, String sagaType, String contextJson, Instant deadlineAt);

    Optional<SagaInstance> find(String sagaId);

    void updateStatus(String sagaId, SagaStatus status);

    void updateCurrentStep(String sagaId, int currentStep);

    void updateContext(String sagaId, String contextJson);

    void touchDeadline(String sagaId, Instant deadlineAt);

    Optional<SagaStepRecord> findStep(String sagaId, int stepIndex, SagaPhase phase);

    /** (saga,step,phase) 단계 행을 PENDING 으로 적재(있으면 commandEventId 갱신). */
    void upsertStepPending(String sagaId, int stepIndex, String stepName, SagaPhase phase, String commandEventId);

    void markStep(String sagaId, int stepIndex, SagaPhase phase, SagaStepStatus status, String replyEventId);

    /** 해당 단계의 ACTION 이 DONE 인가(보상 대상 판정). */
    boolean isActionDone(String sagaId, int stepIndex);

    /** deadline 이 지난 진행 중(RUNNING/COMPENSATING) 인스턴스. 복구 폴러용(잠금은 구현체 책임). */
    List<SagaInstance> findStuck(Instant now, int limit);
}
