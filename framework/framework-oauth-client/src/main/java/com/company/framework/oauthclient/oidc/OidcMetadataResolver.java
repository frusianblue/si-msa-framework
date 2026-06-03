package com.company.framework.oauthclient.oidc;

import com.company.framework.oauthclient.config.OAuthClientProperties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * OIDC discovery 를 공급자 설정에 1회 반영한다(blank 필드만 보충 — 명시값은 보존). 기동을 막지 않도록
 * <b>지연(lazy)</b> 적용: 첫 authorize/callback 시점에 조회하고 캐시한다. discovery 실패 시 다음 호출에서 재시도.
 *
 * <p>discovery 출처: {@code oidc.discovery-uri} 우선, 없으면 {@code oidc.issuer + /.well-known/openid-configuration}.
 * 둘 다 없으면(엔드포인트를 직접 지정한 경우) discovery 를 건너뛴다.
 */
public class OidcMetadataResolver {

    private final OidcDiscoveryClient discovery;
    private final ConcurrentMap<String, Boolean> resolved = new ConcurrentHashMap<>();

    public OidcMetadataResolver(OidcDiscoveryClient discovery) {
        this.discovery = discovery;
    }

    /** OIDC 공급자면 discovery 를 1회 적용. 비활성/이미 적용/출처 없음이면 무동작. */
    public void ensureResolved(String providerId, OAuthClientProperties.Provider provider) {
        OAuthClientProperties.Oidc oidc = provider.getOidc();
        if (oidc == null || !oidc.isEnabled()) {
            return;
        }
        if (Boolean.TRUE.equals(resolved.get(providerId))) {
            return;
        }
        String discoveryUri = discoveryUri(oidc);
        if (discoveryUri == null) {
            resolved.put(providerId, Boolean.TRUE); // 직접 지정 모드 — discovery 불필요
            return;
        }
        OidcDiscoveryClient.Metadata md;
        try {
            md = discovery.fetch(discoveryUri);
        } catch (RuntimeException e) {
            resolved.remove(providerId); // 다음 호출에서 재시도
            throw e;
        }
        // blank 필드만 보충(명시 설정 우선).
        if (isBlank(provider.getAuthorizationUri())) {
            provider.setAuthorizationUri(md.authorizationEndpoint());
        }
        if (isBlank(provider.getTokenUri())) {
            provider.setTokenUri(md.tokenEndpoint());
        }
        if (isBlank(provider.getUserInfoUri()) && !isBlank(md.userInfoEndpoint())) {
            provider.setUserInfoUri(md.userInfoEndpoint());
        }
        if (isBlank(oidc.getJwksUri())) {
            oidc.setJwksUri(md.jwksUri());
        }
        if (isBlank(oidc.getIssuer())) {
            oidc.setIssuer(md.issuer());
        }
        resolved.put(providerId, Boolean.TRUE);
    }

    private static String discoveryUri(OAuthClientProperties.Oidc oidc) {
        if (!isBlank(oidc.getDiscoveryUri())) {
            return oidc.getDiscoveryUri();
        }
        if (!isBlank(oidc.getIssuer())) {
            String issuer = oidc.getIssuer();
            if (issuer.endsWith("/")) {
                issuer = issuer.substring(0, issuer.length() - 1);
            }
            return issuer + "/.well-known/openid-configuration";
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
