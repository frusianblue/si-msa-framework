package com.company.framework.idempotency.config;

import com.company.framework.idempotency.store.IdempotencyStore;
import com.company.framework.idempotency.store.InMemoryIdempotencyStore;
import com.company.framework.idempotency.store.JdbcIdempotencyStore;
import com.company.framework.idempotency.store.RedisIdempotencyStore;
import com.company.framework.idempotency.web.IdempotencyInterceptor;
import com.company.framework.idempotency.web.IdempotencyResponseFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 멱등성 오토컨피그.
 * 1단(모듈): @ConditionalOnClass(IdempotencyStore) — 이 모듈을 의존성에 넣어야 활성.
 * 2단(기능): framework.idempotency.enabled=true.
 * 3단(구현): store.type=memory|redis|jdbc (@ConditionalOnMissingBean 으로 프로젝트 override 허용).
 */
@AutoConfiguration
@ConditionalOnClass(IdempotencyStore.class)
@ConditionalOnProperty(prefix = "framework.idempotency", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    @ConditionalOnProperty(
            prefix = "framework.idempotency.store",
            name = "type",
            havingValue = "memory",
            matchIfMissing = true)
    public IdempotencyStore inMemoryIdempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(IdempotencyStore.class)
    @ConditionalOnProperty(prefix = "framework.idempotency.store", name = "type", havingValue = "redis")
    public IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyStore(redisTemplate);
    }

    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnMissingBean(IdempotencyStore.class)
    @ConditionalOnProperty(prefix = "framework.idempotency.store", name = "type", havingValue = "jdbc")
    public IdempotencyStore jdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
        return new JdbcIdempotencyStore(jdbcTemplate);
    }

    @Bean
    public WebMvcConfigurer idempotencyWebMvcConfigurer(IdempotencyStore store, IdempotencyProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new IdempotencyInterceptor(store, props));
            }
        };
    }

    /**
     * 재생 모드 전용. 응답 캡처를 위해 (헤더가 있는 요청의) 응답을 ContentCachingResponseWrapper 로 감싼다.
     * secure-web 와 동일하게 평범한 @Bean 으로 등록(Boot 자동 등록) + 필터 클래스의 @Order(LOWEST_PRECEDENCE)로 정렬.
     */
    @Bean
    @ConditionalOnClass(ContentCachingResponseWrapper.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "framework.idempotency.replay", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public IdempotencyResponseFilter idempotencyResponseFilter(IdempotencyProperties props) {
        return new IdempotencyResponseFilter(props);
    }
}
