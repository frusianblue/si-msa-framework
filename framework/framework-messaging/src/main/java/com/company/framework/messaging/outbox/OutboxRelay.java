package com.company.framework.messaging.outbox;

import com.company.framework.messaging.MessagingHeaders;
import com.company.framework.messaging.config.MessagingProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox 릴레이: PENDING 행을 주기적으로 잠가-가져와 Kafka 로 발행하고 결과를 기록한다.
 *
 * <p>스케줄링을 위해 애플리케이션의 전역 {@code @EnableScheduling} 에 의존하지 않는다 — 자체 단일 스레드
 * {@link ScheduledExecutorService} 를 {@link SmartLifecycle} 로 기동/종료한다(부수효과 격리).
 *
 * <p>한 번의 폴링은 단일 트랜잭션에서: (1) {@code FOR UPDATE SKIP LOCKED} 로 배치를 잠그고, (2) 각 건을 동기 발행하여
 * 성공은 PUBLISHED, 실패는 attempts 증가(소진 시 FAILED)로 표시한다. <b>건별 예외는 삼켜 배치 전체 롤백을 막는다</b>
 * (이미 성공한 건의 PUBLISHED 표시가 함께 롤백되지 않도록).
 *
 * <p>다중 인스턴스에서 동시에 돌아도 SKIP LOCKED 로 같은 행을 중복 발행하지 않는다(PostgreSQL 전제).
 */
public class OutboxRelay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MessagingProperties.Outbox.Relay relay;

    private volatile ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public OutboxRelay(
            OutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            TransactionTemplate transactionTemplate,
            MessagingProperties.Outbox.Relay relay) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.relay = relay;
    }

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-relay");
            t.setDaemon(true);
            return t;
        });
        long interval = relay.getPollIntervalMs();
        scheduler.scheduleWithFixedDelay(this::pollSafely, interval, interval, TimeUnit.MILLISECONDS);
        running = true;
        log.info(
                "Outbox 릴레이 시작: pollIntervalMs={} batchSize={} maxAttempts={}",
                interval,
                relay.getBatchSize(),
                relay.getMaxAttempts());
    }

    @Override
    public void stop() {
        running = false;
        ScheduledExecutorService s = this.scheduler;
        if (s != null) {
            s.shutdownNow();
        }
        log.info("Outbox 릴레이 종료");
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

    /** 스케줄러 스레드가 죽지 않도록 최상위에서 모든 예외를 흡수한다. */
    private void pollSafely() {
        try {
            transactionTemplate.executeWithoutResult(status -> publishBatch());
        } catch (RuntimeException ex) {
            log.warn("Outbox 폴링 사이클 실패(다음 주기 재시도)", ex);
        }
    }

    private void publishBatch() {
        List<OutboxEvent> batch = repository.fetchAndLockPending(relay.getBatchSize(), relay.getMaxAttempts());
        for (OutboxEvent e : batch) {
            try {
                send(e);
                repository.markPublished(e.id());
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                int next = e.attempts() + 1;
                boolean exhausted = next >= relay.getMaxAttempts();
                repository.markFailure(e.id(), next, exhausted, describe(ex));
                log.warn(
                        "Outbox 발행 실패 id={} eventId={} attempts={} exhausted={}",
                        e.id(),
                        e.eventId(),
                        next,
                        exhausted,
                        ex);
            }
        }
    }

    private void send(OutboxEvent e) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(e.topic(), e.partitionKey(), e.payload());
        record.headers().add(MessagingHeaders.X_EVENT_ID, e.eventId().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessagingHeaders.X_EVENT_TYPE, e.eventType().getBytes(StandardCharsets.UTF_8));
        record.headers()
                .add(MessagingHeaders.X_AGGREGATE_TYPE, e.aggregateType().getBytes(StandardCharsets.UTF_8));
        if (e.headers() != null) {
            record.headers().add(MessagingHeaders.X_HEADERS, e.headers().getBytes(StandardCharsets.UTF_8));
        }
        // 동기 발행: 성공(ack)을 확인한 뒤에야 PUBLISHED 로 표시 → at-least-once 보장.
        kafkaTemplate.send(record).get(relay.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    private static String describe(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
