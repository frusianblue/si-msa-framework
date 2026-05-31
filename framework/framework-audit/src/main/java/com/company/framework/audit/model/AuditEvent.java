package com.company.framework.audit.model;

import java.time.Instant;

/**
 * 표준 감사 이벤트(불변). 두 출처를 동일 포맷으로 통합한다.
 *  - 메서드 감사: @AuditLog 가 붙은 비즈니스 메서드(AuditTrailAspect)
 *  - 접속 감사: 로그인 성공/실패/로그아웃(LoginAuditListener ← LoginAuditEvent)
 */
public record AuditEvent(
        Instant eventTime,
        String eventType,
        String actor,
        String action,
        String target,
        String result,
        String clientIp,
        String traceId,
        String detail,
        Long elapsedMs) {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";
}
