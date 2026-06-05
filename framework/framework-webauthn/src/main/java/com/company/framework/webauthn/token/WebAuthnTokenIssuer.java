package com.company.framework.webauthn.token;

import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.TokenResponse;

/**
 * 패스키 인증으로 신원이 확인된 사용자에게 프레임워크 표준 토큰을 발급하는 계약. 기본 구현
 * ({@link DirectWebAuthnTokenIssuer})은 security 의 JwtProvider/TokenStore 를 직접 사용한다(자체 비밀번호 로그인의
 * 발급 로직과 동일한 형태).
 *
 * <p>동시 로그인 제어·접속 감사까지 자체 비밀번호 로그인과 동일하게 적용하고 싶으면, 프로젝트가 LoginService 에
 * 위임하는 구현을 {@code @Bean} 으로 등록해 교체할 수 있다({@code @ConditionalOnMissingBean} 이라 자동 override).
 * (oauth-client 의 {@code OAuthTokenIssuer} 와 동일 사상)
 */
public interface WebAuthnTokenIssuer {
    TokenResponse issue(AuthenticatedUser user);
}
