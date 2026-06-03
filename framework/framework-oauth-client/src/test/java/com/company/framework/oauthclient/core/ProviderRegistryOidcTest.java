package com.company.framework.oauthclient.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import com.company.framework.oauthclient.config.OAuthClientProperties;
import org.junit.jupiter.api.Test;

/** OIDC 공급자에 대한 순수 로직 검증(외부 의존 0): require 완화, nonce/openid 포함 인가 URL. */
class ProviderRegistryOidcTest {

    private ProviderRegistry registryWith(String id, OAuthClientProperties.Provider p) {
        OAuthClientProperties props = new OAuthClientProperties();
        props.setBaseRedirectUri("http://localhost:8080");
        props.getProviders().put(id, p);
        props.applyPresets();
        return new ProviderRegistry(props);
    }

    private OAuthClientProperties.Provider oidcExplicit() {
        OAuthClientProperties.Provider p = new OAuthClientProperties.Provider();
        p.setClientId("cid");
        p.setClientSecret("csecret");
        p.getOidc().setEnabled(true);
        p.setAuthorizationUri("https://idp.example.com/authorize");
        p.setTokenUri("https://idp.example.com/token");
        p.getOidc().setJwksUri("https://idp.example.com/jwks");
        p.getOidc().setIssuer("https://idp.example.com");
        return p;
    }

    @Test
    void oidc_with_explicit_endpoints_passes_require_without_userinfo() {
        ProviderRegistry registry = registryWith("idp", oidcExplicit());
        OAuthClientProperties.Provider resolved = registry.require("idp");
        // OIDC 기본 attribute = sub/email/name
        assertThat(resolved.getUserNameAttribute()).isEqualTo("sub");
        assertThat(resolved.getEmailAttribute()).isEqualTo("email");
    }

    @Test
    void oidc_with_only_issuer_passes_require_endpoints_deferred_to_discovery() {
        OAuthClientProperties.Provider p = new OAuthClientProperties.Provider();
        p.setClientId("cid");
        p.getOidc().setEnabled(true);
        p.getOidc().setIssuer("https://idp.example.com");
        ProviderRegistry registry = registryWith("idp", p);
        // authorizationUri 등이 아직 비어 있어도 discovery 출처(issuer)가 있으므로 통과
        assertThat(registry.require("idp")).isNotNull();
    }

    @Test
    void oidc_without_issuer_discovery_or_explicit_endpoints_is_rejected() {
        OAuthClientProperties.Provider p = new OAuthClientProperties.Provider();
        p.setClientId("cid");
        p.getOidc().setEnabled(true); // 출처/엔드포인트 전무
        ProviderRegistry registry = registryWith("idp", p);
        assertThatThrownBy(() -> registry.require("idp")).isInstanceOf(BusinessException.class);
    }

    @Test
    void authorize_url_adds_nonce_and_forces_openid_scope() {
        OAuthClientProperties.Provider p = oidcExplicit();
        p.setScope(new java.util.ArrayList<>(java.util.List.of("email", "profile"))); // openid 누락
        ProviderRegistry registry = registryWith("idp", p);
        OAuthClientProperties.Provider resolved = registry.require("idp");

        String url = registry.authorizationUrl("idp", resolved, "STATE1", "NONCE1");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("nonce=NONCE1");
        // scope 에 openid 가 강제 포함되어야 한다(공백은 URL 인코딩 +/%20)
        assertThat(url).containsPattern("scope=openid(\\+|%20)email(\\+|%20)profile");
    }

    @Test
    void non_oidc_authorize_url_has_no_nonce() {
        OAuthClientProperties.Provider g = new OAuthClientProperties.Provider();
        g.setClientId("gid");
        ProviderRegistry registry = registryWith("google", g);
        OAuthClientProperties.Provider resolved = registry.require("google");

        String url = registry.authorizationUrl("google", resolved, "STATE1");
        assertThat(url).doesNotContain("nonce=");
    }
}
