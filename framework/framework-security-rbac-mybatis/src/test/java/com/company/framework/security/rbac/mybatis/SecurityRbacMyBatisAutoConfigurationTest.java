package com.company.framework.security.rbac.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.framework.security.rbac.domain.Menu;
import com.company.framework.security.rbac.domain.Resource;
import com.company.framework.security.rbac.mapper.SecurityMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * RBAC MyBatis 어댑터 검증.
 *
 * <ul>
 *   <li><b>레지스트레이션 가드</b>: {@link SecurityRbacMyBatisAutoConfiguration} 이 {@code .imports} 에 등록돼
 *       자동활성 경로가 존재함을 보장(framework-lock 의 동일 패턴 — 과거 redis 갭이 그렇게 숨었다).</li>
 *   <li><b>포트 위임</b>: provider 들이 {@link SecurityMapper} 에 정확히 위임함을 검증(순수 단위, Spring 무의존).</li>
 *   <li><b>감사 브리지</b>: {@link SecurityContextCurrentUserProvider} 가 SecurityContext 의 이름을 공급.</li>
 * </ul>
 */
class SecurityRbacMyBatisAutoConfigurationTest {

    @Test
    @DisplayName("SecurityRbacMyBatisAutoConfiguration 이 AutoConfiguration.imports 에 등록돼 있다")
    void autoConfigurationIsRegisteredInImports() throws Exception {
        String path = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        List<String> registered = new ArrayList<>();
        Enumeration<URL> resources = getClass().getClassLoader().getResources(path);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(registered::add);
            }
        }
        assertThat(registered)
                .as(".imports 에 SecurityRbacMyBatisAutoConfiguration 이 등록돼야 자동활성된다")
                .contains(SecurityRbacMyBatisAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("MyBatisResourceMetadataProvider 는 SecurityMapper.findAllResources 에 위임")
    void resourceProviderDelegatesToMapper() {
        SecurityMapper mapper = mock(SecurityMapper.class);
        Resource r = new Resource();
        r.setUrlPattern("/api/**");
        when(mapper.findAllResources()).thenReturn(List.of(r));

        MyBatisResourceMetadataProvider provider = new MyBatisResourceMetadataProvider(mapper);
        List<Resource> result = provider.findAllResources();

        assertThat(result).containsExactly(r);
        verify(mapper).findAllResources();
    }

    @Test
    @DisplayName("MyBatisMenuProvider 는 SecurityMapper.findMenusByRoles 에 위임")
    void menuProviderDelegatesToMapper() {
        SecurityMapper mapper = mock(SecurityMapper.class);
        Menu m = new Menu();
        m.setId(1L);
        List<String> roles = List.of("ROLE_USER");
        when(mapper.findMenusByRoles(roles)).thenReturn(List.of(m));

        MyBatisMenuProvider provider = new MyBatisMenuProvider(mapper);
        List<Menu> result = provider.findMenusByRoles(roles);

        assertThat(result).containsExactly(m);
        verify(mapper).findMenusByRoles(roles);
    }

    @Test
    @DisplayName("SecurityContextCurrentUserProvider: 인증 사용자명을 공급, 미인증/익명은 empty")
    void currentUserProviderReadsSecurityContext() {
        SecurityContextCurrentUserProvider provider = new SecurityContextCurrentUserProvider();
        try {
            // 미인증(SecurityContext 비어 있음) → empty
            SecurityContextHolder.clearContext();
            assertThat(provider.getCurrentUser()).isEmpty();

            // 인증됨 → 이름 공급
            var auth = new UsernamePasswordAuthenticationToken("alice", "n/a", List.of());
            SecurityContextHolder.setContext(new SecurityContextImpl(auth));
            assertThat(provider.getCurrentUser()).contains("alice");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
