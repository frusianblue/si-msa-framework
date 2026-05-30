package com.company.framework.idempotency.store;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 구현(store.type=redis). 다중 인스턴스 공유. SETNX(setIfAbsent) 로 원자적 선점.
 * spring-data-redis 가 클래스패스에 있을 때만 오토컨피그가 활성(@ConditionalOnClass).
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String LOCK = "idem:lock:";
    private static final String RESULT = "idem:res:";

    private final StringRedisTemplate redis;

    public RedisIdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean putIfAbsent(String key, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(LOCK + key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void saveResult(String key, String resultJson, Duration ttl) {
        redis.opsForValue().set(RESULT + key, resultJson, ttl);
    }

    @Override
    public Optional<String> findResult(String key) {
        return Optional.ofNullable(redis.opsForValue().get(RESULT + key));
    }
}
