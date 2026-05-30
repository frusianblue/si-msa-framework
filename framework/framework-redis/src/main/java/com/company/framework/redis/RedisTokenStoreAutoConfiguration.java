package com.company.framework.redis;

import com.company.framework.security.token.TokenStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * framework.security.token-store.type=redis 일 때 RedisTokenStore 를 활성화.
 * (이 모듈을 의존성에 추가해야만 클래스가 존재 -> @ConditionalOnClass 통과)
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "framework.security.token-store", name = "type", havingValue = "redis")
public class RedisTokenStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenStore.class)
    public TokenStore redisTokenStore(StringRedisTemplate redisTemplate) {
        return new RedisTokenStore(redisTemplate);
    }
}
