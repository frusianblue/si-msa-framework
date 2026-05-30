package com.company.framework.security.auth;

/**
 * 프로젝트가 구현하는 단 하나의 인증 계약.
 * DB/LDAP/SSO/GPKI 등 방식이 달라도 이 인터페이스만 구현하면 공통 로그인 흐름이 동작한다.
 * 인증 실패 시 BusinessException(UNAUTHORIZED) 등을 던지면 된다.
 */
public interface Authenticator {
    AuthenticatedUser authenticate(LoginCommand command);
}
