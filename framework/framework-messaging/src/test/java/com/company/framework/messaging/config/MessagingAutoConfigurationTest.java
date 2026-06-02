package com.company.framework.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.company.framework.idempotency.store.IdempotencyStore;
import com.company.framework.messaging.consumer.IdempotentEventProcessor;
import com.company.framework.messaging.outbox.OutboxEventPublisher;
import com.company.framework.messaging.outbox.OutboxRelay;
import com.company.framework.messaging.outbox.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 메시징 오토컨피그 로딩/토글 스모크(발행측·소비측 독립 검증).
 *
 * <ul>
 *   <li>발행({@link MessagingAutoConfiguration}): {@code framework.messaging.enabled=true} → {@link OutboxRepository}/
 *       {@link OutboxEventPublisher}(DB 적재만). 릴레이({@link OutboxRelay})는 relay 토글 미설정으로 비활성.
 *       발행자는 {@link JdbcTemplate}(mock) 과 core 가 노출하는 Jackson 3 {@link JsonMapper}(직접 제공)를 요구.
 *   <li>소비({@link MessagingConsumerAutoConfiguration}): {@code consumer.enabled=true} + {@link IdempotencyStore}(mock)
 *       빈 존재 → {@link IdempotentEventProcessor}.
 * </ul>
 */
class MessagingAutoConfigurationTest {

    // ===== 발행측 =====

    private final ApplicationContextRunner publisherRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

    @Test
    @DisplayName("발행 enabled=true → OutboxRepository/Publisher 등록, Relay 비활성")
    void registersPublisherWhenEnabled() {
        publisherRunner.withPropertyValues("framework.messaging.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxRepository.class);
            assertThat(context).hasSingleBean(OutboxEventPublisher.class);
            assertThat(context).doesNotHaveBean(OutboxRelay.class);
        });
    }

    @Test
    @DisplayName("발행 기본(비활성) → 발행 빈 없음")
    void publisherBacksOffWhenDisabled() {
        publisherRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OutboxEventPublisher.class);
        });
    }

    // ===== 소비측 =====

    private final ApplicationContextRunner consumerRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessagingConsumerAutoConfiguration.class))
            .withBean(IdempotencyStore.class, () -> mock(IdempotencyStore.class));

    @Test
    @DisplayName("소비 consumer.enabled=true (+IdempotencyStore) → IdempotentEventProcessor 등록")
    void registersConsumerWhenEnabled() {
        consumerRunner
                .withPropertyValues("framework.messaging.consumer.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdempotentEventProcessor.class);
                });
    }

    @Test
    @DisplayName("소비 기본(비활성) → 소비 빈 없음")
    void consumerBacksOffWhenDisabled() {
        consumerRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IdempotentEventProcessor.class);
        });
    }
}
