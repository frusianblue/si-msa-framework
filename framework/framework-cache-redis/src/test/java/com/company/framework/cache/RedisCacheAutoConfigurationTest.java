package com.company.framework.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 분산 캐시(Redis) 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@code framework.cache.redis.enabled} 미지정 → CacheManager 미등록(선택형 기본 off).
 *   <li>enabled=true + RedisConnectionFactory(mock) → {@link RedisCacheManager} 가 {@code CacheManager} 로 등록.
 *   <li>앱이 {@link RedisCacheConfiguration} 빈을 주면 본 모듈 기본 설정은 백오프(@ConditionalOnMissingBean).
 * </ul>
 *
 * <p>core 의 CacheAutoConfiguration 은 슬라이스에 로드하지 않는다(본 모듈 단독 검증). 실제 앱에선 본 모듈이
 * {@code before = CacheAutoConfiguration} 로 먼저 떠 Redis 매니저가 core 의 Caffeine 을 대체한다.
 */
class RedisCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RedisCacheAutoConfiguration.class));

    @Test
    @DisplayName("enabled 미지정 → CacheManager 미등록(기본 off)")
    void backsOffByDefault() {
        runner.withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CacheManager.class);
                });
    }

    @Test
    @DisplayName("enabled=true + RedisConnectionFactory → RedisCacheManager")
    void registersRedisCacheManagerWhenEnabled() {
        runner.withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withPropertyValues("framework.cache.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class);
                    assertThat(context).hasSingleBean(RedisCacheConfiguration.class);
                });
    }

    @Test
    @DisplayName("앱이 RedisCacheConfiguration 을 제공하면 기본 설정은 백오프")
    void appProvidedConfigWins() {
        RedisCacheConfiguration custom = RedisCacheConfiguration.defaultCacheConfig();
        runner.withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean("customRedisCacheConfig", RedisCacheConfiguration.class, () -> custom)
                .withPropertyValues("framework.cache.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisCacheConfiguration.class);
                    assertThat(context.getBean(RedisCacheConfiguration.class)).isSameAs(custom);
                    assertThat(context.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class);
                });
    }

    /**
     * 레지스트레이션 가드: 위 스모크는 클래스를 직접 로드({@code AutoConfigurations.of})하므로 {@code .imports} 미등록이어도
     * 통과한다. 본 테스트는 클래스패스의 모든 {@code AutoConfiguration.imports} 를 직접 읽어 자동활성 경로가 실제로
     * 존재함을 보장한다(과거 redis 갭이 .imports 누락으로 숨었던 교훈).
     */
    @Test
    @DisplayName("RedisCacheAutoConfiguration 이 AutoConfiguration.imports 에 등록돼 있다")
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
                .as(".imports 에 RedisCacheAutoConfiguration 이 등록돼야 자동활성된다")
                .contains(RedisCacheAutoConfiguration.class.getName());
    }
}
