package com.company.framework.oauthclient.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 소셜 로그인(OAuth 2.0 / OIDC) 설정. 모듈은 기본 비활성(framework.oauth-client.enabled=false)이며,
 * google/kakao/naver 는 표준 엔드포인트/속성 키 프리셋을 내장하므로 client-id/client-secret 만 채우면 된다.
 *
 * <pre>
 * framework:
 *   oauth-client:
 *     enabled: false
 *     base-redirect-uri: "http://localhost:8080"   # 콜백 베이스(redirect-uri 미지정 시 {base}/api/v1/auth/oauth/{id}/callback)
 *     state:
 *       ttl: PT5M
 *       store: { type: memory, key-prefix: "oauth:state:" }   # memory | redis
 *     providers:
 *       google:
 *         client-id: "..."
 *         client-secret: "..."
 *         # 나머지(authorization-uri/token-uri/user-info-uri/scope/*-attribute)는 프리셋 자동 적용
 *       kakao:
 *         client-id: "..."
 *         client-secret: "..."        # 카카오는 client-secret 미사용 가능(콘솔 설정에 따름)
 * </pre>
 *
 * <p>프리셋이 없는 임의 공급자도 authorization-uri/token-uri/user-info-uri/*-attribute 를 직접 지정하면 동작한다.
 */
@ConfigurationProperties(prefix = "framework.oauth-client")
public class OAuthClientProperties {

    /** 모듈 전체 on/off. */
    private boolean enabled = false;

    /** 콜백 베이스 URL. provider.redirectUri 미지정 시 {base}/api/v1/auth/oauth/{id}/callback 으로 조립. */
    private String baseRedirectUri = "http://localhost:8080";

    @NestedConfigurationProperty
    private State state = new State();

    /** 공급자 맵(키 = 공급자 id, 예: google/kakao/naver). */
    private Map<String, Provider> providers = new LinkedHashMap<>();

    public static class State {
        private Duration ttl = Duration.ofMinutes(5);

        @NestedConfigurationProperty
        private Store store = new Store();

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Store getStore() {
            return store;
        }

        public void setStore(Store store) {
            this.store = store;
        }
    }

    public static class Store {
        /** memory(기본) | redis. 다중 인스턴스(파드)에서는 authorize/callback 이 다른 파드로 갈 수 있어 redis 권장. */
        private String type = "memory";

        private String keyPrefix = "oauth:state:";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    /** 단일 공급자 설정. 미지정 필드는 {@link OAuthClientProperties#applyPresets()} 가 프리셋으로 보충. */
    public static class Provider {
        private String clientId;
        private String clientSecret;
        private String authorizationUri;
        private String tokenUri;
        private String userInfoUri;
        private List<String> scope = new ArrayList<>();

        /** userinfo 응답에서 외부 식별자(providerId)를 꺼낼 키. 점(.) 표기로 중첩 접근(예: response.id). */
        private String userNameAttribute;

        private String emailAttribute;
        private String nameAttribute;

        /** redirect_uri 직접 지정(미지정 시 baseRedirectUri 로 조립). IdP 콘솔 등록값과 정확히 일치해야 한다. */
        private String redirectUri;

        /** OIDC 강화 설정(id_token 검증/discovery/nonce). 기본 비활성 — 켜면 표준 OIDC RP 로 동작. */
        @NestedConfigurationProperty
        private Oidc oidc = new Oidc();

        public Oidc getOidc() {
            return oidc;
        }

        public void setOidc(Oidc oidc) {
            this.oidc = oidc;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getAuthorizationUri() {
            return authorizationUri;
        }

        public void setAuthorizationUri(String authorizationUri) {
            this.authorizationUri = authorizationUri;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getUserInfoUri() {
            return userInfoUri;
        }

        public void setUserInfoUri(String userInfoUri) {
            this.userInfoUri = userInfoUri;
        }

        public List<String> getScope() {
            return scope;
        }

        public void setScope(List<String> scope) {
            this.scope = scope;
        }

        public String getUserNameAttribute() {
            return userNameAttribute;
        }

        public void setUserNameAttribute(String userNameAttribute) {
            this.userNameAttribute = userNameAttribute;
        }

        public String getEmailAttribute() {
            return emailAttribute;
        }

        public void setEmailAttribute(String emailAttribute) {
            this.emailAttribute = emailAttribute;
        }

        public String getNameAttribute() {
            return nameAttribute;
        }

        public void setNameAttribute(String nameAttribute) {
            this.nameAttribute = nameAttribute;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }
    }

    /** OIDC(OpenID Connect) RP 강화 설정. provider 별로 켠다(기본 off — kakao/naver 등 비OIDC 흐름 보존). */
    public static class Oidc {
        /** OIDC 검증 on/off. 켜면 id_token 을 받아 검증하고 그 클레임으로 신원을 구성한다. */
        private boolean enabled = false;

        /** 기대 issuer(iss 클레임). discovery 출처로도 사용. */
        private String issuer;

        /** JWKS 엔드포인트. 미지정 시 discovery 로 보충. */
        private String jwksUri;

        /** discovery 문서 URL. 미지정 시 issuer 로부터 {@code /.well-known/openid-configuration} 조립. */
        private String discoveryUri;

        /** exp/nbf 허용 시계 오차. */
        private Duration clockSkew = Duration.ofSeconds(60);

        /** nonce 사용(authorize↔callback 바인딩, id_token 재생/위조 차단). 기본 on. */
        private boolean nonce = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }

        public String getDiscoveryUri() {
            return discoveryUri;
        }

        public void setDiscoveryUri(String discoveryUri) {
            this.discoveryUri = discoveryUri;
        }

        public Duration getClockSkew() {
            return clockSkew;
        }

        public void setClockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
        }

        public boolean isNonce() {
            return nonce;
        }

        public void setNonce(boolean nonce) {
            this.nonce = nonce;
        }
    }

    /**
     * 알려진 공급자(google/kakao/naver)의 미지정 필드를 표준값으로 채운다. 명시값이 있으면 보존(override)한다.
     * 오토컨피그가 1회 호출한다.
     */
    public void applyPresets() {
        providers.forEach((id, p) -> {
            switch (id.toLowerCase()) {
                case "google" -> {
                    defaultUri(
                            p::getAuthorizationUri,
                            p::setAuthorizationUri,
                            "https://accounts.google.com/o/oauth2/v2/auth");
                    defaultUri(p::getTokenUri, p::setTokenUri, "https://oauth2.googleapis.com/token");
                    defaultUri(
                            p::getUserInfoUri, p::setUserInfoUri, "https://openidconnect.googleapis.com/v1/userinfo");
                    defaultScope(p, List.of("openid", "email", "profile"));
                    defaultAttr(p, "sub", "email", "name");
                    defaultUri(p.getOidc()::getIssuer, p.getOidc()::setIssuer, "https://accounts.google.com");
                }
                case "kakao" -> {
                    defaultUri(
                            p::getAuthorizationUri, p::setAuthorizationUri, "https://kauth.kakao.com/oauth/authorize");
                    defaultUri(p::getTokenUri, p::setTokenUri, "https://kauth.kakao.com/oauth/token");
                    defaultUri(p::getUserInfoUri, p::setUserInfoUri, "https://kapi.kakao.com/v2/user/me");
                    defaultAttr(p, "id", "kakao_account.email", "kakao_account.profile.nickname");
                }
                case "naver" -> {
                    defaultUri(
                            p::getAuthorizationUri, p::setAuthorizationUri, "https://nid.naver.com/oauth2.0/authorize");
                    defaultUri(p::getTokenUri, p::setTokenUri, "https://nid.naver.com/oauth2.0/token");
                    defaultUri(p::getUserInfoUri, p::setUserInfoUri, "https://openapi.naver.com/v1/nid/me");
                    defaultAttr(p, "response.id", "response.email", "response.name");
                }
                default -> {
                    // 프리셋 없는 임의 공급자 — 사용자가 uri/attribute 를 직접 지정해야 한다(검증은 ProviderRegistry).
                }
            }
            applyOidcDefaults(p);
        });
    }

    private static void defaultUri(
            java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter, String value) {
        if (getter.get() == null || getter.get().isBlank()) setter.accept(value);
    }

    private static void defaultScope(Provider p, List<String> scope) {
        if (p.getScope() == null || p.getScope().isEmpty()) p.setScope(new ArrayList<>(scope));
    }

    private static void defaultAttr(Provider p, String userName, String email, String name) {
        if (p.getUserNameAttribute() == null || p.getUserNameAttribute().isBlank()) p.setUserNameAttribute(userName);
        if (p.getEmailAttribute() == null || p.getEmailAttribute().isBlank()) p.setEmailAttribute(email);
        if (p.getNameAttribute() == null || p.getNameAttribute().isBlank()) p.setNameAttribute(name);
    }

    /** OIDC 활성 공급자의 신원 attribute 기본값(표준 클레임 sub/email/name). 명시값은 보존. */
    private static void applyOidcDefaults(Provider p) {
        if (p.getOidc() == null || !p.getOidc().isEnabled()) return;
        defaultAttr(p, "sub", "email", "name");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseRedirectUri() {
        return baseRedirectUri;
    }

    public void setBaseRedirectUri(String baseRedirectUri) {
        this.baseRedirectUri = baseRedirectUri;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers;
    }
}
