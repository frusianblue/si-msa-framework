package com.company.framework.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.company.framework.security.loginattempt.LoginAttemptService;
import com.company.framework.security.token.TokenStore;
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
 * <p>참고: 현재 {@code AutoConfiguration.imports} 에는 {@link RedisTokenStoreAutoConfiguration} 만 등록돼 있고
 * {@link RedisLoginAttemptAutoConfiguration} 은 미등록 상태다. 본 테스트는 두 클래스를 직접 로드해 의도된 동작을
 * 검증·문서화한다(레지스트레이션 갭은 별도 점검 필요).
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
}
