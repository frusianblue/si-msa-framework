package com.company.framework.oauthclient.web;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.oauthclient.config.OAuthClientProperties;
import com.company.framework.oauthclient.core.OAuthClient;
import com.company.framework.oauthclient.core.OAuthUserInfo;
import com.company.framework.oauthclient.core.OAuthUserResolver;
import com.company.framework.oauthclient.core.ProviderRegistry;
import com.company.framework.oauthclient.store.OAuthStateStore;
import com.company.framework.oauthclient.token.OAuthTokenIssuer;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.TokenResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 소셜 로그인 오케스트레이션.
 *
 * <ol>
 *   <li>{@link #authorizeUrl(String)} — state 발급/저장 후 IdP 인가 URL 생성
 *   <li>{@link #callback(String, String, String)} — state 검증 → 토큰 교환 → userinfo → 사용자 매핑 → 자체 토큰 발급
 * </ol>
 */
public class OAuthLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProviderRegistry registry;
    private final OAuthClient client;
    private final OAuthStateStore stateStore;
    private final OAuthUserResolver userResolver;
    private final OAuthTokenIssuer tokenIssuer;
    private final Duration stateTtl;

    public OAuthLoginService(
            ProviderRegistry registry,
            OAuthClient client,
            OAuthStateStore stateStore,
            OAuthUserResolver userResolver,
            OAuthTokenIssuer tokenIssuer,
            OAuthClientProperties properties) {
        this.registry = registry;
        this.client = client;
        this.stateStore = stateStore;
        this.userResolver = userResolver;
        this.tokenIssuer = tokenIssuer;
        this.stateTtl = properties.getState().getTtl();
    }

    /** IdP 인가 페이지로 보낼 URL. state 를 발급/저장해 CSRF 를 방지한다. */
    public String authorizeUrl(String providerId) {
        OAuthClientProperties.Provider provider = registry.require(providerId);
        String state = newState();
        stateStore.save(state, providerId, stateTtl);
        return registry.authorizationUrl(providerId, provider, state);
    }

    /** IdP 콜백 처리. 성공 시 자체 토큰을 발급해 반환한다. */
    public TokenResponse callback(String providerId, String code, String state) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "인가 코드(code)가 없습니다.");
        }
        String boundProvider = stateStore
                .consume(state)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED, "state 검증 실패(만료/위조/재사용)."));
        if (!boundProvider.equals(providerId)) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "state 의 공급자와 콜백 공급자가 일치하지 않습니다.");
        }

        OAuthClientProperties.Provider provider = registry.require(providerId);
        String redirectUri = registry.redirectUri(providerId, provider);
        String accessToken = client.exchangeCodeForAccessToken(provider, code, redirectUri);
        Map<String, Object> raw = client.fetchUserInfo(provider, accessToken);
        OAuthUserInfo userInfo = registry.toUserInfo(providerId, provider, raw);

        AuthenticatedUser user = userResolver.resolve(userInfo); // 앱이 매핑/가입/거부
        return tokenIssuer.issue(user);
    }

    private static String newState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
