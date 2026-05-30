package com.company.framework.core.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @AuditLog 가 붙은 메서드 실행을 감사 로그로 남긴다.
 * 운영에서는 별도 audit appender/테이블로 분리 적재하는 것을 권장.
 */
@Aspect
public class AuditLogAspect {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    @Around("@annotation(auditLog)")
    public Object writeAudit(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        String user = currentUser();
        String traceId = MDC.get("traceId");
        long start = System.currentTimeMillis();
        String result = "SUCCESS";
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            result = "FAILURE";
            throw t;
        } finally {
            long took = System.currentTimeMillis() - start;
            audit.info("traceId={} user={} action={} target={} result={} elapsedMs={}",
                    traceId, user, auditLog.action(), auditLog.target(), result, took);
        }
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }
}
