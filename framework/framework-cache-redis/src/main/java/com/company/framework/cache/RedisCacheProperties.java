package com.company.framework.cache;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 분산 캐시(Redis) 모듈 설정.
 *
 * <pre>{@code
 * framework:
 *   cache:
 *     redis:
 *       enabled: false                 # 선택형 → 명시적 on. on 이면 core 의 Caffeine CacheManager 대신 Redis 사용
 *       time-to-live: 10m              # 기본 TTL(미설정 캐시에 적용). core Caffeine 기본(10m)과 동일
 *       key-prefix: ""                 # 전역 키 접두(비우면 "cacheName::키"; 채우면 "prefix캐시명::키")
 *       cache-null-values: true        # null 도 캐시(캐시 관통 방지). false 면 null 미저장
 *       ttls:                          # 캐시별 TTL override (캐시명 → Duration)
 *         commonCode: 1h
 *         userProfile: 5m
 * }</pre>
 *
 * <p><b>직렬화</b>: 값은 기본 <b>JDK 직렬화</b>(RedisSerializer.java())로 저장한다 — 캐시 값은 {@code Serializable}
 * 이어야 한다. 본 스택은 Jackson 3(tools.jackson.*)이라 Spring Data Redis 의 {@code GenericJackson2JsonRedisSerializer}
 * (Jackson 2)를 의도적으로 쓰지 않는다(레포 전역 규약 — RedisTokenStore/MFA 도 Jackson 미사용). JSON 직렬화가 필요하면
 * 앱이 {@code RedisCacheConfiguration} 빈을 직접 등록하면 본 모듈이 그것을 우선한다(@ConditionalOnMissingBean).
 *
 * <p>운영(k8s 다중 replica)에서 파드 간 캐시 공유/무효화 일관성이 필요할 때 켠다. 단일 인스턴스/개발은 core 의
 * 로컬 Caffeine(기본)으로 충분하다.
 */
@ConfigurationProperties(prefix = "framework.cache.redis")
public class RedisCacheProperties {

    /** 선택형 → 기본 off. true 면 core Caffeine 대신 Redis 분산 캐시를 사용. */
    private boolean enabled = false;

    /** 기본 TTL(캐시별 override 없으면 적용). core Caffeine 기본과 맞춰 10분. */
    private Duration timeToLive = Duration.ofMinutes(10);

    /** 전역 키 접두. 비우면 Spring Data Redis 기본("캐시명::키"). 채우면 "prefix캐시명::키". */
    private String keyPrefix = "";

    /** null 값 캐시 허용(기본 true = 캐시 관통/스탬피드 일부 완화). false 면 null 미저장. */
    private boolean cacheNullValues = true;

    /** 캐시명 → TTL override. 여기 없는 캐시는 {@link #timeToLive} 적용. */
    private Map<String, Duration> ttls = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public boolean isCacheNullValues() {
        return cacheNullValues;
    }

    public void setCacheNullValues(boolean cacheNullValues) {
        this.cacheNullValues = cacheNullValues;
    }

    public Map<String, Duration> getTtls() {
        return ttls;
    }

    public void setTtls(Map<String, Duration> ttls) {
        this.ttls = ttls;
    }
}
