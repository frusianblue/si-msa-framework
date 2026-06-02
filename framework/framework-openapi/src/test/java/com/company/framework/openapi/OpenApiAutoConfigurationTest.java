package com.company.framework.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * OpenAPI 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>기본(미설정) → {@code matchIfMissing=true} 이므로 {@link OpenAPI} 빈이 등록(기본 ON 모듈).
 *   <li>{@code framework.openapi.enabled=false} → 빈을 만들지 않음.
 * </ul>
 *
 * <p>springdoc(io.swagger {@link OpenAPI})은 이 모듈의 {@code api} 의존이라 클래스패스에 존재 →
 * {@code @ConditionalOnClass(OpenAPI)} 통과. 빈은 {@code OpenApiProperties} 외 의존이 없다.
 */
class OpenApiAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(OpenApiAutoConfiguration.class));

    @Test
    @DisplayName("기본(미설정) → OpenAPI 빈 등록(기본 ON)")
    void registersByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OpenAPI.class);
        });
    }

    @Test
    @DisplayName("enabled=false → OpenAPI 빈 미등록")
    void backsOffWhenDisabled() {
        runner.withPropertyValues("framework.openapi.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OpenAPI.class);
        });
    }
}
