package com.company.framework.oauthclient.token;

import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.TokenResponse;

/**
 * 신원확인된 사용자에게 자체 토큰을 발급하는 계약. 기본 구현({@link DirectOAuthTokenIssuer})은 security 의
 * JwtProvider/TokenStore 를 직접 사용한다.
 *
 * <p>동시 로그인 제어·접속 감사까지 자체 비밀번호 로그인과 동일하게 적용하고 싶으면, 프로젝트가 LoginService 에
 * 위임하는 구현을 {@code @Bean} 으로 등록해 교체할 수 있다(@ConditionalOnMissingBean 이라 자동 override).
 */
public interface OAuthTokenIssuer {
    TokenResponse issue(AuthenticatedUser user);
}
