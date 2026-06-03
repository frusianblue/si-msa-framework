package com.company.framework.oauthclient.oidc;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.oauthclient.config.OAuthClientProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * OIDC id_token 검증기. 서명(JWKS 의 RSA/EC, 또는 HS 계열은 client-secret HMAC) + 표준 클레임을 확인한다.
 *
 * <ol>
 *   <li>서명: 헤더 alg 가 HS* 면 client-secret 으로 HMAC 검증, 그 외(RS/ES/PS)는 JWKS 에서 kid 로 공개키 해석.
 *   <li>exp/nbf: jjwt 가 clock-skew 허용범위로 검증.
 *   <li>iss: 설정된 issuer 와 일치.
 *   <li>aud: client-id 포함.
 *   <li>nonce: authorize 때 발급해 저장한 값과 일치(nonce 사용 시).
 * </ol>
 *
 * <p>검증 통과한 클레임 전체를 {@code Map} 으로 반환한다(상위에서 user-name/email/name attribute 로 정규화).
 */
public class IdTokenVerifier {

    private final JwksKeyResolver jwks;

    public IdTokenVerifier(JwksKeyResolver jwks) {
        this.jwks = jwks;
    }

    public Map<String, Object> verify(OAuthClientProperties.Provider provider, String idToken, String expectedNonce) {
        OAuthClientProperties.Oidc oidc = provider.getOidc();
        long skewSeconds = oidc.getClockSkew() == null
                ? 60
                : Math.max(0, oidc.getClockSkew().toSeconds());

        Locator<Key> keyLocator = header -> locateKey(header, oidc.getJwksUri(), provider.getClientSecret());

        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(keyLocator)
                    .clockSkewSeconds(skewSeconds)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "id_token 검증에 실패했습니다: " + e.getMessage());
        }

        // issuer
        if (!isBlank(oidc.getIssuer()) && !oidc.getIssuer().equals(claims.getIssuer())) {
            throw new BusinessException(
                    ErrorCode.Common.UNAUTHORIZED, "id_token issuer 가 일치하지 않습니다(기대=" + oidc.getIssuer() + ").");
        }
        // audience ⊇ client-id
        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(provider.getClientId())) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "id_token audience 에 client-id 가 없습니다.");
        }
        // nonce
        if (oidc.isNonce()) {
            String nonce = claims.get("nonce", String.class);
            if (expectedNonce == null || !expectedNonce.equals(nonce)) {
                throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "id_token nonce 가 일치하지 않습니다(재생/위조 차단).");
            }
        }
        // subject 필수
        if (isBlank(claims.getSubject())) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "id_token 에 subject(sub) 가 없습니다.");
        }
        return new LinkedHashMap<>(claims);
    }

    private Key locateKey(Header header, String jwksUri, String clientSecret) {
        String alg = header instanceof JwsHeader jws ? jws.getAlgorithm() : null;
        if (alg != null && alg.startsWith("HS")) {
            if (isBlank(clientSecret)) {
                throw new BusinessException(
                        ErrorCode.Common.UNAUTHORIZED, "HS 서명 id_token 검증에는 client-secret 이 필요합니다.");
            }
            return Keys.hmacShaKeyFor(clientSecret.getBytes(StandardCharsets.UTF_8));
        }
        String kid = header instanceof JwsHeader jws ? jws.getKeyId() : null;
        return jwks.resolve(jwksUri, kid);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
