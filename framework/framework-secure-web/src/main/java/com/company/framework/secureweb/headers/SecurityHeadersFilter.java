package com.company.framework.secureweb.headers;

import com.company.framework.secureweb.config.SecureWebProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 보안 응답 헤더 부착(보안성 심의·시큐어코딩 공통 항목).
 * 응답 본문이 쓰이기 전(체인 진입 전)에 설정해 다운스트림 에러 응답에도 헤더가 남도록 한다.
 *
 * <p>주의: Spring Security 의 기본 보안헤더 기능을 함께 켜면 일부 헤더가 중복 설정될 수 있다.
 * 둘 중 하나만 사용하길 권장(본 모듈은 JWT/stateless 체인에서 헤더 기능이 꺼져 있는 경우의 표준 보강용).
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final SecureWebProperties.Headers cfg;

    public SecurityHeadersFilter(SecureWebProperties.Headers cfg) {
        this.cfg = cfg;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        applyHeaders(request, response);
        chain.doFilter(request, response);
    }

    private void applyHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (StringUtils.hasText(cfg.getFrameOptions())) {
            response.setHeader("X-Frame-Options", cfg.getFrameOptions());
        }
        if (cfg.isContentTypeOptions()) {
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        if (StringUtils.hasText(cfg.getReferrerPolicy())) {
            response.setHeader("Referrer-Policy", cfg.getReferrerPolicy());
        }
        if (StringUtils.hasText(cfg.getContentSecurityPolicy())) {
            response.setHeader("Content-Security-Policy", cfg.getContentSecurityPolicy());
        }
        if (StringUtils.hasText(cfg.getPermissionsPolicy())) {
            response.setHeader("Permissions-Policy", cfg.getPermissionsPolicy());
        }
        SecureWebProperties.Headers.Hsts hsts = cfg.getHsts();
        if (hsts.isEnabled() && request.isSecure()) {
            StringBuilder sb = new StringBuilder("max-age=").append(hsts.getMaxAgeSeconds());
            if (hsts.isIncludeSubDomains()) {
                sb.append("; includeSubDomains");
            }
            if (hsts.isPreload()) {
                sb.append("; preload");
            }
            response.setHeader("Strict-Transport-Security", sb.toString());
        }
    }
}
