package com.company.framework.saga.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Saga 오케스트레이션 모듈 토글.
 *
 * <pre>{@code
 * framework:
 *   saga:
 *     enabled: false                # 2단 토글(선택형 → 명시적 on 필요)
 *     instance-table: saga_instance # 인스턴스 상태 테이블(DDL: db/saga/saga-postgres.sql)
 *     step-table: saga_step         # 단계 로그 테이블
 *     reply-topic: saga-replies     # 참여 서비스가 회신할 토픽(커맨드 헤더 x-saga-reply-topic 로 전달)
 *     step-timeout: 60s             # 단계 응답 기한(초과 시 복구 폴러 재구동 대상)
 *     recovery:
 *       enabled: false              # 스턱/재기동 복구 폴러. PostgreSQL 전제(SKIP LOCKED). 보통 특정 인스턴스군만.
 *       poll-interval-ms: 5000
 *       batch-size: 50
 * }</pre>
 */
@ConfigurationProperties(prefix = "framework.saga")
public class SagaProperties {

    private boolean enabled = false;
    private String instanceTable = "saga_instance";
    private String stepTable = "saga_step";
    private String replyTopic = "saga-replies";
    private Duration stepTimeout = Duration.ofSeconds(60);
    private final Recovery recovery = new Recovery();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInstanceTable() {
        return instanceTable;
    }

    public void setInstanceTable(String instanceTable) {
        this.instanceTable = instanceTable;
    }

    public String getStepTable() {
        return stepTable;
    }

    public void setStepTable(String stepTable) {
        this.stepTable = stepTable;
    }

    public String getReplyTopic() {
        return replyTopic;
    }

    public void setReplyTopic(String replyTopic) {
        this.replyTopic = replyTopic;
    }

    public Duration getStepTimeout() {
        return stepTimeout;
    }

    public void setStepTimeout(Duration stepTimeout) {
        this.stepTimeout = stepTimeout;
    }

    public Recovery getRecovery() {
        return recovery;
    }

    public static class Recovery {
        private boolean enabled = false;
        private long pollIntervalMs = 5000L;
        private int batchSize = 50;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
