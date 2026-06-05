package com.company.framework.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.session.config.FrameworkSessionAutoConfiguration;
import com.company.framework.session.config.SessionStoreSafetyGuard;
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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * framework-session 오토컨피그 로딩/토글 스모크.
 *
 * <p>실제 Redis 세션 외부화는 Spring Boot 표준 SessionAutoConfiguration 이 담당하므로, 본 모듈의 검증 대상은
 * (1) 오설정 가드 빈 등록/백오프, (2) {@code .imports} 자동활성 경로 존재이다.
 * 클래스패스 게이트({@code @ConditionalOnClass(RedisSessionRepository)})는 spring-session-data-redis(api)로 충족된다.
 */
class FrameworkSessionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FrameworkSessionAutoConfiguration.class));

    @Test
    @DisplayName("기본(enabled 미지정) → SessionStoreSafetyGuard 등록")
    void registersGuardByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SessionStoreSafetyGuard.class);
        });
    }

    @Test
    @DisplayName("framework.session.enabled=false → 미등록")
    void backsOffWhenDisabled() {
        runner.withPropertyValues("framework.session.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SessionStoreSafetyGuard.class);
        });
    }

    @Test
    @DisplayName("mode=session 이면 가드는 정상 등록(경고 없이)")
    void quietWhenSessionMode() {
        runner.withPropertyValues("framework.security.session.mode=session").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SessionStoreSafetyGuard.class);
        });
    }

    @Test
    @DisplayName("FrameworkSessionAutoConfiguration 이 AutoConfiguration.imports 에 등록돼 있다")
    void autoConfigRegisteredInImports() throws Exception {
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
        assertThat(registered).contains(FrameworkSessionAutoConfiguration.class.getName());
    }
}
