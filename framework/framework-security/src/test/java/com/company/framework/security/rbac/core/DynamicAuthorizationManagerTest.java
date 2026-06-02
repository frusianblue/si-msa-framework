package com.company.framework.security.rbac.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.security.rbac.domain.Menu;
import com.company.framework.security.rbac.domain.Resource;
import com.company.framework.security.rbac.mapper.SecurityMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * DB 기반 동적 인가 판정 단위 테스트. URL/메서드 매핑·역할 보유 여부·미매핑 정책·미인증 차단을 검증한다.
 * DB 는 고정 매핑을 돌려주는 스텁 {@link SecurityMapper} 로 대체(컨텍스트/실DB 불필요).
 */
class DynamicAuthorizationManagerTest {

    private final DynamicAuthorizationManager manager =
            new DynamicAuthorizationManager(new SecurityMetadataService(new StubSecurityMapper(List.of(
                    resource("/admin/**", "ALL", "ROLE_ADMIN"), resource("/api/orders/**", "POST", "ROLE_MANAGER")))));

    @Test
    @DisplayName("요구 역할 보유 → 허용")
    void grantsWhenUserHasRequiredRole() {
        assertThat(granted("GET", "/admin/users", roles("ROLE_ADMIN"))).isTrue();
    }

    @Test
    @DisplayName("요구 역할 미보유 → 거부")
    void deniesWhenUserLacksRole() {
        assertThat(granted("GET", "/admin/users", roles("ROLE_USER"))).isFalse();
    }

    @Test
    @DisplayName("메서드 불일치 매핑은 적용 안 됨 — POST 전용 규칙은 GET 에 무영향(인증되면 허용)")
    void methodSpecificMappingIgnoredForOtherMethods() {
        assertThat(granted("GET", "/api/orders/1", roles("ROLE_USER"))).isTrue();
    }

    @Test
    @DisplayName("메서드 일치 + 역할 미보유 → 거부")
    void deniesWhenMethodMatchesButRoleMissing() {
        assertThat(granted("POST", "/api/orders", roles("ROLE_USER"))).isFalse();
    }

    @Test
    @DisplayName("미매핑 URL 은 인증된 사용자에게 허용(기본 정책)")
    void allowsUnmappedForAuthenticated() {
        assertThat(granted("GET", "/public/info", roles("ROLE_USER"))).isTrue();
    }

    @Test
    @DisplayName("미인증(null) → 거부")
    void deniesWhenUnauthenticated() {
        assertThat(granted("GET", "/admin/users", null)).isFalse();
    }

    // ---- helpers ----

    private boolean granted(String method, String uri, Authentication auth) {
        RequestAuthorizationContext context = new RequestAuthorizationContext(new MockHttpServletRequest(method, uri));
        return manager.authorize(() -> auth, context).isGranted();
    }

    private static Authentication roles(String... roles) {
        return new UsernamePasswordAuthenticationToken("u", "p", AuthorityUtils.createAuthorityList(roles));
    }

    private static Resource resource(String urlPattern, String httpMethod, String roleName) {
        Resource r = new Resource();
        r.setUrlPattern(urlPattern);
        r.setHttpMethod(httpMethod);
        r.setRoleName(roleName);
        return r;
    }

    /** findAllResources 만 사용하는 고정 매핑 스텁. */
    private record StubSecurityMapper(List<Resource> resources) implements SecurityMapper {
        @Override
        public List<String> findRolesByLoginId(String loginId) {
            return List.of();
        }

        @Override
        public List<Resource> findAllResources() {
            return resources;
        }

        @Override
        public List<Menu> findMenusByRoles(List<String> roles) {
            return List.of();
        }
    }
}
