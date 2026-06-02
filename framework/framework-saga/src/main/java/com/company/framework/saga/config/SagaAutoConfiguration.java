package com.company.framework.saga.config;

import com.company.framework.messaging.outbox.OutboxEventPublisher;
import com.company.framework.saga.SagaCommandPublisher;
import com.company.framework.saga.SagaDefinition;
import com.company.framework.saga.SagaOrchestrator;
import com.company.framework.saga.SagaRegistry;
import com.company.framework.saga.SagaStore;
import com.company.framework.saga.SagaTransactionRunner;
import com.company.framework.saga.jdbc.JdbcSagaStore;
import com.company.framework.saga.messaging.OutboxSagaCommandPublisher;
import com.company.framework.saga.messaging.SagaReplyConsumer;
import com.company.framework.saga.recovery.SagaRecoveryRelay;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saga 오케스트레이션 모듈 오토컨피그.
 *
 * <ul>
 *   <li>1단(모듈): {@code @ConditionalOnClass(OutboxEventPublisher, JdbcTemplate)} — 커맨드 발행(Outbox)과
 *       상태 영속(JDBC)이 둘 다 있어야 활성. 즉 framework-messaging 의존이 전제.
 *   <li>2단(기능): {@code framework.saga.enabled=true} → 레지스트리/스토어/발행자/오케스트레이터 제공.
 *   <li>3단(복구): {@code framework.saga.recovery.enabled=true} → {@link SagaRecoveryRelay}(PostgreSQL SKIP LOCKED).
 * </ul>
 *
 * <p>커맨드 발행 원자성을 위해 messaging 의 {@code framework.messaging.enabled=true} 도 함께 켜야 하며,
 * 실제 Kafka 발행은 어느 인스턴스군에서든 {@code outbox.relay.enabled=true} 가 돌고 있어야 한다.
 */
@AutoConfiguration
@ConditionalOnClass({OutboxEventPublisher.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "framework.saga", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SagaProperties.class)
public class SagaAutoConfiguration {

    /** 앱이 선언한 SagaDefinition 빈들을 모아 레지스트리 구성. */
    @Bean
    @ConditionalOnMissingBean
    public SagaRegistry sagaRegistry(ObjectProvider<SagaDefinition> definitions) {
        return new SagaRegistry(definitions.stream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaStore sagaStore(JdbcTemplate jdbcTemplate, SagaProperties properties) {
        return new JdbcSagaStore(jdbcTemplate, properties.getInstanceTable(), properties.getStepTable());
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaCommandPublisher sagaCommandPublisher(OutboxEventPublisher outboxEventPublisher, JsonMapper jsonMapper) {
        return new OutboxSagaCommandPublisher(outboxEventPublisher, jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaTransactionRunner sagaTransactionRunner(PlatformTransactionManager transactionManager) {
        return new SpringSagaTransactionRunner(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaOrchestrator sagaOrchestrator(
            SagaRegistry sagaRegistry,
            SagaStore sagaStore,
            SagaCommandPublisher sagaCommandPublisher,
            SagaTransactionRunner sagaTransactionRunner,
            SagaProperties properties) {
        return new SagaOrchestrator(
                sagaRegistry,
                sagaStore,
                sagaCommandPublisher,
                sagaTransactionRunner,
                properties.getReplyTopic(),
                properties.getStepTimeout());
    }

    /** 리플라이 소비 헬퍼. spring-kafka(ConsumerRecord) 가 있을 때만 노출(앱이 @KafkaListener 에서 호출). */
    @Bean
    @ConditionalOnClass(ConsumerRecord.class)
    @ConditionalOnMissingBean
    public SagaReplyConsumer sagaReplyConsumer(SagaOrchestrator sagaOrchestrator, JsonMapper jsonMapper) {
        return new SagaReplyConsumer(sagaOrchestrator, jsonMapper);
    }

    /** 3단: 스턱/재기동 복구 폴러. */
    @Bean
    @ConditionalOnProperty(prefix = "framework.saga.recovery", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public SagaRecoveryRelay sagaRecoveryRelay(
            SagaOrchestrator sagaOrchestrator,
            PlatformTransactionManager transactionManager,
            SagaProperties properties) {
        return new SagaRecoveryRelay(sagaOrchestrator, transactionManager, properties.getRecovery());
    }
}
