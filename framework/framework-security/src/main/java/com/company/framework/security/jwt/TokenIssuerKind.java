package com.company.framework.security.jwt;

/**
 * 다운스트림에서 재검증할 토큰의 <b>발급자 종류</b>(무신뢰/zero-trust 재검증 경로 분기).
 *
 * <ul>
 *   <li>{@link #INTERNAL} — {@link JwtProvider} 가 발급한 <b>자체 JWT</b>(HMAC, iss 없음). jti 블랙리스트(중앙 로그아웃) 대상.
 *   <li>{@link #AUTHORIZATION_SERVER} — {@code services/auth-server}(OP)가 발급한 표준 토큰(RS256, iss=AS issuer).
 *       JWKS 로 검증한다. <b>자체 jti 블랙리스트와 혼용 금지</b> — 폐기는 AS {@code /oauth2/revoke} 경로다.
 * </ul>
 *
 * <p>게이트웨이의 동명 enum 과 같은 의미지만, 게이트웨이는 별도 배포 서비스(WebFlux)라 의존하지 않으므로 servlet 측에 따로 둔다.
 */
public enum TokenIssuerKind {
    INTERNAL,
    AUTHORIZATION_SERVER
}
