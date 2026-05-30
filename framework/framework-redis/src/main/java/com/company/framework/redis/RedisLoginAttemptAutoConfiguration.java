package com.company.framework.redis;

import com.company.framework.security.loginattempt.LoginAttemptProperties;
import com.company.framework.security.loginattempt.LoginAttemptService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * framework.security.login-attempt.type=redis 일 때 RedisLoginAttemptService 를 활성화.
 * (이 모듈을 의존성에 추가해야만 클래스가 존재 → @ConditionalOnClass 통과)
 *
 * <p>SecurityAutoConfiguration 의 기본 InMemory 빈은 type=memory(matchIfMissing) 조건이라,
 * type=redis 면 기본 빈이 생성되지 않아 이 빈이 단일 LoginAttemptService 로 적용된다.
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(LoginAttemptProperties.class)
@ConditionalOnProperty(prefix = "framework.security.login-attempt", name = "type", havingValue = "redis")
public class RedisLoginAttemptAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LoginAttemptService.class)
    public LoginAttemptService redisLoginAttemptService(
            StringRedisTemplate redisTemplate, LoginAttemptProperties props) {
        return new RedisLoginAttemptService(redisTemplate, props);
    }
}
