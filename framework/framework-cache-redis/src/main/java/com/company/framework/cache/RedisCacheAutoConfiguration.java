package com.company.framework.cache;

import com.company.framework.core.cache.CacheAutoConfiguration;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 분산 캐시(Redis) 오토컨피그(3단 토글).
 *
 * <ol>
 *   <li>모듈: {@code @ConditionalOnClass(RedisConnectionFactory)} — 이 모듈 + spring-data-redis 가 있어야 활성.
 *   <li>기능: {@code framework.cache.redis.enabled=true}.
 *   <li>구현: {@link RedisCacheManager} 를 {@code CacheManager} 로 등록(@ConditionalOnMissingBean — 앱 override 허용).
 * </ol>
 *
 * <p><b>핵심: core 보다 먼저 실행한다</b>({@code @AutoConfiguration(before = CacheAutoConfiguration.class)}).
 * framework-core 의 {@link CacheAutoConfiguration} 은 Caffeine {@code CacheManager} 를
 * {@code @ConditionalOnMissingBean(CacheManager.class)} 로 등록하므로, 본 모듈이 먼저 Redis {@code CacheManager}
 * 를 올리면 core 가 자기 조건으로 물러난다(Caffeine 대신 Redis 적용). {@code @EnableCaching} 은 core 가 이미 켠다 →
 * 여기서 재선언하지 않는다.
 *
 * <p><b>직렬화</b>: 값=JDK(RedisSerializer.java()), 키=String. Jackson 2({@code GenericJackson2JsonRedisSerializer})
 * 는 본 스택(Jackson 3) 규약상 쓰지 않는다. JSON 직렬화가 필요하면 앱이 {@link RedisCacheConfiguration} 빈을
 * 직접 등록한다 → 아래 {@code cacheManager} 의 {@code cacheDefaults} 가 그 빈을 우선 사용
 * ({@code @ConditionalOnMissingBean(RedisCacheConfiguration.class)}).
 */
@AutoConfiguration(before = CacheAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "framework.cache.redis", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RedisCacheProperties.class)
public class RedisCacheAutoConfiguration {

    /**
     * 기본 캐시 설정(앱이 자체 {@link RedisCacheConfiguration} 빈을 주면 그쪽 우선). 값=JDK, 키=String,
     * TTL/접두/null 정책은 프로퍼티 반영.
     */
    @Bean
    @ConditionalOnMissingBean(RedisCacheConfiguration.class)
    public RedisCacheConfiguration redisCacheConfiguration(RedisCacheProperties props) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(props.getTimeToLive())
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.java()));

        if (!props.isCacheNullValues()) {
            config = config.disableCachingNullValues();
        }
        String prefix = props.getKeyPrefix();
        if (prefix != null && !prefix.isBlank()) {
            // 기본은 "캐시명::키". 전역 접두를 주면 "prefix캐시명::키".
            config = config.computePrefixWith(cacheName -> prefix + cacheName + "::");
        }
        return config;
    }

    /**
     * Redis {@code CacheManager}. core 의 Caffeine 매니저보다 먼저 등록되어 분산 캐시로 동작한다. 캐시별 TTL override 는
     * {@code framework.cache.redis.ttls} 로 초기 캐시 설정을 심는다(이후 동적 생성 캐시는 기본 설정).
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            RedisCacheConfiguration defaultConfig,
            RedisCacheProperties props) {
        Map<String, RedisCacheConfiguration> perCache = new LinkedHashMap<>();
        for (Map.Entry<String, Duration> e : props.getTtls().entrySet()) {
            perCache.put(e.getKey(), defaultConfig.entryTtl(e.getValue()));
        }
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCache)
                .build();
    }
}
