package com.company.framework.oauthclient.oidc;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * JWKS(jwks_uri)에서 서명 검증용 공개키를 kid 로 해석한다. 키 회전 대응:
 *
 * <ul>
 *   <li>jwks_uri 별로 {kid → Key} 스냅샷을 캐시(TTL).
 *   <li>알 수 없는 kid 가 오면 <b>1회 재조회</b>(IdP 가 키를 회전했을 수 있으므로).
 *   <li>kid 가 없고 키가 1개뿐이면 그 키를 사용(단일키 JWKS 관용).
 * </ul>
 *
 * <p>JWKS 파싱은 jjwt {@link Jwks#setParser()} 로 수행한다(RSA/EC 공개 JWK → {@link Jwk#toKey()}).
 */
public class JwksKeyResolver {

    private record Snapshot(Map<String, Key> byKid, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final RestClient restClient;
    private final Duration cacheTtl;
    private final ConcurrentHashMap<String, Snapshot> cache = new ConcurrentHashMap<>();

    public JwksKeyResolver(RestClient restClient, Duration cacheTtl) {
        this.restClient = restClient;
        this.cacheTtl =
                (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) ? Duration.ofHours(1) : cacheTtl;
    }

    /** kid(없으면 null)로 키를 해석. 회전 대비 1회 재조회. */
    public Key resolve(String jwksUri, String kid) {
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "jwks-uri 가 설정되지 않았습니다(OIDC 검증 불가).");
        }
        Snapshot snapshot = cache.get(jwksUri);
        if (snapshot == null || snapshot.expired()) {
            snapshot = refresh(jwksUri);
        }
        Key key = pick(snapshot, kid);
        if (key == null) {
            // kid 미발견 → 키 회전 가능성: 강제 재조회 후 1회 더 시도.
            snapshot = refresh(jwksUri);
            key = pick(snapshot, kid);
        }
        if (key == null) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "JWKS 에서 서명키를 찾지 못했습니다(kid=" + kid + ").");
        }
        return key;
    }

    private static Key pick(Snapshot snapshot, String kid) {
        if (kid != null && !kid.isBlank()) {
            return snapshot.byKid().get(kid);
        }
        // kid 없음: 단일키면 그 키, 아니면 모호하므로 null.
        return snapshot.byKid().size() == 1
                ? snapshot.byKid().values().iterator().next()
                : null;
    }

    private synchronized Snapshot refresh(String jwksUri) {
        // 동기화 구간 재진입 시 막 갱신된 스냅샷이 있으면 재사용.
        Snapshot existing = cache.get(jwksUri);
        if (existing != null && !existing.expired()) {
            return existing;
        }
        String body = fetchJwksJson(jwksUri);
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "JWKS 응답이 비어 있습니다: " + jwksUri);
        }
        Map<String, Key> byKid = new HashMap<>();
        try {
            JwkSet set = Jwks.setParser().build().parse(body);
            for (Jwk<?> jwk : set.getKeys()) {
                byKid.put(jwk.getId(), jwk.toKey());
            }
        } catch (RuntimeException e) {
            throw new BusinessException(
                    ErrorCode.Common.UNAUTHORIZED, "JWKS 파싱에 실패했습니다(" + jwksUri + "): " + e.getMessage());
        }
        Snapshot snapshot = new Snapshot(Map.copyOf(byKid), Instant.now().plus(cacheTtl));
        cache.put(jwksUri, snapshot);
        return snapshot;
    }

    /** JWKS 문서(JSON)를 가져온다. 테스트에서 네트워크 없이 검증하도록 오버라이드 지점으로 분리. */
    protected String fetchJwksJson(String jwksUri) {
        try {
            return restClient
                    .get()
                    .uri(jwksUri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new BusinessException(
                    ErrorCode.Common.UNAUTHORIZED, "JWKS 조회에 실패했습니다(" + jwksUri + "): " + e.getMessage());
        }
    }
}
