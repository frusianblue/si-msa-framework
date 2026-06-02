package com.company.framework.secureweb.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.secureweb.csrf.DoubleSubmitCsrfFilter;
import com.company.framework.secureweb.headers.SecurityHeadersFilter;
import com.company.framework.secureweb.injection.InjectionScreeningFilter;
import com.company.framework.secureweb.path.PathTraversalFilter;
import com.company.framework.secureweb.ratelimit.RateLimitFilter;
import com.company.framework.secureweb.support.SecureWebResponder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * 웹 보안 필터 오토컨피그 로딩/토글 스모크.
 *
 * <p>{@code @ConditionalOnWebApplication(SERVLET)} 이라 서블릿 컨텍스트에서만 활성 → {@link WebApplicationContextRunner}.
 * {@code spring-boot-starter-web}({@code OncePerRequestFilter}/{@code CorsFilter}/{@code FilterRegistrationBean})은
 * main 에서 {@code compileOnly} 라 test 에 재선언했다.
 *
 * <ul>
 *   <li>{@code enabled=true} (필터 세부토글 기본) → SecureWebResponder + 보안헤더/경로조작(기본 ON).
 *       인젝션/CSRF/rate-limit(기본 OFF)은 미등록.
 *   <li>세부토글 전부 ON → 인젝션/CSRF/rate-limit 필터도 등록.
 *   <li>기본(비활성) → SecureWebResponder 조차 없음.
 * </ul>
 */
class SecureWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecureWebAutoConfiguration.class));

    @Test
    @DisplayName("enabled=true (기본 세부토글) → 헤더/경로조작 필터 ON, 인젝션/CSRF/rate-limit OFF")
    void registersDefaultFiltersWhenEnabled() {
        runner.withPropertyValues("framework.secure-web.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecureWebResponder.class);
            assertThat(context).hasSingleBean(SecurityHeadersFilter.class);
            assertThat(context).hasSingleBean(PathTraversalFilter.class);
            assertThat(context).doesNotHaveBean(InjectionScreeningFilter.class);
            assertThat(context).doesNotHaveBean(DoubleSubmitCsrfFilter.class);
            assertThat(context).doesNotHaveBean(RateLimitFilter.class);
        });
    }

    @Test
    @DisplayName("세부토글 전부 ON → 인젝션/CSRF/rate-limit 필터까지 등록")
    void registersAllFiltersWhenAllToggledOn() {
        runner.withPropertyValues(
                        "framework.secure-web.enabled=true",
                        "framework.secure-web.injection.enabled=true",
                        "framework.secure-web.csrf.enabled=true",
                        "framework.secure-web.rate-limit.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(InjectionScreeningFilter.class);
                    assertThat(context).hasSingleBean(DoubleSubmitCsrfFilter.class);
                    assertThat(context).hasSingleBean(RateLimitFilter.class);
                });
    }

    @Test
    @DisplayName("기본(비활성) → 어떤 보안필터 빈도 만들지 않음")
    void backsOffWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SecureWebResponder.class);
            assertThat(context).doesNotHaveBean(SecurityHeadersFilter.class);
            assertThat(context).doesNotHaveBean(PathTraversalFilter.class);
        });
    }
}
