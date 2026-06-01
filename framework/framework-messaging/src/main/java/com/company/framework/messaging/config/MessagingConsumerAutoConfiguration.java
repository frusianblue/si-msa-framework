package com.company.framework.messaging.config;

import com.company.framework.idempotency.store.IdempotencyStore;
import com.company.framework.messaging.consumer.IdempotentEventProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 소비자측 멱등 소비 오토컨피그(발행측과 독립).
 *
 * <ul>
 *   <li>1단(모듈): {@code @ConditionalOnClass(IdempotencyStore, ConsumerRecord)} — framework-idempotency + kafka-clients 필요.
 *   <li>2단(기능): {@code framework.messaging.consumer.enabled=true}.
 *   <li>3단(저장소): {@code @ConditionalOnBean(IdempotencyStore)} — idempotency 모듈이 켜져 store 빈이 있어야 함
 *       (다중 인스턴스 컨슈머는 {@code framework.idempotency.store.type=redis} 권장 — 인메모리는 인스턴스별이라 교차 중복 미차단).
 * </ul>
 *
 * <p>발행측({@link MessagingAutoConfiguration}, {@code messaging.enabled})과 무관 — 순수 소비 서비스는 consumer 만 켠다.
 * idempotency 가 없으면(클래스/빈 부재) 본 구성은 우아하게 비활성된다.
 */
@AutoConfiguration(afterName = "com.company.framework.idempotency.config.IdempotencyAutoConfiguration")
@ConditionalOnClass({IdempotencyStore.class, ConsumerRecord.class})
@ConditionalOnProperty(prefix = "framework.messaging.consumer", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConsumerAutoConfiguration {

    @Bean
    @ConditionalOnBean(IdempotencyStore.class)
    @ConditionalOnMissingBean
    public IdempotentEventProcessor idempotentEventProcessor(
            IdempotencyStore idempotencyStore, MessagingProperties properties) {
        MessagingProperties.Consumer c = properties.getConsumer();
        return new IdempotentEventProcessor(idempotencyStore, c.getTtl(), c.getKeyPrefix());
    }
}
