package com.company.framework.security.jwt;

import com.company.framework.security.token.TokenStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer <token> 검증 후 SecurityContext 에 인증정보 주입.
 * 로그아웃된 토큰(jti 블랙리스트)은 거부한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final TokenStore tokenStore;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, TokenStore tokenStore) {
        this.jwtProvider = jwtProvider;
        this.tokenStore = tokenStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && jwtProvider.validate(token) && !tokenStore.isBlacklisted(jwtProvider.getJti(token))) {
            SecurityContextHolder.getContext().setAuthentication(jwtProvider.getAuthentication(token));
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(HEADER);
        return (bearer != null && bearer.startsWith(PREFIX)) ? bearer.substring(PREFIX.length()) : null;
    }
}
