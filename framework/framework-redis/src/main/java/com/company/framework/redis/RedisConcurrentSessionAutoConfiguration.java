package com.company.framework.redis;

import com.company.framework.security.concurrent.ConcurrentSessionProperties;
import com.company.framework.security.concurrent.ConcurrentSessionService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@code framework.security.concurrent-session.store.type=redis} 일 때 {@link RedisConcurrentSessionService} 를 활성화.
 * (이 모듈을 의존성에 추가해야만 클래스가 존재 → {@code @ConditionalOnClass} 통과)
 *
 * <p>SecurityAutoConfiguration 의 기본 InMemory 빈은 {@code store.type=memory}(matchIfMissing) 조건이라,
 * {@code type=redis} 면 memory/jdbc 빈이 모두 생성되지 않아 이 빈이 단일 {@link ConcurrentSessionService} 로 적용된다.
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(ConcurrentSessionProperties.class)
@ConditionalOnProperty(prefix = "framework.security.concurrent-session.store", name = "type", havingValue = "redis")
public class RedisConcurrentSessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ConcurrentSessionService.class)
    public ConcurrentSessionService redisConcurrentSessionService(
            StringRedisTemplate redisTemplate, ConcurrentSessionProperties props) {
        return new RedisConcurrentSessionService(redisTemplate, props);
    }
}
