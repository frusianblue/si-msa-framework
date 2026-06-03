package com.company.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;

/**
 * 게이트웨이 엣지에서 access JWT 를 검증한다. framework-security 의 JwtProvider 와 <b>동일한 HMAC-SHA 비밀키</b>로
 * 서명/만료/typ 를 확인한다(서블릿 의존 없이 jjwt 만 사용 — WebFlux 안전, 블로킹 IO 없음).
 */
public class GatewayTokenVerifier {

    private final SecretKey key;
    private final String expectedType;

    public GatewayTokenVerifier(String secret, String expectedType) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expectedType = expectedType;
    }

    /** 검증 성공 시 사용자 식별/권한을 반환. 서명 불일치·만료·타입 불일치는 {@link JwtException}. */
    public Verified verify(String token) {
        Claims claims =
                Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        if (expectedType != null && !expectedType.isBlank()) {
            String typ = claims.get("typ", String.class);
            if (!expectedType.equals(typ)) {
                throw new JwtException("예상치 못한 토큰 타입: " + typ);
            }
        }
        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new JwtException("subject(userId) 가 없습니다.");
        }
        return new Verified(userId, claims.getId(), extractRoles(claims));
    }

    private static List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /** 검증된 신원. jti 는 중앙 로그아웃(블랙리스트) 조회에 사용(토큰에 없으면 null). */
    public record Verified(String userId, String jti, List<String> roles) {}
}
