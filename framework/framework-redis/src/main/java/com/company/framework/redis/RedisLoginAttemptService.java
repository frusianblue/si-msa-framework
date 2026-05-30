package com.company.framework.redis;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.security.loginattempt.LoginAttemptProperties;
import com.company.framework.security.loginattempt.LoginAttemptService;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 운영(다중 인스턴스) 표준 로그인 시도 제한. InMemory 와 달리 카운터/잠금을 Redis 에 두어
 * 모든 파드가 동일한 실패 횟수를 공유한다(k8s 다중 replica 에서 우회 불가).
 *
 * <p>키 / TTL 설계 (네이티브 만료로 별도 정리 불필요):
 * <ul>
 *   <li>{@code la:fail:{key}} : 실패 횟수. 첫 실패 시 TTL = lock-duration 의 롤링 윈도우.
 *       (InMemory 는 카운터에 시간 만료가 없으나, Redis 는 stale 키 방지 위해 롤링 윈도우 적용 — 의도적 차이)</li>
 *   <li>{@code la:lock:{key}} : 잠금 표시. TTL = lock-duration 후 자동 해제.</li>
 * </ul>
 */
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final String FAIL = "la:fail:";
    private static final String LOCK = "la:lock:";

    private final StringRedisTemplate redis;
    private final LoginAttemptProperties props;

    public RedisLoginAttemptService(StringRedisTemplate redis, LoginAttemptProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public void assertNotLocked(String key) {
        if (isLocked(key)) {
            throw new BusinessException(
                    ErrorCode.Common.LOGIN_LOCKED, "로그인 시도가 많아 계정이 일시적으로 잠겼습니다. 잠시 후 다시 시도하세요.");
        }
    }

    @Override
    public void recordFailure(String key) {
        if (!props.isEnabled() || key == null) {
            return;
        }
        Long failures = redis.opsForValue().increment(FAIL + key);
        if (failures == null) {
            return;
        }
        if (failures == 1L) {
            // 첫 실패에만 윈도우 TTL 설정(이후 INCR 은 TTL 유지)
            redis.expire(FAIL + key, props.getLockDuration());
        }
        if (failures >= props.getMaxAttempts()) {
            redis.opsForValue().set(LOCK + key, "1", props.getLockDuration());
            redis.delete(FAIL + key); // 잠금 전환 후 카운터 정리(잠금 해제 시 0 부터)
        }
    }

    @Override
    public void reset(String key) {
        if (key != null) {
            redis.delete(FAIL + key);
            redis.delete(LOCK + key);
        }
    }

    @Override
    public boolean isLocked(String key) {
        if (!props.isEnabled() || key == null) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(LOCK + key));
    }
}
