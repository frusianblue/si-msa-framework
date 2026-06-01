package com.company.framework.messaging.config;

import java.time.Duration;
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
    private final Consumer consumer = new Consumer();

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

    public Consumer getConsumer() {
        return consumer;
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

    /**
     * 소비자측 멱등 소비 설정. {@code framework.messaging.consumer.enabled=true} + framework-idempotency 의존 시 활성.
     * 발행측({@code messaging.enabled})과 독립 — 순수 소비 서비스는 consumer 만 켜면 된다.
     */
    public static class Consumer {
        /** 멱등 소비 활성(IdempotentEventProcessor 빈 제공). */
        private boolean enabled = false;
        /** 멱등 키 보관 기간(중복 판정 윈도). 재시도 주기보다 충분히 길게, 무한은 회피. */
        private Duration ttl = Duration.ofHours(24);
        /** 멱등 저장소 키 접두어. */
        private String keyPrefix = "evt:";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }
}
