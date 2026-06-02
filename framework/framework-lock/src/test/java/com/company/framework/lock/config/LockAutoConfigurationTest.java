package com.company.framework.lock.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.company.framework.lock.DistributedLock;
import com.company.framework.lock.aspect.SchedulerLockAspect;
import com.company.framework.lock.support.InMemoryDistributedLock;
import com.company.framework.lock.support.JdbcDistributedLock;
import com.company.framework.lock.support.RedisDistributedLock;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 분산 락 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@code framework.lock.enabled} 미지정 → 어떤 {@link DistributedLock} 도 미등록(선택형 기본 off).
 *   <li>enabled=true + type 미지정/memory → {@link InMemoryDistributedLock} + {@link SchedulerLockAspect}.
 *   <li>enabled=true + type=redis(+StringRedisTemplate mock) → {@link RedisDistributedLock}.
 *   <li>enabled=true + type=jdbc(+JdbcTemplate mock) → {@link JdbcDistributedLock}.
 * </ul>
 */
class LockAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(LockAutoConfiguration.class));

    @Test
    @DisplayName("enabled 미지정 → DistributedLock 미등록(기본 off)")
    void backsOffByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DistributedLock.class);
        });
    }

    @Test
    @DisplayName("enabled=true + type 미지정 → InMemory + 스케줄러 애스펙트")
    void registersInMemoryByDefaultWhenEnabled() {
        runner.withPropertyValues("framework.lock.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DistributedLock.class);
            assertThat(context.getBean(DistributedLock.class)).isInstanceOf(InMemoryDistributedLock.class);
            assertThat(context).hasSingleBean(SchedulerLockAspect.class);
        });
    }

    @Test
    @DisplayName("type=redis + StringRedisTemplate → RedisDistributedLock")
    void registersRedisWhenTyped() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues("framework.lock.enabled=true", "framework.lock.type=redis")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context.getBean(DistributedLock.class)).isInstanceOf(RedisDistributedLock.class);
                });
    }

    @Test
    @DisplayName("type=jdbc + JdbcTemplate → JdbcDistributedLock")
    void registersJdbcWhenTyped() {
        runner.withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withPropertyValues("framework.lock.enabled=true", "framework.lock.type=jdbc")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context.getBean(DistributedLock.class)).isInstanceOf(JdbcDistributedLock.class);
                });
    }

    @Test
    @DisplayName("scheduler.enabled=false → 애스펙트 미등록(락 빈은 유지)")
    void schedulerAspectCanBeDisabled() {
        runner.withPropertyValues("framework.lock.enabled=true", "framework.lock.scheduler.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context).doesNotHaveBean(SchedulerLockAspect.class);
                });
    }

    /**
     * 레지스트레이션 가드: 위 토글 스모크는 클래스를 직접 로드({@code AutoConfigurations.of})하므로 {@code .imports}
     * 미등록이어도 통과한다(과거 redis 갭이 그렇게 숨었다). 본 테스트는 클래스패스의 모든 {@code AutoConfiguration.imports}
     * 를 직접 읽어 {@link LockAutoConfiguration} 이 실제로 등록돼(=자동활성 경로 존재) 있음을 보장한다.
     */
    @Test
    @DisplayName("LockAutoConfiguration 이 AutoConfiguration.imports 에 등록돼 있다")
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
                .as(".imports 에 LockAutoConfiguration 이 등록돼야 자동활성된다")
                .contains(LockAutoConfiguration.class.getName());
    }
}
