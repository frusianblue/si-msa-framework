package com.company.framework.oauthclient.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import com.company.framework.oauthclient.config.OAuthClientProperties;
import com.company.framework.oauthclient.store.InMemoryOAuthStateStore;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 순수 로직(외부 의존 0) 검증: 프리셋·중첩 속성 추출·인가 URL·state 1회 소비. */
class ProviderRegistryTest {

    private OAuthClientProperties propsWith(String id, OAuthClientProperties.Provider p) {
        OAuthClientProperties props = new OAuthClientProperties();
        props.setBaseRedirectUri("http://localhost:8080/");
        props.getProviders().put(id, p);
        props.applyPresets();
        return props;
    }

    @Test
    void google_preset_fills_endpoints_and_builds_authorize_url() {
        OAuthClientProperties.Provider g = new OAuthClientProperties.Provider();
        g.setClientId("gid");
        g.setClientSecret("gsecret");
        OAuthClientProperties props = propsWith("google", g);
        ProviderRegistry registry = new ProviderRegistry(props);

        OAuthClientProperties.Provider resolved = registry.require("google");
        assertThat(resolved.getTokenUri()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(resolved.getUserNameAttribute()).isEqualTo("sub");

        String url = registry.authorizationUrl("google", resolved, "ST/ATE+1");
        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?response_type=code");
        assertThat(url).contains("client_id=gid");
        assertThat(url).contains("state=ST%2FATE%2B1"); // URL 인코딩
        assertThat(url)
                .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fv1%2Fauth%2Foauth%2Fgoogle%2Fcallback");
    }

    @Test
    void kakao_nested_userinfo_is_normalized() {
        OAuthClientProperties.Provider k = new OAuthClientProperties.Provider();
        k.setClientId("kid");
        OAuthClientProperties props = propsWith("kakao", k);
        ProviderRegistry registry = new ProviderRegistry(props);

        Map<String, Object> raw = Map.of(
                "id", 4242L, "kakao_account", Map.of("email", "u@kakao.com", "profile", Map.of("nickname", "홍길동")));
        OAuthUserInfo info = registry.toUserInfo("kakao", registry.require("kakao"), raw);

        assertThat(info.providerId()).isEqualTo("4242");
        assertThat(info.email()).isEqualTo("u@kakao.com");
        assertThat(info.name()).isEqualTo("홍길동");
        assertThat(info.attributes()).isSameAs(raw);
    }

    @Test
    void missing_provider_id_in_response_is_rejected() {
        OAuthClientProperties.Provider n = new OAuthClientProperties.Provider();
        n.setClientId("nid");
        OAuthClientProperties props = propsWith("naver", n);
        ProviderRegistry registry = new ProviderRegistry(props);

        Map<String, Object> raw = Map.of("response", Map.of("email", "x@naver.com"));
        assertThatThrownBy(() -> registry.toUserInfo("naver", registry.require("naver"), raw))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unknown_provider_is_rejected() {
        ProviderRegistry registry = new ProviderRegistry(new OAuthClientProperties());
        assertThatThrownBy(() -> registry.require("unknown")).isInstanceOf(BusinessException.class);
    }

    @Test
    void state_is_consumed_exactly_once() {
        InMemoryOAuthStateStore store = new InMemoryOAuthStateStore();
        store.save("s1", "google", Duration.ofMinutes(5));
        assertThat(store.consume("s1")).contains("google");
        assertThat(store.consume("s1")).isEmpty(); // 재사용 차단
    }

    @Test
    void expired_state_is_not_accepted() {
        InMemoryOAuthStateStore store = new InMemoryOAuthStateStore();
        store.save("s2", "kakao", Duration.ofMillis(-1)); // 이미 만료
        assertThat(store.consume("s2")).isEmpty();
    }
}
