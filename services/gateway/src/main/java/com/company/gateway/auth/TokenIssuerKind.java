package com.company.gateway.auth;

/**
 * 엣지에서 검증한 토큰의 <b>발급자 종류</b>. 검증 경로뿐 아니라 <b>로그아웃/폐기 경계</b>를 가른다(이중 발급기 — AUTH_SERVER.md §4).
 *
 * <ul>
 *   <li>{@link #INTERNAL} — framework-security 가 발급한 <b>자체 JWT</b>(HMAC, iss 없음). jti 블랙리스트(중앙 로그아웃) 대상.
 *   <li>{@link #AUTHORIZATION_SERVER} — {@code services/auth-server}(OP)가 발급한 표준 토큰(RS256, iss=AS issuer).
 *       JWKS 로 검증한다. <b>자체 jti 블랙리스트와 혼용 금지</b> — 폐기는 AS {@code /oauth2/revoke} 경로다.
 * </ul>
 */
public enum TokenIssuerKind {
    INTERNAL,
    AUTHORIZATION_SERVER
}
