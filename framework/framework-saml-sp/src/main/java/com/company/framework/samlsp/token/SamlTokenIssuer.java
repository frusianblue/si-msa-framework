package com.company.framework.samlsp.token;

import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.TokenResponse;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.token.TokenStore;
import java.util.UUID;

/**
 * SAML 신원확인 후 자체 토큰을 발급하는 계약. 소셜 로그인의 {@code OAuthTokenIssuer} 와 같은 역할이지만,
 * SAML 모듈이 oauth-client(선택형)에 결합되지 않도록 framework-security 의 {@link JwtProvider}/{@link TokenStore}
 * 만 재사용한다(= 발급 JWT 형태는 자체 로그인/소셜 로그인과 동일).
 *
 * <p>기본 구현은 {@link DirectSamlTokenIssuer}. 동시 로그인 제어·접속 감사까지 통합하려면 프로젝트가
 * LoginService 위임 구현을 {@code @Bean} 으로 등록해 교체할 수 있다({@code @ConditionalOnMissingBean} 이라 자동 override).
 */
public interface SamlTokenIssuer {

    TokenResponse issue(AuthenticatedUser user);

    /** 기본 발급기: security 의 JwtProvider/TokenStore 로 access(JWT)+refresh 를 발급·저장(소셜 로그인 기본과 동일 형태). */
    final class DirectSamlTokenIssuer implements SamlTokenIssuer {

        private final JwtProvider jwtProvider;
        private final TokenStore tokenStore;

        public DirectSamlTokenIssuer(JwtProvider jwtProvider, TokenStore tokenStore) {
            this.jwtProvider = jwtProvider;
            this.tokenStore = tokenStore;
        }

        @Override
        public TokenResponse issue(AuthenticatedUser user) {
            String access = jwtProvider.createAccessToken(user.userId(), user.roles());
            String refresh = UUID.randomUUID().toString().replace("-", "");
            tokenStore.saveRefresh(
                    refresh, new TokenStore.RefreshEntry(user.userId(), user.roles()), jwtProvider.refreshTtl());
            return new TokenResponse(
                    access, refresh, "Bearer", jwtProvider.accessTtl().toSeconds(), user.roles());
        }
    }
}
