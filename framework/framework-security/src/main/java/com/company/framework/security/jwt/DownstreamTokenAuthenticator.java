package com.company.framework.security.jwt;

import io.jsonwebtoken.io.Decoders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * 다운스트림 무신뢰(zero-trust) 재검증의 <b>발급자 분기(iss routing)</b>. 자체 JWT(HMAC)와 AS 토큰(RS256/JWKS)을
 * 모두 로컬 재검증해 Spring {@link Authentication} 을 만든다(게이트웨이의 {@code GatewayTokenAuthenticator} servlet 판).
 *
 * <ol>
 *   <li>토큰의 {@code iss} 를 <b>검증 전에 엿본다</b>(라우팅 전용). AS 검증기가 설정돼 있고 {@code iss} 가 그 AS issuer 와
 *       같으면 {@link TokenIssuerKind#AUTHORIZATION_SERVER}, 아니면 {@link TokenIssuerKind#INTERNAL}.
 *   <li>분기된 경로가 <b>서명을 끝까지 검증</b>한다 — {@code iss} 위조는 라우팅만 바꿀 뿐 통과시키지 못한다(신뢰 경계 아님).
 * </ol>
 *
 * <p>AS 검증기가 없으면({@code framework.security.resource-server.enabled=false}, 기본) 항상 {@code INTERNAL} —
 * 즉 도입 전 동작과 동일(HMAC only).
 */
public class DownstreamTokenAuthenticator {

    /** 검증 결과 + 블랙리스트 판단 정보(jti·kind). */
    public record Authenticated(Authentication authentication, String jti, TokenIssuerKind kind) {}

    /** 라우팅 전용 iss 추출 정규식(서명 검증 전, 신뢰 경계 아님). */
    private static final Pattern ISS = Pattern.compile("\"iss\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final JwtProvider internalProvider;
    private final ResourceServerJwtVerifier authServerVerifier; // null 이면 AS 경로 비활성

    public DownstreamTokenAuthenticator(JwtProvider internalProvider, ResourceServerJwtVerifier authServerVerifier) {
        this.internalProvider = internalProvider;
        this.authServerVerifier = authServerVerifier;
    }

    /** 발급자 분기 결정(순수 CPU). */
    public TokenIssuerKind kindOf(String token) {
        if (authServerVerifier == null) {
            return TokenIssuerKind.INTERNAL;
        }
        String iss = peekIssuer(token);
        return authServerVerifier.expectedIssuer().equals(iss)
                ? TokenIssuerKind.AUTHORIZATION_SERVER
                : TokenIssuerKind.INTERNAL;
    }

    /**
     * 토큰을 분기 검증해 인증 결과를 반환. 검증 실패(서명/만료/iss/sub 등)·깨진 토큰이면 {@code null}
     * (기존 {@link JwtAuthenticationFilter} 의 관용적 동작과 동일 — 인증 미설정으로 두고 인가 계층이 401 처리).
     */
    public Authenticated tryAuthenticate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            TokenIssuerKind kind = kindOf(token);
            if (kind == TokenIssuerKind.AUTHORIZATION_SERVER) {
                ResourceServerJwtVerifier.Verified v = authServerVerifier.verify(token);
                return new Authenticated(buildAuthentication(v.userId(), v.roles(), token), v.jti(), kind);
            }
            if (!internalProvider.validate(token)) {
                return null;
            }
            return new Authenticated(internalProvider.getAuthentication(token), internalProvider.getJti(token), kind);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Authentication buildAuthentication(String userId, List<String> roles, String token) {
        List<GrantedAuthority> authorities = (roles == null ? List.<String>of() : roles)
                .stream()
                        .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                        .map(SimpleGrantedAuthority::new)
                        .map(a -> (GrantedAuthority) a)
                        .toList();
        User principal = new User(userId, "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    /** JWT payload 의 iss 를 서명 검증 없이 추출(라우팅 전용). 실패/부재 시 null. */
    private static String peekIssuer(String token) {
        try {
            int first = token.indexOf('.');
            int second = token.indexOf('.', first + 1);
            if (first < 0 || second < 0) {
                return null;
            }
            String payload =
                    new String(Decoders.BASE64URL.decode(token.substring(first + 1, second)), StandardCharsets.UTF_8);
            Matcher m = ISS.matcher(payload);
            return m.find() ? m.group(1) : null;
        } catch (RuntimeException e) {
            return null; // 깨진 토큰 → INTERNAL 로 분기되어 HMAC 검증에서 탈락
        }
    }
}
