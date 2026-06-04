package com.company.framework.security.jwt;

import com.company.framework.security.token.TokenStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 다운스트림 인증 필터. {@code framework.security.edge-trust.mode} 로 <b>신뢰 자세(posture)</b>를 가른다
 * (배치 환경별 분기 — TOKEN_VERIFICATION_GUIDE.md §7).
 *
 * <ul>
 *   <li><b>{@code zero-trust}</b>(기본): {@code Authorization: Bearer} 를 <b>로컬 재검증</b>한다. 발급자 분기로
 *       자체 JWT(HMAC) + AS 토큰(RS256/JWKS)을 모두 받는다({@link DownstreamTokenAuthenticator}). 로그아웃된
 *       자체 JWT(jti 블랙리스트)는 거부. 게이트웨이가 주입한 {@code X-User-*} 헤더는 <b>신원 근거로 쓰지 않는다</b>.
 *       VM 등 네트워크 격리가 약한 환경 권장.
 *   <li><b>{@code gateway-headers}</b>(완화): 게이트웨이가 이미 검증해 주입한 {@code X-User-Id}/{@code X-User-Roles}
 *       헤더를 <b>그대로 신뢰</b>해 인증을 구성한다(Bearer 재검증 생략 → AS 토큰도 게이트웨이 검증 결과로 자동 수용).
 *       K8s + NetworkPolicy 처럼 "게이트웨이 우회 불가"가 네트워크로 보장될 때만 안전.
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 신뢰 자세. */
    public enum Mode {
        ZERO_TRUST,
        GATEWAY_HEADERS
    }

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final DownstreamTokenAuthenticator authenticator;
    private final TokenStore tokenStore;
    private final Mode mode;
    private final String userIdHeader;
    private final String rolesHeader;

    public JwtAuthenticationFilter(
            DownstreamTokenAuthenticator authenticator,
            TokenStore tokenStore,
            Mode mode,
            String userIdHeader,
            String rolesHeader) {
        this.authenticator = authenticator;
        this.tokenStore = tokenStore;
        this.mode = (mode == null) ? Mode.ZERO_TRUST : mode;
        this.userIdHeader = userIdHeader;
        this.rolesHeader = rolesHeader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication =
                (mode == Mode.GATEWAY_HEADERS) ? fromGatewayHeaders(request) : fromBearer(request);
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    /** zero-trust: Bearer 를 로컬 재검증(자체 JWT + AS). 자체 JWT 의 jti 블랙리스트만 대조(§4 경계). */
    private Authentication fromBearer(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            return null;
        }
        DownstreamTokenAuthenticator.Authenticated a = authenticator.tryAuthenticate(token);
        if (a == null) {
            return null;
        }
        // 중앙 로그아웃: 자체 JWT(INTERNAL)의 jti 만 블랙리스트 대조. AS 토큰 폐기는 AS /oauth2/revoke (혼용 금지).
        if (a.kind() == TokenIssuerKind.INTERNAL && tokenStore.isBlacklisted(a.jti())) {
            return null;
        }
        return a.authentication();
    }

    /** gateway-headers: 게이트웨이가 검증·주입한 신뢰 헤더로 인증을 구성(Bearer 재검증 생략). */
    private Authentication fromGatewayHeaders(HttpServletRequest request) {
        String userId = (userIdHeader == null) ? null : request.getHeader(userIdHeader);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        String rolesRaw = (rolesHeader == null) ? null : request.getHeader(rolesHeader);
        List<GrantedAuthority> authorities = (rolesRaw == null || rolesRaw.isBlank())
                ? List.of()
                : Arrays.stream(rolesRaw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                        .map(SimpleGrantedAuthority::new)
                        .map(a -> (GrantedAuthority) a)
                        .toList();
        User principal = new User(userId, "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "N/A", authorities);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(HEADER);
        return (bearer != null && bearer.startsWith(PREFIX)) ? bearer.substring(PREFIX.length()) : null;
    }
}
