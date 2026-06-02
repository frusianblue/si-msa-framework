package com.company.framework.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.company.framework.security.loginattempt.LoginAttemptService;
import com.company.framework.security.token.TokenStore;
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
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@link RedisTokenStoreAutoConfiguration}: {@code framework.security.token-store.type=redis} +
 *       {@link StringRedisTemplate}(mock) → {@link TokenStore}(RedisTokenStore). type 미지정 → 미등록.
 *   <li>{@link RedisLoginAttemptAutoConfiguration}: {@code framework.security.login-attempt.type=redis} +
 *       {@link StringRedisTemplate}(mock) → {@link LoginAttemptService}(Redis 구현).
 * </ul>
 *
 * <p>참고: {@code AutoConfiguration.imports} 에 {@link RedisTokenStoreAutoConfiguration} 과
 * {@link RedisLoginAttemptAutoConfiguration} 이 모두 등록돼 있어 {@code type=redis} 설정만으로 자동활성된다.
 * 본 테스트는 두 클래스를 직접 로드해 토글 조건과 빈 등록을 검증한다.
 */
class RedisAutoConfigurationTest {

    private final ApplicationContextRunner tokenStoreRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisTokenStoreAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    @DisplayName("token-store.type=redis → RedisTokenStore 등록")
    void registersTokenStoreWhenRedis() {
        tokenStoreRunner
                .withPropertyValues("framework.security.token-store.type=redis")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TokenStore.class);
                    assertThat(context.getBean(TokenStore.class)).isInstanceOf(RedisTokenStore.class);
                });
    }

    @Test
    @DisplayName("token-store.type 미지정 → TokenStore 미등록")
    void tokenStoreBacksOffByDefault() {
        tokenStoreRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TokenStore.class);
        });
    }

    private final ApplicationContextRunner loginAttemptRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisLoginAttemptAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    @DisplayName("login-attempt.type=redis → RedisLoginAttemptService 등록")
    void registersLoginAttemptWhenRedis() {
        loginAttemptRunner
                .withPropertyValues("framework.security.login-attempt.type=redis")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LoginAttemptService.class);
                    assertThat(context.getBean(LoginAttemptService.class)).isInstanceOf(RedisLoginAttemptService.class);
                });
    }

    @Test
    @DisplayName("login-attempt.type 미지정 → LoginAttemptService 미등록")
    void loginAttemptBacksOffByDefault() {
        loginAttemptRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LoginAttemptService.class);
        });
    }

    /**
     * 레지스트레이션 가드: 위 토글 스모크는 클래스를 직접 로드({@code AutoConfigurations.of})하므로
     * {@code .imports} 미등록이어도 통과한다 — 과거 RedisLoginAttemptAutoConfiguration 갭이 그렇게 숨었다.
     * 본 테스트는 클래스패스의 모든 {@code AutoConfiguration.imports} 를 직접 읽어 두 오토컨피그가
     * 실제로 등록돼(=자동활성 경로 존재) 있음을 보장한다.
     */
    @Test
    @DisplayName("두 Redis 오토컨피그가 AutoConfiguration.imports 에 등록돼 있다")
    void bothRedisAutoConfigurationsAreRegisteredInImports() throws Exception {
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
                .as(".imports 에 redis 오토컨피그 2종이 모두 등록돼야 자동활성된다")
                .contains(
                        RedisTokenStoreAutoConfiguration.class.getName(),
                        RedisLoginAttemptAutoConfiguration.class.getName());
    }
}
