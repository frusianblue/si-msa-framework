package com.company.framework.lock.aspect;

import com.company.framework.lock.DistributedLock;
import com.company.framework.lock.annotation.SchedulerLock;
import com.company.framework.lock.config.LockProperties;
import java.time.Duration;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;

/**
 * {@link SchedulerLock} 가 붙은 메서드 실행을 분산 락으로 감싼다.
 *
 * <p>흐름: 새 토큰으로 {@code atMostFor} 동안 락 시도 → 못 잡으면 스킵(다른 인스턴스가 실행 중) → 잡으면 메서드 실행 →
 * {@code finally} 에서 해제. 단, 실행이 {@code atLeastFor} 보다 빨리 끝나면 즉시 해제하지 않고 {@code atLeastFor} 경계까지
 * 락을 유지({@link DistributedLock#keepUntil})해 클럭 스큐로 인한 직후 재실행을 막는다.
 *
 * <p>예외가 나도 {@code finally} 에서 정확히 정리한다. {@code atLeastFor} 미설정(0)이면 종료 즉시 해제한다.
 */
@Aspect
public class SchedulerLockAspect {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLockAspect.class);

    private final DistributedLock lock;
    private final LockProperties properties;

    public SchedulerLockAspect(DistributedLock lock, LockProperties properties) {
        this.lock = lock;
        this.properties = properties;
    }

    @Around("@annotation(schedulerLock)")
    public Object around(ProceedingJoinPoint joinPoint, SchedulerLock schedulerLock) throws Throwable {
        String key = resolveName(joinPoint, schedulerLock.name());
        Duration atMostFor = resolve(schedulerLock.atMostFor(), properties.getDefaultAtMostFor());
        Duration atLeastFor = resolve(schedulerLock.atLeastFor(), properties.getDefaultAtLeastFor());
        String token = UUID.randomUUID().toString();

        if (!lock.tryLock(key, token, atMostFor)) {
            log.debug("[scheduler-lock] 다른 인스턴스가 보유 중 — 실행 스킵 name={}", key);
            return null; // @SchedulerLock 대상은 void 가정
        }
        log.debug("[scheduler-lock] 획득 name={} atMostFor={} atLeastFor={}", key, atMostFor, atLeastFor);

        long startNanos = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            if (!atLeastFor.isZero() && elapsed.compareTo(atLeastFor) < 0) {
                // 최소 보유 경계까지 잠금 유지 후 자동 만료(클럭 스큐 직후 재실행 방지).
                lock.keepUntil(key, token, atLeastFor.minus(elapsed));
                log.debug("[scheduler-lock] atLeastFor 까지 유지 name={} 남은={}", key, atLeastFor.minus(elapsed));
            } else {
                lock.unlock(key, token);
                log.debug("[scheduler-lock] 해제 name={} elapsed={}", key, elapsed);
            }
        }
    }

    private String resolveName(ProceedingJoinPoint joinPoint, String declared) {
        if (declared != null && !declared.isBlank()) {
            return declared;
        }
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        return sig.getDeclaringType().getSimpleName() + "." + sig.getName();
    }

    private Duration resolve(String value, Duration fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return DurationStyle.detectAndParse(value.trim());
    }
}
