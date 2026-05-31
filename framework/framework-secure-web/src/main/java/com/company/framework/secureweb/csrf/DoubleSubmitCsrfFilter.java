package com.company.framework.secureweb.csrf;

import com.company.framework.core.error.ErrorCode;
import com.company.framework.secureweb.config.SecureWebProperties;
import com.company.framework.secureweb.support.PathSupport;
import com.company.framework.secureweb.support.SecureWebResponder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF 더블서브밋 쿠키 방식. Spring Security 의 CSRF 와 독립적으로 동작한다
 * (본 프레임워크의 JWT/stateless 체인은 Spring CSRF 를 비활성화하므로 충돌 없음).
 * <b>기본 off</b> — 순수 Bearer 토큰 API 엔 보통 불필요. 쿠키 기반 인증/폼 엔드포인트 보호용.
 *
 * <p>동작:
 * <ul>
 *   <li>안전 메서드(GET/HEAD/OPTIONS/TRACE): 통과. CSRF 쿠키가 없으면 새로 발급(JS 가 읽어 헤더로 재전송).</li>
 *   <li>비안전 메서드(POST/PUT/PATCH/DELETE): 헤더 토큰과 쿠키 토큰이 일치해야 통과. 아니면 403.</li>
 *   <li>protect-paths 가 지정되면 해당 경로만 보호. 비어 있으면 모든 비안전 메서드 보호. exclude-paths 는 항상 제외.</li>
 * </ul>
 * 쿠키는 JS 가 읽어야 하므로 HttpOnly 를 설정하지 않는다(더블서브밋 전제). Secure/SameSite 는 설정값을 따른다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class DoubleSubmitCsrfFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DoubleSubmitCsrfFilter.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final SecureWebResponder responder;
    private final SecureWebProperties.Csrf cfg;
    private final SecureRandom random = new SecureRandom();

    public DoubleSubmitCsrfFilter(SecureWebResponder responder, SecureWebProperties.Csrf cfg) {
        this.responder = responder;
        this.cfg = cfg;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = PathSupport.relativePath(request);

        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(java.util.Locale.ROOT))) {
            ensureCookie(request, response);
            chain.doFilter(request, response);
            return;
        }

        boolean protectedRequest = !PathSupport.matchesAny(cfg.getExcludePaths(), path)
                && (cfg.getProtectPaths().isEmpty() || PathSupport.matchesAny(cfg.getProtectPaths(), path));

        if (protectedRequest) {
            String cookieToken = readCookie(request, cfg.getCookieName());
            String headerToken = request.getHeader(cfg.getHeaderName());
            if (cookieToken == null || headerToken == null || !constantTimeEquals(cookieToken, headerToken)) {
                log.warn(
                        "[secure-web] csrf rejected path={} method={} ip={}",
                        PathSupport.forLog(path),
                        PathSupport.forLog(request.getMethod()),
                        PathSupport.forLog(request.getRemoteAddr()));
                responder.writeError(response, ErrorCode.Common.FORBIDDEN, "CSRF 토큰이 유효하지 않습니다.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void ensureCookie(HttpServletRequest request, HttpServletResponse response) {
        if (readCookie(request, cfg.getCookieName()) != null || response.isCommitted()) {
            return;
        }
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String token = B64.encodeToString(buf);
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.getCookieName()).append('=').append(token).append("; Path=/");
        if (cfg.getCookieSameSite() != null && !cfg.getCookieSameSite().isBlank()) {
            sb.append("; SameSite=").append(cfg.getCookieSameSite());
        }
        if (cfg.isCookieSecure()) {
            sb.append("; Secure");
        }
        // HttpOnly 미설정: 더블서브밋이므로 JS 가 읽어 헤더로 재전송해야 함.
        response.addHeader("Set-Cookie", sb.toString());
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
