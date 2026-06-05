package com.company.framework.webauthn.web;

import com.company.framework.security.auth.AuthenticatedUser;
import org.springframework.security.core.Authentication;

/**
 * 패스키 ceremony 성공으로 세션에 수립된 Spring Security {@link Authentication} 을 프레임워크 표준
 * {@link AuthenticatedUser}(userId/name/roles)로 변환하는 계약. 기본 구현
 * ({@link DefaultWebAuthnAuthenticatedUserResolver})은 principal 이름과 권한을 그대로 매핑한다.
 *
 * <p>user handle ↔ 내부 사용자 ID 매핑, 추가 클레임 부여 등이 필요하면 프로젝트가 {@code @Bean} 으로 교체한다
 * ({@code @ConditionalOnMissingBean} 이라 자동 override).
 */
public interface WebAuthnAuthenticatedUserResolver {
    AuthenticatedUser resolve(Authentication authentication);
}
