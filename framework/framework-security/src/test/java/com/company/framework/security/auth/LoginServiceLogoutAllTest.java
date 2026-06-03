package com.company.framework.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import com.company.framework.security.concurrent.ConcurrentSessionProperties;
import com.company.framework.security.concurrent.ConcurrentSessionService;
import com.company.framework.security.concurrent.InMemoryConcurrentSessionService;
import com.company.framework.security.jwt.JwtProperties;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.token.InMemoryTokenStore;
import com.company.framework.security.token.TokenStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 전 기기 로그아웃(logoutAll) 단위 테스트. 실제 in-memory TokenStore/ConcurrentSessionService/JwtProvider 로
 * 왕복 검증한다: 사용자의 모든 세션 refresh 제거 + accessJti 블랙리스트 + 레지스트리 해제, 현재 토큰 안전망.
 */
class LoginServiceLogoutAllTest {

    private static final String SECRET = "0123456789-abcdefghij-ABCDEFGHIJ-0123456789";
    private static final String USER = "user-42";

    private final JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, 1800, 1209600));

    private ConcurrentSessionProperties enabledProps() {
        ConcurrentSessionProperties props = new ConcurrentSessionProperties();
        props.setEnabled(true);
        props.setMaxSessions(10); // 테스트에서 강제퇴출 없이 여러 세션 등록
        return props;
    }

    private LoginService loginService(ConcurrentSessionService sessions, ConcurrentSessionProperties props) {
        TokenStore store = new InMemoryTokenStore();
        return new LoginService(null, provider, store, null, null, null, sessions, props, null);
    }

    @Test
    void logoutAll_invalidates_every_registered_session_and_current_token() {
        ConcurrentSessionProperties props = enabledProps();
        InMemoryConcurrentSessionService sessions = new InMemoryConcurrentSessionService(props);
        InMemoryTokenStore store = new InMemoryTokenStore();
        LoginService loginService = new LoginService(null, provider, store, null, null, null, sessions, props, null);

        List<String> jtis = new ArrayList<>();
        List<String> refreshes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String token = provider.createAccessToken(USER, List.of("USER"));
            String jti = provider.getJti(token);
            String refresh = "refresh-" + i;
            store.saveRefresh(refresh, new TokenStore.RefreshEntry(USER, List.of("USER")), provider.refreshTtl());
            sessions.register(new ConcurrentSessionService.ActiveSession(
                    USER, refresh, jti, refresh, System.currentTimeMillis() + i));
            jtis.add(jti);
            refreshes.add(refresh);
        }

        // 레지스트리에 없는 별도 토큰으로 호출(현재 토큰 안전망 검증).
        String current = provider.createAccessToken(USER, List.of("USER"));
        String currentJti = provider.getJti(current);

        int terminated = loginService.logoutAll(current, "10.0.0.1");

        assertThat(terminated).isEqualTo(3);
        for (String jti : jtis) {
            assertThat(store.isBlacklisted(jti)).as("session jti blacklisted").isTrue();
        }
        for (String refresh : refreshes) {
            assertThat(store.findRefresh(refresh)).as("refresh removed").isEmpty();
        }
        assertThat(store.isBlacklisted(currentJti))
                .as("current token blacklisted (safety net)")
                .isTrue();
        assertThat(sessions.activeSessions(USER)).isEmpty();
    }

    @Test
    void logoutAll_without_registry_still_invalidates_current_token() {
        InMemoryTokenStore store = new InMemoryTokenStore();
        LoginService svc = new LoginService(null, provider, store, null, null, null, null, null, null);

        String current = provider.createAccessToken(USER, List.of("USER"));
        String currentJti = provider.getJti(current);

        int terminated = svc.logoutAll(current, "10.0.0.2");

        assertThat(terminated).isZero();
        assertThat(store.isBlacklisted(currentJti)).isTrue();
    }

    @Test
    void logoutAll_rejects_invalid_token() {
        ConcurrentSessionProperties props = enabledProps();
        LoginService loginService = loginService(new InMemoryConcurrentSessionService(props), props);

        assertThatThrownBy(() -> loginService.logoutAll("not-a-jwt", "10.0.0.3"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> loginService.logoutAll(null, "10.0.0.3")).isInstanceOf(BusinessException.class);
    }
}
