package com.company.framework.saga;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 오케스트레이션 saga 코디네이터. 단계 정의({@link SagaRegistry})에 따라 정방향 커맨드를 발행하고,
 * 참여 서비스의 리플라이로 전진/완료하며, 실패 시 완료된 단계를 역순으로 보상한다.
 *
 * <p>상태({@link SagaStore})와 커맨드 발행({@link SagaCommandPublisher}=Outbox)은 {@link SagaTransactionRunner}
 * 로 한 트랜잭션에 묶여 원자적이다(상태만 바뀌고 커맨드가 안 나가거나 그 반대가 없음).
 *
 * <p>멱등: 같은 단계의 리플라이가 중복 도착하면 단계 상태가 이미 PENDING 이 아니므로 무시된다. 따라서
 * 참여 서비스는 {@code (x-saga-id, x-saga-step)} 기준으로 커맨드를 멱등 처리해야 한다(복구 재구동 시 재배달 가능).
 */
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SagaRegistry registry;
    private final SagaStore store;
    private final SagaCommandPublisher publisher;
    private final SagaTransactionRunner tx;
    private final String replyTopic;
    private final Duration stepTimeout;

    public SagaOrchestrator(
            SagaRegistry registry,
            SagaStore store,
            SagaCommandPublisher publisher,
            SagaTransactionRunner tx,
            String replyTopic,
            Duration stepTimeout) {
        this.registry = registry;
        this.store = store;
        this.publisher = publisher;
        this.tx = tx;
        this.replyTopic = replyTopic;
        this.stepTimeout = stepTimeout;
    }

    /** 새 saga 시작 후 첫 단계 커맨드를 발행한다. @return 생성된 sagaId. */
    public String start(String sagaType, String contextJson) {
        SagaDefinition def = registry.get(sagaType);
        String sagaId = UUID.randomUUID().toString();
        tx.runWithoutResult(() -> {
            store.createInstance(sagaId, sagaType, contextJson, deadline());
            dispatchAction(def, sagaId, 0, contextJson);
        });
        log.info("saga 시작 type={} id={}", sagaType, sagaId);
        return sagaId;
    }

    /** 참여 서비스 리플라이 처리(앱의 리스너 → SagaReplyConsumer 가 호출). */
    public void onReply(
            String sagaId,
            int stepIndex,
            SagaPhase phase,
            SagaOutcome outcome,
            String replyEventId,
            String replyContextJson) {
        tx.runWithoutResult(() -> handleReply(sagaId, stepIndex, phase, outcome, replyEventId, replyContextJson));
    }

    private void handleReply(
            String sagaId,
            int stepIndex,
            SagaPhase phase,
            SagaOutcome outcome,
            String replyEventId,
            String replyContextJson) {
        Optional<SagaInstance> opt = store.find(sagaId);
        if (opt.isEmpty()) {
            log.debug("알 수 없는 saga 리플라이 무시 id={}", sagaId);
            return;
        }
        SagaInstance inst = opt.get();
        if (isTerminal(inst.status())) {
            log.debug("종료된 saga 리플라이 무시 id={} status={}", sagaId, inst.status());
            return;
        }
        Optional<SagaStepRecord> step = store.findStep(sagaId, stepIndex, phase);
        if (step.isEmpty() || step.get().status() != SagaStepStatus.PENDING) {
            log.debug("중복/비대상 단계 리플라이 무시 id={} step={} phase={}", sagaId, stepIndex, phase);
            return; // 멱등
        }
        SagaDefinition def = registry.get(inst.sagaType());
        if (phase == SagaPhase.ACTION) {
            handleActionReply(def, sagaId, stepIndex, outcome, replyEventId, replyContextJson);
        } else {
            handleCompensationReply(def, sagaId, stepIndex, outcome, replyEventId);
        }
    }

    private void handleActionReply(
            SagaDefinition def,
            String sagaId,
            int stepIndex,
            SagaOutcome outcome,
            String replyEventId,
            String replyContextJson) {
        if (outcome == SagaOutcome.SUCCESS) {
            store.markStep(sagaId, stepIndex, SagaPhase.ACTION, SagaStepStatus.DONE, replyEventId);
            if (replyContextJson != null && !replyContextJson.isBlank()) {
                store.updateContext(sagaId, replyContextJson);
            }
            int next = SagaPlanner.nextActionIndex(stepIndex, def.size());
            if (next >= 0) {
                store.updateCurrentStep(sagaId, next);
                store.touchDeadline(sagaId, deadline());
                dispatchAction(def, sagaId, next, currentContext(sagaId));
            } else {
                store.updateStatus(sagaId, SagaStatus.COMPLETED);
                log.info("saga 완료 id={}", sagaId);
            }
        } else {
            store.markStep(sagaId, stepIndex, SagaPhase.ACTION, SagaStepStatus.FAILED, replyEventId);
            store.updateStatus(sagaId, SagaStatus.COMPENSATING);
            log.warn("saga 단계 실패 → 보상 시작 id={} failedStep={}", sagaId, stepIndex);
            beginCompensation(def, sagaId, stepIndex);
        }
    }

    private void handleCompensationReply(
            SagaDefinition def, String sagaId, int stepIndex, SagaOutcome outcome, String replyEventId) {
        if (outcome == SagaOutcome.SUCCESS) {
            store.markStep(sagaId, stepIndex, SagaPhase.COMPENSATION, SagaStepStatus.DONE, replyEventId);
            continueCompensation(def, sagaId, stepIndex);
        } else {
            // 보상 실패는 자동 회복 불가 → 운영자 개입(FAILED).
            store.markStep(sagaId, stepIndex, SagaPhase.COMPENSATION, SagaStepStatus.FAILED, replyEventId);
            store.updateStatus(sagaId, SagaStatus.FAILED);
            log.error("saga 보상 실패(운영자 개입 필요) id={} step={}", sagaId, stepIndex);
        }
    }

    /** failedExclusive 미만의 완료 단계부터 역순 보상 착수. 대상 없으면 즉시 COMPENSATED. */
    private void beginCompensation(SagaDefinition def, String sagaId, int failedExclusive) {
        int target = SagaPlanner.compensationTargetBelow(def, i -> store.isActionDone(sagaId, i), failedExclusive);
        if (target < 0) {
            store.updateStatus(sagaId, SagaStatus.COMPENSATED);
            log.info("보상 대상 없음 → COMPENSATED id={}", sagaId);
            return;
        }
        store.updateCurrentStep(sagaId, target);
        store.touchDeadline(sagaId, deadline());
        dispatchCompensation(def, sagaId, target);
    }

    private void continueCompensation(SagaDefinition def, String sagaId, int compensatedIndex) {
        int target = SagaPlanner.compensationTargetBelow(def, i -> store.isActionDone(sagaId, i), compensatedIndex);
        if (target < 0) {
            store.updateStatus(sagaId, SagaStatus.COMPENSATED);
            log.info("saga 보상 완료 id={}", sagaId);
            return;
        }
        store.updateCurrentStep(sagaId, target);
        store.touchDeadline(sagaId, deadline());
        dispatchCompensation(def, sagaId, target);
    }

    private void dispatchAction(SagaDefinition def, String sagaId, int index, String contextJson) {
        SagaStep s = def.step(index);
        String eventId = publisher.publish(
                s.commandTopic(), s.commandType(), sagaId, index, SagaPhase.ACTION, replyTopic, contextJson);
        store.upsertStepPending(sagaId, index, s.name(), SagaPhase.ACTION, eventId);
    }

    private void dispatchCompensation(SagaDefinition def, String sagaId, int index) {
        SagaStep s = def.step(index);
        String eventId = publisher.publish(
                s.compensationTopic(),
                s.compensationType(),
                sagaId,
                index,
                SagaPhase.COMPENSATION,
                replyTopic,
                currentContext(sagaId));
        store.upsertStepPending(sagaId, index, s.name(), SagaPhase.COMPENSATION, eventId);
    }

    /** 복구 폴러 전용: 트랜잭션 안(호출자 제공)에서 현재 단계 커맨드를 재발행한다. */
    public void redriveWithinTx(SagaInstance inst) {
        SagaDefinition def = registry.get(inst.sagaType());
        int idx = inst.currentStep();
        store.touchDeadline(inst.sagaId(), deadline());
        if (inst.status() == SagaStatus.RUNNING) {
            dispatchAction(def, inst.sagaId(), idx, inst.contextJson());
        } else if (inst.status() == SagaStatus.COMPENSATING) {
            dispatchCompensation(def, inst.sagaId(), idx);
        }
        log.info("saga 재구동 id={} step={} status={}", inst.sagaId(), idx, inst.status());
    }

    /** 복구 폴러 전용: deadline 지난 진행 중 인스턴스 목록. */
    public List<SagaInstance> findStuck(int limit) {
        return store.findStuck(Instant.now(), limit);
    }

    private String currentContext(String sagaId) {
        return store.find(sagaId).map(SagaInstance::contextJson).orElse(null);
    }

    private Instant deadline() {
        return Instant.now().plus(stepTimeout);
    }

    private static boolean isTerminal(SagaStatus s) {
        return s == SagaStatus.COMPLETED || s == SagaStatus.COMPENSATED || s == SagaStatus.FAILED;
    }
}
