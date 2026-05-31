package com.company.framework.security.auth;

import java.time.Instant;

/**
 * 인증 관련 감사 이벤트(접속 로그). LoginService 가 ApplicationEventPublisher 로 발행한다.
 * framework-audit 모듈이 있으면 이를 수신해 DB/Kafka 로 적재한다(없으면 무해히 무시 — 강결합 0).
 */
public record LoginAuditEvent(Type type, String loginId, String clientIp, String detail, Instant occurredAt) {

    public enum Type {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT
    }

    public static LoginAuditEvent of(Type type, String loginId, String clientIp, String detail) {
        return new LoginAuditEvent(type, loginId, clientIp, detail, Instant.now());
    }
}
