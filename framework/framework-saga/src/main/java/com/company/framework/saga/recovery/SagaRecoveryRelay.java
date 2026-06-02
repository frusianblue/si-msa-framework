package com.company.framework.saga.recovery;

import com.company.framework.saga.SagaInstance;
import com.company.framework.saga.SagaOrchestrator;
import com.company.framework.saga.config.SagaProperties;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스턱/재기동 복구 폴러. deadline 이 지난 진행 중(RUNNING/COMPENSATING) saga 의 현재 단계 커맨드를 재발행해
 * 응답 유실/인스턴스 재기동에도 saga 가 멈추지 않게 한다(at-least-once 재구동).
 *
 * <p>OutboxRelay 와 동일하게 전역 {@code @EnableScheduling} 에 의존하지 않고 자체 단일 스레드
 * {@link ScheduledExecutorService} 를 {@link SmartLifecycle} 로 기동/종료한다. 한 폴링은 단일 트랜잭션에서
 * {@code FOR UPDATE SKIP LOCKED} 로 스턱 인스턴스를 잠가-가져와 각 건을 재구동한다(건별 예외는 흡수).
 *
 * <p>PostgreSQL 전제(SKIP LOCKED). 재구동은 같은 (saga-id, step) 으로 커맨드를 재배달하므로 참여 서비스는
 * 그 키 기준 멱등이어야 한다.
 */
public class SagaRecoveryRelay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryRelay.class);

    private final SagaOrchestrator orchestrator;
    private final TransactionTemplate transactionTemplate;
    private final SagaProperties.Recovery recovery;

    private volatile ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public SagaRecoveryRelay(
            SagaOrchestrator orchestrator,
            PlatformTransactionManager transactionManager,
            SagaProperties.Recovery recovery) {
        this.orchestrator = orchestrator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setReadOnly(false);
        this.recovery = recovery;
    }

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "saga-recovery");
            t.setDaemon(true);
            return t;
        });
        long interval = recovery.getPollIntervalMs();
        scheduler.scheduleWithFixedDelay(this::pollSafely, interval, interval, TimeUnit.MILLISECONDS);
        running = true;
        log.info("saga 복구 폴러 시작: pollIntervalMs={} batchSize={}", interval, recovery.getBatchSize());
    }

    @Override
    public void stop() {
        running = false;
        ScheduledExecutorService s = this.scheduler;
        if (s != null) {
            s.shutdownNow();
        }
        log.info("saga 복구 폴러 종료");
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void pollSafely() {
        try {
            transactionTemplate.executeWithoutResult(status -> recoverBatch());
        } catch (RuntimeException ex) {
            log.warn("saga 복구 폴링 사이클 실패(다음 주기 재시도)", ex);
        }
    }

    private void recoverBatch() {
        List<SagaInstance> stuck = orchestrator.findStuck(recovery.getBatchSize());
        for (SagaInstance inst : stuck) {
            try {
                orchestrator.redriveWithinTx(inst);
            } catch (RuntimeException ex) {
                log.warn("saga 재구동 실패 id={}(다음 주기 재시도)", inst.sagaId(), ex);
            }
        }
    }
}
