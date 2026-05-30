package com.company.framework.security.devauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * dev-auth 활성 시 모든 요청에 가짜 로그인 사용자를 주입한다.
 * → 토큰 없이도 @AuthenticationPrincipal/감사필드/hasRole 이 정상 동작해 개발이 안 끊긴다.
 * 권한은 헤더 X-Dev-Roles 로 호출마다 바꿔 권한별 화면을 테스트할 수 있다.
 */
public class DevAuthInjectionFilter extends OncePerRequestFilter {

    private final DevAuthProperties props;

    public DevAuthInjectionFilter(DevAuthProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        List<String> roles = props.getRoles();
        if (props.isAllowHeaderOverride()) {
            String header = request.getHeader("X-Dev-Roles");
            if (header != null && !header.isBlank()) {
                roles = Arrays.stream(header.split(",")).map(String::trim).toList();
            }
        }
        List<GrantedAuthority> authorities = roles.stream()
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .map(a -> (GrantedAuthority) a)
                .toList();
        var principal = new User(props.getUserId(), "", authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "dev", authorities));
        filterChain.doFilter(request, response);
    }
}
