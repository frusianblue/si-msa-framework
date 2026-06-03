package com.company.framework.context.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.context.RequestContext;
import com.company.framework.context.async.ContextTaskDecorator;
import com.company.framework.context.client.ContextPropagationInterceptor;
import com.company.framework.context.web.ContextBindingFilter;
import com.company.framework.context.web.ContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("ContextAutoConfiguration 자동설정")
class ContextAutoConfigurationTest {

    private final WebApplicationContextRunner runner =
            new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(ContextAutoConfiguration.class));

    @Test
    @DisplayName("enabled 미지정 → 빈 미등록(기본 off)")
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ContextResolver.class);
            assertThat(context).doesNotHaveBean(ContextBindingFilter.class);
            assertThat(context).doesNotHaveBean(ContextTaskDecorator.class);
            assertThat(context).doesNotHaveBean(ContextPropagationInterceptor.class);
        });
    }

    @Test
    @DisplayName("enabled=true → 리졸버/필터/데코레이터/인터셉터 등록")
    void enabledRegistersBeans() {
        runner.withPropertyValues("framework.context.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ContextResolver.class);
            assertThat(context).hasSingleBean(ContextBindingFilter.class);
            assertThat(context).hasSingleBean(ContextTaskDecorator.class);
            assertThat(context).hasSingleBean(ContextPropagationInterceptor.class);
        });
    }

    @Test
    @DisplayName("propagate-downstream=false → 인터셉터만 미등록")
    void interceptorOptOut() {
        runner.withPropertyValues("framework.context.enabled=true", "framework.context.propagate-downstream=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ContextBindingFilter.class);
                    assertThat(context).doesNotHaveBean(ContextPropagationInterceptor.class);
                });
    }

    @Test
    @DisplayName("앱이 ContextResolver 빈을 직접 정의하면 그쪽이 우선(@ConditionalOnMissingBean)")
    void appResolverWins() {
        runner.withPropertyValues("framework.context.enabled=true")
                .withUserConfiguration(CustomResolverConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ContextResolver.class);
                    assertThat(context.getBean(ContextResolver.class)).isSameAs(CustomResolverConfig.CUSTOM);
                });
    }

    @Test
    @DisplayName("ContextAutoConfiguration 이 AutoConfiguration.imports 에 등록돼 있다")
    void autoConfigurationIsRegisteredInImports() throws Exception {
        String path = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        List<String> registered = new ArrayList<>();
        Enumeration<URL> resources = getClass().getClassLoader().getResources(path);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(registered::add);
            }
        }
        assertThat(registered)
                .as(".imports 에 ContextAutoConfiguration 이 등록돼야 자동활성된다")
                .contains(ContextAutoConfiguration.class.getName());
    }

    @Configuration
    static class CustomResolverConfig {
        static final ContextResolver CUSTOM = new ContextResolver() {
            @Override
            public RequestContext resolve(HttpServletRequest request) {
                return RequestContext.builder().tenantId("from-jwt").build();
            }
        };

        @Bean
        ContextResolver contextResolver() {
            return CUSTOM;
        }
    }
}
