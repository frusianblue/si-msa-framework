package com.company.framework.audit.listener;

import com.company.framework.audit.model.AuditEvent;
import com.company.framework.audit.sink.AuditEventSink;
import com.company.framework.audit.support.AuditContext;
import com.company.framework.security.auth.LoginAuditEvent;
import org.springframework.context.event.EventListener;

/**
 * 접속 감사: framework-security 가 발행한 LoginAuditEvent(성공/실패/로그아웃)를 표준 감사 이벤트로 적재.
 * security 는 audit 를 알지 못하며(이벤트만 발행), 이 리스너는 audit 모듈이 있을 때만 존재한다.
 */
public class LoginAuditListener {

    private final AuditEventSink sink;

    public LoginAuditListener(AuditEventSink sink) {
        this.sink = sink;
    }

    @EventListener
    public void on(LoginAuditEvent e) {
        String result = e.type().name().endsWith("FAILURE") ? AuditEvent.RESULT_FAILURE : AuditEvent.RESULT_SUCCESS;
        String actor = (e.loginId() != null && !e.loginId().isBlank()) ? e.loginId() : "anonymous";
        sink.save(new AuditEvent(
                e.occurredAt(),
                e.type().name(), // LOGIN_* | LOGOUT | MFA_*
                actor,
                "AUTH",
                "SESSION",
                result,
                e.clientIp(),
                AuditContext.traceId(),
                e.detail(),
                null));
    }
}
