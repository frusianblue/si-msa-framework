package com.company.gateway.auth;

import io.jsonwebtoken.io.Decoders;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 엣지 토큰 인증의 <b>발급자 분기(iss routing)</b>. 자체 JWT(HMAC)와 AS 토큰(RS256/JWKS)을 모두 받기 위한 라우터.
 *
 * <ol>
 *   <li>토큰의 {@code iss} 를 <b>검증 전에 엿본다</b>(라우팅 전용). AS 검증기가 설정돼 있고 {@code iss} 가 그 AS issuer 와
 *       같으면 {@link TokenIssuerKind#AUTHORIZATION_SERVER}, 아니면 {@link TokenIssuerKind#INTERNAL}.
 *   <li>분기된 경로가 <b>서명을 끝까지 검증</b>한다. 따라서 {@code iss} 를 위조해도 라우팅만 바뀔 뿐 위조 토큰이
 *       통과하지는 못한다(잘못 분기되면 해당 경로의 서명 검증에서 탈락). → iss 엿보기는 신뢰 경계가 아니라 분기 힌트.
 * </ol>
 *
 * <p>AS 검증기가 없으면(={@code gateway.auth.authorization-server.enabled=false}, 기본) 항상 {@code INTERNAL} 로
 * 동작 — 즉 도입 전 동작과 100% 동일(HMAC only).
 */
public class GatewayTokenAuthenticator {

    /** 라우팅 전용 iss 추출 정규식(서명 검증 전, 신뢰 경계 아님). payload JSON 에서 {@code "iss":"..."} 문자열 값만 본다. */
    private static final Pattern ISS = Pattern.compile("\"iss\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final GatewayTokenVerifier internalVerifier;
    private final GatewayJwksTokenVerifier authServerVerifier; // null 이면 AS 경로 비활성

    public GatewayTokenAuthenticator(
            GatewayTokenVerifier internalVerifier, GatewayJwksTokenVerifier authServerVerifier) {
        this.internalVerifier = internalVerifier;
        this.authServerVerifier = authServerVerifier;
    }

    /** 발급자 분기 결정(순수 CPU, IO 없음 → 이벤트 루프에서 안전하게 호출 가능). */
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
     * 분기된 경로로 검증. {@link TokenIssuerKind#AUTHORIZATION_SERVER} 경로는 JWKS 조회(블로킹 IO)가 일어날 수 있으므로,
     * 호출 측에서 {@code boundedElastic} 스케줄러로 감싸 이벤트 루프를 막지 않도록 한다.
     */
    public GatewayTokenVerifier.Verified verify(String token, TokenIssuerKind kind) {
        if (kind == TokenIssuerKind.AUTHORIZATION_SERVER) {
            return authServerVerifier.verify(token);
        }
        return internalVerifier.verify(token);
    }

    /** JWT payload 의 iss 클레임을 서명 검증 없이 추출(라우팅 전용). 추출 실패/부재 시 null. */
    private static String peekIssuer(String token) {
        try {
            int first = token.indexOf('.');
            int second = token.indexOf('.', first + 1);
            if (first < 0 || second < 0) {
                return null;
            }
            String payloadB64 = token.substring(first + 1, second);
            String payload = new String(Decoders.BASE64URL.decode(payloadB64), StandardCharsets.UTF_8);
            Matcher m = ISS.matcher(payload);
            return m.find() ? m.group(1) : null;
        } catch (RuntimeException e) {
            return null; // 깨진 토큰 → INTERNAL 로 분기되어 HMAC 검증에서 정상적으로 탈락
        }
    }
}
