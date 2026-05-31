package com.company.framework.messaging.config;

import com.company.framework.messaging.outbox.OutboxEvent;
import com.company.framework.messaging.outbox.OutboxEventPublisher;
import com.company.framework.messaging.outbox.OutboxRelay;
import com.company.framework.messaging.outbox.OutboxRepository;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 신뢰성 발행(Outbox) 모듈 오토컨피그.
 *
 * <ul>
 *   <li>1단(모듈): {@code @ConditionalOnClass(OutboxEvent, KafkaTemplate, JdbcTemplate)} — 모듈+kafka+jdbc 가 있어야 활성.
 *   <li>2단(기능): {@code framework.messaging.enabled=true} → {@link OutboxEventPublisher}/{@link OutboxRepository} 제공.
 *       이 단계만으로 발행자는 동작(Kafka 연결 불필요, DB 적재만).
 *   <li>3단(릴레이): {@code framework.messaging.outbox.relay.enabled=true} → KafkaTemplate/트랜잭션템플릿/{@link OutboxRelay}
 *       제공. 발행 워커는 보통 특정 인스턴스군에서만 켠다(PostgreSQL SKIP LOCKED 전제).
 * </ul>
 *
 * <p>JSON 직렬화는 core 가 공통 설정을 입혀 노출하는 Jackson 3 {@link JsonMapper} 빈을 주입해 사용한다.
 */
@AutoConfiguration
@ConditionalOnClass({OutboxEvent.class, KafkaTemplate.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "framework.messaging", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    // ===== 2단: 발행자 (DB 적재만, Kafka 불필요) =====

    @Bean
    @ConditionalOnMissingBean
    public OutboxRepository outboxRepository(JdbcTemplate jdbcTemplate, MessagingProperties properties) {
        return new OutboxRepository(jdbcTemplate, properties.getOutbox().getTable());
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxEventPublisher outboxEventPublisher(OutboxRepository outboxRepository, JsonMapper jsonMapper) {
        return new OutboxEventPublisher(outboxRepository, jsonMapper);
    }

    // ===== 3단: 릴레이 (Kafka 발행 워커) =====

    @Bean
    @ConditionalOnProperty(prefix = "framework.messaging.outbox.relay", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "outboxKafkaTemplate")
    public KafkaTemplate<String, String> outboxKafkaTemplate(MessagingProperties properties) {
        MessagingProperties.Kafka k = properties.getKafka();
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, k.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, k.getAcks());
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, k.isEnableIdempotence());
        config.put(ProducerConfig.RETRIES_CONFIG, k.getRetries());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.messaging.outbox.relay", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "outboxTransactionTemplate")
    public TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        // 릴레이 전용: 새 트랜잭션 + 쓰기(라우팅 시 WRITE 노드 + SKIP LOCKED 잠금 유지).
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(false);
        return template;
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.messaging.outbox.relay", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> outboxKafkaTemplate,
            TransactionTemplate outboxTransactionTemplate,
            MessagingProperties properties) {
        return new OutboxRelay(
                outboxRepository,
                outboxKafkaTemplate,
                outboxTransactionTemplate,
                properties.getOutbox().getRelay());
    }
}
