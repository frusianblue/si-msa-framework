package com.company.framework.security.loginattempt;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단일 인스턴스/로컬용 로그인 시도 제한. 재시작 시 소멸, MSA 다중 인스턴스 공유 불가.
 * 운영(다중 인스턴스)에서는 Redis 구현으로 교체 권장(LoginAttemptService 빈만 대체).
 */
public class InMemoryLoginAttemptService implements LoginAttemptService {

    private static final class Counter {
        int failures;
        Instant lockedUntil;
    }

    private final LoginAttemptProperties props;
    private final ConcurrentHashMap<String, Counter> store = new ConcurrentHashMap<>();

    public InMemoryLoginAttemptService(LoginAttemptProperties props) {
        this.props = props;
    }

    @Override
    public void assertNotLocked(String key) {
        if (isLocked(key)) {
            throw new BusinessException(ErrorCode.Common.LOGIN_LOCKED, "로그인 시도가 많아 계정이 일시적으로 잠겼습니다. 잠시 후 다시 시도하세요.");
        }
    }

    @Override
    public void recordFailure(String key) {
        if (!props.isEnabled() || key == null) return;
        store.compute(key, (k, c) -> {
            if (c == null) c = new Counter();
            c.failures++;
            if (c.failures >= props.getMaxAttempts()) {
                c.lockedUntil = Instant.now().plus(props.getLockDuration());
            }
            return c;
        });
    }

    @Override
    public void reset(String key) {
        if (key != null) store.remove(key);
    }

    @Override
    public boolean isLocked(String key) {
        if (!props.isEnabled() || key == null) return false;
        Counter c = store.get(key);
        if (c == null || c.lockedUntil == null) return false;
        if (Instant.now().isAfter(c.lockedUntil)) { // 잠금 만료 → 초기화
            store.remove(key);
            return false;
        }
        return true;
    }
}
