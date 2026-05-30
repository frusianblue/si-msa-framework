package com.company.framework.core.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 컨트롤러/서비스 계층 실행시간 측정. 임계치 초과 시 WARN 으로 슬로우 쿼리/로직 탐지.
 */
@Aspect
public class ExecutionTimeAspect {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimeAspect.class);
    private static final long SLOW_THRESHOLD_MS = 1000L;

    @Pointcut("execution(* com.company..controller..*(..)) || execution(* com.company..service..*(..))")
    public void appLayer() {}

    @Around("appLayer()")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long took = System.currentTimeMillis() - start;
            String sig = pjp.getSignature().toShortString();
            if (took >= SLOW_THRESHOLD_MS) {
                log.warn("[SLOW] {} took {}ms", sig, took);
            } else if (log.isDebugEnabled()) {
                log.debug("{} took {}ms", sig, took);
            }
        }
    }
}
