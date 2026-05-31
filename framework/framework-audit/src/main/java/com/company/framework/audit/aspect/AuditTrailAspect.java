package com.company.framework.audit.aspect;

import com.company.framework.audit.model.AuditEvent;
import com.company.framework.audit.sink.AuditEventSink;
import com.company.framework.audit.support.AuditContext;
import com.company.framework.core.aspect.AuditLog;
import java.time.Instant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * core 의 @AuditLog 메서드를 표준 감사 이벤트로 "영속화"한다(현 AOP 의 적재 계층).
 * core 의 AuditLogAspect 는 그대로 두며(운영 로그), 이 Aspect 는 싱크(jdbc/logging)로 적재한다.
 * 두 Around 어드바이스는 같은 조인포인트를 중첩 감싸므로 실제 메서드는 한 번만 실행된다.
 */
@Aspect
public class AuditTrailAspect {

    private static final String EVENT_TYPE = "METHOD_AUDIT";

    private final AuditEventSink sink;

    public AuditTrailAspect(AuditEventSink sink) {
        this.sink = sink;
    }

    @Around("@annotation(auditLog)")
    public Object record(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        String result = AuditEvent.RESULT_SUCCESS;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            result = AuditEvent.RESULT_FAILURE;
            throw t;
        } finally {
            long took = System.currentTimeMillis() - start;
            sink.save(new AuditEvent(
                    Instant.now(),
                    EVENT_TYPE,
                    AuditContext.currentActor(),
                    auditLog.action(),
                    auditLog.target(),
                    result,
                    AuditContext.clientIp(),
                    AuditContext.traceId(),
                    null,
                    took));
        }
    }
}
