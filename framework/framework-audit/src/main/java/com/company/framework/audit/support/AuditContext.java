package com.company.framework.audit.support;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 감사 이벤트의 공통 컨텍스트(누가/어디서/어떤 추적)를 현재 스레드에서 수집.
 * core 의 AuditLogAspect 와 동일 사상(SecurityContext + MDC traceId)에 클라이언트 IP 를 추가한다.
 */
public final class AuditContext {

    private AuditContext() {}

    public static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    public static String traceId() {
        return MDC.get("traceId");
    }

    /** X-Forwarded-For(프록시/인그레스 뒤) 우선, 없으면 remoteAddr. 요청 컨텍스트 밖이면 null. */
    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
