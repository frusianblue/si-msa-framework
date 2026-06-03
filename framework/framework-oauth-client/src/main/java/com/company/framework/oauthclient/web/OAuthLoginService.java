package com.company.framework.oauthclient.web;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.oauthclient.config.OAuthClientProperties;
import com.company.framework.oauthclient.core.OAuthClient;
import com.company.framework.oauthclient.core.OAuthUserInfo;
import com.company.framework.oauthclient.core.OAuthUserResolver;
import com.company.framework.oauthclient.core.ProviderRegistry;
import com.company.framework.oauthclient.oidc.IdTokenVerifier;
import com.company.framework.oauthclient.oidc.OidcMetadataResolver;
import com.company.framework.oauthclient.store.OAuthStateStore;
import com.company.framework.oauthclient.token.OAuthTokenIssuer;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.TokenResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 소셜 로그인(OAuth 2.0 / OIDC) 오케스트레이션.
 *
 * <ol>
 *   <li>{@link #authorizeUrl(String)} — (OIDC면 discovery 보충 +) state/nonce 발급·저장 후 IdP 인가 URL 생성
 *   <li>{@link #callback(String, String, String)} — state 검증 → 토큰 교환 → (OIDC면 id_token 검증, 아니면 userinfo)
 *       → 사용자 매핑 → 자체 토큰 발급
 * </ol>
 *
 * <p>OIDC 활성 공급자는 id_token 을 받아 서명/iss/aud/exp/nonce 를 검증하고 그 클레임으로 신원을 만든다
 * (userInfoUri 가 설정돼 있으면 userinfo 로 빈 필드를 보충). 비OIDC 공급자(kakao/naver 등)는 기존 userinfo 흐름 그대로.
 */
public class OAuthLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProviderRegistry registry;
    private final OAuthClient client;
    private final OAuthStateStore stateStore;
    private final OAuthUserResolver userResolver;
    private final OAuthTokenIssuer tokenIssuer;
    private final OidcMetadataResolver metadataResolver;
    private final IdTokenVerifier idTokenVerifier;
    private final Duration stateTtl;

    public OAuthLoginService(
            ProviderRegistry registry,
            OAuthClient client,
            OAuthStateStore stateStore,
            OAuthUserResolver userResolver,
            OAuthTokenIssuer tokenIssuer,
            OidcMetadataResolver metadataResolver,
            IdTokenVerifier idTokenVerifier,
            OAuthClientProperties properties) {
        this.registry = registry;
        this.client = client;
        this.stateStore = stateStore;
        this.userResolver = userResolver;
        this.tokenIssuer = tokenIssuer;
        this.metadataResolver = metadataResolver;
        this.idTokenVerifier = idTokenVerifier;
        this.stateTtl = properties.getState().getTtl();
    }

    /** IdP 인가 페이지로 보낼 URL. state(+OIDC면 nonce)를 발급/저장해 CSRF/재생을 방지한다. */
    public String authorizeUrl(String providerId) {
        OAuthClientProperties.Provider provider = registry.require(providerId);
        metadataResolver.ensureResolved(providerId, provider); // OIDC면 discovery 로 엔드포인트 보충(1회)
        String state = newRandom();
        if (isOidc(provider)) {
            String nonce = newRandom();
            stateStore.save(state, providerId, nonce, stateTtl);
            return registry.authorizationUrl(providerId, provider, state, nonce);
        }
        stateStore.save(state, providerId, stateTtl);
        return registry.authorizationUrl(providerId, provider, state);
    }

    /** IdP 콜백 처리. 성공 시 자체 토큰을 발급해 반환한다. */
    public TokenResponse callback(String providerId, String code, String state) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "인가 코드(code)가 없습니다.");
        }
        OAuthStateStore.StateData stateData = stateStore
                .consumeState(state)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED, "state 검증 실패(만료/위조/재사용)."));
        if (!stateData.providerId().equals(providerId)) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "state 의 공급자와 콜백 공급자가 일치하지 않습니다.");
        }

        OAuthClientProperties.Provider provider = registry.require(providerId);
        metadataResolver.ensureResolved(providerId, provider);
        String redirectUri = registry.redirectUri(providerId, provider);
        Map<String, Object> tokenResponse = client.exchangeCodeForTokens(provider, code, redirectUri);

        Map<String, Object> raw;
        if (isOidc(provider)) {
            String idToken = string(tokenResponse.get("id_token"));
            if (idToken == null || idToken.isBlank()) {
                throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "OIDC 토큰 응답에 id_token 이 없습니다.");
            }
            Map<String, Object> claims = idTokenVerifier.verify(provider, idToken, stateData.nonce());
            raw = new LinkedHashMap<>(claims);
            // userInfoUri 가 있으면 빈 필드를 userinfo 로 보충(id_token 클레임이 우선).
            if (provider.getUserInfoUri() != null && !provider.getUserInfoUri().isBlank()) {
                String accessToken = string(tokenResponse.get("access_token"));
                if (accessToken != null && !accessToken.isBlank()) {
                    Map<String, Object> userInfo = client.fetchUserInfo(provider, accessToken);
                    userInfo.forEach(raw::putIfAbsent);
                }
            }
        } else {
            String accessToken = string(tokenResponse.get("access_token"));
            if (accessToken == null || accessToken.isBlank()) {
                throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "토큰 응답에 access_token 이 없습니다.");
            }
            raw = client.fetchUserInfo(provider, accessToken);
        }

        OAuthUserInfo userInfo = registry.toUserInfo(providerId, provider, raw);
        AuthenticatedUser user = userResolver.resolve(userInfo); // 앱이 매핑/가입/거부
        return tokenIssuer.issue(user);
    }

    private static boolean isOidc(OAuthClientProperties.Provider provider) {
        return provider.getOidc() != null && provider.getOidc().isEnabled();
    }

    private static String string(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String newRandom() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
