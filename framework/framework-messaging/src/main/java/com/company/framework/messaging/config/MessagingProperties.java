package com.company.framework.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 신뢰성 메시지 발행(Outbox) 모듈 토글.
 *
 * <pre>{@code
 * framework:
 *   messaging:
 *     enabled: false                  # 2단 토글(선택형 → 명시적 on 필요). 켜면 OutboxEventPublisher 사용 가능.
 *     outbox:
 *       table: outbox_event           # 적재 테이블명(스키마는 db/messaging/outbox-postgres.sql 참고)
 *       relay:
 *         enabled: false              # 릴레이(발행 워커) on/off. 보통 특정 인스턴스군에서만 켠다. PostgreSQL 전제(SKIP LOCKED).
 *         poll-interval-ms: 1000
 *         batch-size: 100
 *         max-attempts: 10            # 초과 시 FAILED(운영자 확인 대상)
 *         send-timeout-ms: 5000
 *     kafka:
 *       bootstrap-servers: localhost:9092
 *       acks: all                     # 유실 방지(권장 all)
 *       enable-idempotence: true      # 프로듀서 중복 방지
 *       retries: 3
 * }</pre>
 */
@ConfigurationProperties(prefix = "framework.messaging")
public class MessagingProperties {

    /** 2단 토글. 선택형 → 기본 off. */
    private boolean enabled = false;

    private final Outbox outbox = new Outbox();
    private final Kafka kafka = new Kafka();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public static class Outbox {
        private String table = "outbox_event";
        private final Relay relay = new Relay();

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public Relay getRelay() {
            return relay;
        }

        public static class Relay {
            private boolean enabled = false;
            private long pollIntervalMs = 1000L;
            private int batchSize = 100;
            private int maxAttempts = 10;
            private long sendTimeoutMs = 5000L;

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

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }

            public long getSendTimeoutMs() {
                return sendTimeoutMs;
            }

            public void setSendTimeoutMs(long sendTimeoutMs) {
                this.sendTimeoutMs = sendTimeoutMs;
            }
        }
    }

    public static class Kafka {
        private String bootstrapServers = "localhost:9092";
        private String acks = "all";
        private boolean enableIdempotence = true;
        private int retries = 3;

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getAcks() {
            return acks;
        }

        public void setAcks(String acks) {
            this.acks = acks;
        }

        public boolean isEnableIdempotence() {
            return enableIdempotence;
        }

        public void setEnableIdempotence(boolean enableIdempotence) {
            this.enableIdempotence = enableIdempotence;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }
    }
}
