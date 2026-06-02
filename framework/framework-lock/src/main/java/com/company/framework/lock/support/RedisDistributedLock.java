package com.company.framework.lock.support;

import com.company.framework.lock.DistributedLock;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis 분산 락(type=redis). 다중 파드가 동일 Redis 를 공유해 상호배제한다. spring-data-redis 가 클래스패스에 있을 때만
 * 오토컨피그가 활성({@code @ConditionalOnClass}).
 *
 * <p>설계 요점
 *
 * <ul>
 *   <li><b>획득</b>: {@code SET key token NX PX ttl}({@link org.springframework.data.redis.core.ValueOperations#setIfAbsent}).
 *       원자적 선점 + TTL 동시 설정.
 *   <li><b>해제</b>: Lua <i>compare-and-delete</i> — 값이 내 토큰일 때만 삭제. "GET 후 DEL" 의 경합(내 락이 만료→타 인스턴스
 *       재획득 사이에 내가 DEL)을 막는다. 단일 인스턴스 Redis 기준 정석 패턴.
 *   <li><b>연장</b>: Lua <i>compare-and-pexpire</i> — 내 토큰일 때만 TTL 재설정.
 * </ul>
 *
 * <p>주: 단일 Redis(또는 단일 마스터) 전제의 락이다. 멀티마스터 Redlock 수준의 합의가 필요한 극단적 신뢰성 요구는 범위 밖
 * (대다수 SI/배치/스케줄러 중복방지에는 본 구현으로 충분).
 */
public class RedisDistributedLock implements DistributedLock {

    private static final String PREFIX = "lock:";

    /** 내 토큰일 때만 DEL. KEYS[1]=락키, ARGV[1]=토큰. 반환 1=해제, 0=비소유. */
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /** 내 토큰일 때만 PEXPIRE. KEYS[1]=락키, ARGV[1]=토큰, ARGV[2]=ttl(ms). */
    private static final RedisScript<Long> KEEP_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisDistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryLock(String key, String token, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(PREFIX + key, token, ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void unlock(String key, String token) {
        redis.execute(UNLOCK_SCRIPT, List.of(PREFIX + key), token);
    }

    @Override
    public void keepUntil(String key, String token, Duration ttl) {
        redis.execute(KEEP_SCRIPT, List.of(PREFIX + key), token, String.valueOf(ttl.toMillis()));
    }
}
