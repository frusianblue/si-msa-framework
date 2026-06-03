package com.company.framework.oauthclient.store;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 운영 표준 state 저장소(다중 파드 공유 + TTL 네이티브 만료). 키: {keyPrefix}{state}, 값: 공급자 id.
 * 소비는 getAndDelete 로 원자적 1회용을 보장(재사용 공격 차단).
 */
public class RedisOAuthStateStore implements OAuthStateStore {

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisOAuthStateStore(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = (keyPrefix == null || keyPrefix.isBlank()) ? "oauth:state:" : keyPrefix;
    }

    @Override
    public void save(String state, String providerId, Duration ttl) {
        redis.opsForValue().set(keyPrefix + state, providerId, ttl);
    }

    @Override
    public Optional<String> consume(String state) {
        String providerId = redis.opsForValue().getAndDelete(keyPrefix + state);
        return Optional.ofNullable(providerId);
    }
}
