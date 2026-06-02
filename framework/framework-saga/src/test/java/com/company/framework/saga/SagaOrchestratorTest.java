package com.company.framework.saga;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * 오케스트레이터 상태머신 단위 테스트. 영속/발행은 인메모리 페이크로 대체하고 SagaTransactionRunner 는 no-op.
 * 해피패스, 단일/다단계 역순 보상, 보상없는 단계 스킵, 멱등 중복, 종료/미존재 무시, 재구동을 검증한다.
 */
class SagaOrchestratorTest {

    // ---- 인메모리 스토어 ----
    static final class MemStore implements SagaStore {
        final Map<String, SagaInstance> inst = new HashMap<>();
        final Map<String, SagaStepRecord> steps = new LinkedHashMap<>();

        static String k(String s, int i, SagaPhase p) {
            return s + "|" + i + "|" + p;
        }

        public void createInstance(String id, String t, String ctx, Instant dl) {
            inst.put(id, new SagaInstance(id, t, SagaStatus.RUNNING, 0, ctx, dl));
        }

        public Optional<SagaInstance> find(String id) {
            return Optional.ofNullable(inst.get(id));
        }

        public void updateStatus(String id, SagaStatus s) {
            SagaInstance i = inst.get(id);
            inst.put(
                    id,
                    new SagaInstance(i.sagaId(), i.sagaType(), s, i.currentStep(), i.contextJson(), i.deadlineAt()));
        }

        public void updateCurrentStep(String id, int cs) {
            SagaInstance i = inst.get(id);
            inst.put(id, new SagaInstance(i.sagaId(), i.sagaType(), i.status(), cs, i.contextJson(), i.deadlineAt()));
        }

        public void updateContext(String id, String ctx) {
            SagaInstance i = inst.get(id);
            inst.put(id, new SagaInstance(i.sagaId(), i.sagaType(), i.status(), i.currentStep(), ctx, i.deadlineAt()));
        }

        public void touchDeadline(String id, Instant dl) {
            SagaInstance i = inst.get(id);
            inst.put(id, new SagaInstance(i.sagaId(), i.sagaType(), i.status(), i.currentStep(), i.contextJson(), dl));
        }

        public Optional<SagaStepRecord> findStep(String id, int i, SagaPhase p) {
            return Optional.ofNullable(steps.get(k(id, i, p)));
        }

        public void upsertStepPending(String id, int i, String name, SagaPhase p, String cmd) {
            steps.put(k(id, i, p), new SagaStepRecord(id, i, name, p, SagaStepStatus.PENDING, cmd, null));
        }

        public void markStep(String id, int i, SagaPhase p, SagaStepStatus st, String reply) {
            SagaStepRecord r = steps.get(k(id, i, p));
            steps.put(k(id, i, p), new SagaStepRecord(id, i, r.stepName(), p, st, r.commandEventId(), reply));
        }

        public boolean isActionDone(String id, int i) {
            SagaStepRecord r = steps.get(k(id, i, SagaPhase.ACTION));
            return r != null && r.status() == SagaStepStatus.DONE;
        }

        public List<SagaInstance> findStuck(Instant now, int lim) {
            List<SagaInstance> out = new ArrayList<>();
            for (SagaInstance i : inst.values()) {
                if ((i.status() == SagaStatus.RUNNING || i.status() == SagaStatus.COMPENSATING)
                        && i.deadlineAt().isBefore(now)) {
                    out.add(i);
                }
            }
            return out;
        }
    }

    static final class CapPub implements SagaCommandPublisher {
        final List<String> log = new ArrayList<>();
        int seq = 0;

        public String publish(String topic, String type, String id, int idx, SagaPhase ph, String reply, String ctx) {
            log.add(ph + ":" + idx + ":" + type);
            return "evt-" + (seq++);
        }
    }

    static final SagaTransactionRunner NOTX = new SagaTransactionRunner() {
        public <T> T run(Supplier<T> w) {
            return w.get();
        }
    };

    static SagaDefinition def3() {
        return SagaDefinition.named("OrderSaga")
                .step("reserveStock", "stock-cmd", "ReserveStock", "stock-cmd", "ReleaseStock")
                .step("processPayment", "pay-cmd", "ProcessPayment", "pay-cmd", "RefundPayment")
                .step("arrangeShipping", "ship-cmd", "ArrangeShipping") // 보상 없음
                .build();
    }

    static SagaOrchestrator orch(MemStore s, CapPub p) {
        SagaRegistry reg = new SagaRegistry(List.of(def3()));
        return new SagaOrchestrator(reg, s, p, NOTX, "saga-replies", Duration.ofSeconds(60));
    }

    @Test
    void happyPath_allSuccess_completes() {
        MemStore s = new MemStore();
        CapPub p = new CapPub();
        SagaOrchestrator o = orch(s, p);
        String id = o.start("OrderSaga", "{}");
        assertThat(p.log).containsExactly("ACTION:0:ReserveStock");
        o.onReply(id, 0, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r0", null);
        o.onReply(id, 1, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r1", null);
        o.onReply(id, 2, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r2", null);
        assertThat(s.find(id).get().status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(p.log)
                .containsExactly("ACTION:0:ReserveStock", "ACTION:1:ProcessPayment", "ACTION:2:ArrangeShipping");
    }

    @Test
    void stepFailure_compensatesCompletedStep() {
        MemStore s = new MemStore();
        CapPub p = new CapPub();
        SagaOrchestrator o = orch(s, p);
        String id = o.start("OrderSaga", "{}");
        o.onReply(id, 0, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r0", null);
        o.onReply(id, 1, SagaPhase.ACTION, SagaOutcome.FAILURE, "r1", null);
        assertThat(s.find(id).get().status()).isEqualTo(SagaStatus.COMPENSATING);
        assertThat(p.log).contains("COMPENSATION:0:ReleaseStock");
        o.onReply(id, 0, SagaPhase.COMPENSATION, SagaOutcome.SUCCESS, "c0", null);
        assertThat(s.find(id).get().status()).isEqualTo(SagaStatus.COMPENSATED);
    }

    @Test
    void lastStepFailure_compensatesInReverse_skippingNonCompensatable() {
        MemStore s = new MemStore();
        CapPub p = new CapPub();
        SagaOrchestrator o = orch(s, p);
        String id = o.start("OrderSaga", "{}");
        o.onReply(id, 0, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r0", null);
        o.onReply(id, 1, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r1", null);
        o.onReply(id, 2, SagaPhase.ACTION, SagaOutcome.FAILURE, "r2", null); // shipping 은 보상 없음
        assertThat(p.log).contains("COMPENSATION:1:RefundPayment");
        o.onReply(id, 1, SagaPhase.COMPENSATION, SagaOutcome.SUCCESS, "c1", null);
        int iRef = p.log.indexOf("COMPENSATION:1:RefundPayment");
        int iRel = p.log.indexOf("COMPENSATION:0:ReleaseStock");
        assertThat(iRef).isGreaterThanOrEqualTo(0);
        assertThat(iRel).isGreaterThan(iRef); // 역순 1 → 0
        o.onReply(id, 0, SagaPhase.COMPENSATION, SagaOutcome.SUCCESS, "c0", null);
        assertThat(s.find(id).get().status()).isEqualTo(SagaStatus.COMPENSATED);
    }

    @Test
    void duplicateActionReply_isIgnored() {
        MemStore s = new MemStore();
        CapPub p = new CapPub();
        SagaOrchestrator o = orch(s, p);
        String id = o.start("OrderSaga", "{}");
        o.onReply(id, 0, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r0", null);
        int afterFirst = p.log.size();
        o.onReply(id, 0, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r0-dup", null);
        assertThat(p.log).hasSize(afterFirst);
    }

    @Test
    void unknownAndTerminalReplies_areIgnored() {
        MemStore s = new MemStore();
        CapPub p = new CapPub();
        SagaOrchestrator o = orch(s, p);
        o.onReply("nope", 0, SagaPhase.ACTION, SagaOutcome.SUCCESS, "x", null); // 예외 없이 무시
        String id = o.start("OrderSaga", "{}");
        o.onReply(id, 0, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r0", null);
        o.onReply(id, 1, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r1", null);
        o.onReply(id, 2, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r2", null);
        int before = p.log.size();
        o.onReply(id, 2, SagaPhase.ACTION, SagaOutcome.SUCCESS, "late", null);
        assertThat(p.log).hasSize(before);
        assertThat(s.find(id).get().status()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void recovery_redrivesCurrentStepCommand() {
        MemStore s = new MemStore();
        CapPub p = new CapPub();
        SagaOrchestrator o = orch(s, p);
        String id = o.start("OrderSaga", "{}");
        o.onReply(id, 0, SagaPhase.ACTION, SagaOutcome.SUCCESS, "r0", null); // 현재 step=1 대기
        s.touchDeadline(id, Instant.now().minusSeconds(1)); // 기한 초과 강제
        List<SagaInstance> stuck = o.findStuck(10);
        assertThat(stuck).hasSize(1);
        int before = p.log.size();
        o.redriveWithinTx(stuck.get(0));
        assertThat(p.log).hasSize(before + 1);
        assertThat(p.log.get(p.log.size() - 1)).isEqualTo("ACTION:1:ProcessPayment");
    }
}
