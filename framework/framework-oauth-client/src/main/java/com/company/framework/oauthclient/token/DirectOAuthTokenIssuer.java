package com.company.framework.oauthclient.token;

import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.TokenResponse;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.token.TokenStore;
import java.util.UUID;

/**
 * 기본 토큰 발급기. security 의 {@link JwtProvider}/{@link TokenStore} 로 access(JWT)+refresh 를 발급하고
 * refresh 를 저장한다(자체 비밀번호 로그인의 발급 로직과 동일한 형태). LoginService 의존이 없으므로 소셜 로그인
 * 전용(Authenticator 미구현) 프로젝트에서도 동작한다.
 */
public class DirectOAuthTokenIssuer implements OAuthTokenIssuer {

    private final JwtProvider jwtProvider;
    private final TokenStore tokenStore;

    public DirectOAuthTokenIssuer(JwtProvider jwtProvider, TokenStore tokenStore) {
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
