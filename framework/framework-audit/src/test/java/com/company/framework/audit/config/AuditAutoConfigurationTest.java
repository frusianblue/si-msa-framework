package com.company.framework.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.audit.query.AuditQueryService;
import com.company.framework.audit.sink.AuditEventSink;
import com.company.framework.audit.sink.LoggingAuditEventSink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 감사 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@code framework.audit.enabled=true} (store.type 미지정) → 기본 싱크 {@link LoggingAuditEventSink}
 *       (인프라 0). jdbc/kafka 싱크와 조회 API 는 store.type 조건 미충족으로 비활성.
 *   <li>기본(미설정/false) → 어떤 감사 빈도 만들지 않음.
 * </ul>
 *
 * <p>오토컨피그의 {@code @Bean} 시그니처가 {@code JdbcTemplate}(jdbc)/{@code OutboxEventPublisher}(messaging)/
 * {@code AuditController}(web) 타입을 참조하므로(introspection), test 에 jdbc/web/messaging 3종을 재선언했다
 * (main 은 모두 {@code compileOnly}/{@code compileOnly project}). 클래스 존재만 필요하다.
 */
class AuditAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class));

    @Test
    @DisplayName("enabled=true (store 미지정) → 기본 logging 싱크 등록, jdbc 조회 API 비활성")
    void registersLoggingSinkWhenEnabled() {
        runner.withPropertyValues("framework.audit.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuditEventSink.class);
            assertThat(context.getBean(AuditEventSink.class)).isInstanceOf(LoggingAuditEventSink.class);
            assertThat(context).doesNotHaveBean(AuditQueryService.class);
        });
    }

    @Test
    @DisplayName("기본(비활성) → 어떤 감사 빈도 만들지 않음")
    void backsOffWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AuditEventSink.class);
        });
    }
}
