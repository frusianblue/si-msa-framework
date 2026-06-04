package com.company.framework.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Authorization Server(OP)가 발급한 <b>RS256 JWT</b> 를 JWKS(공개키)로 검증하는 <b>리소스 서버 검증기</b>.
 * 다운스트림 무신뢰(zero-trust) 재검증에서 AS 토큰을 받기 위한 경로(이중 발급기 — AUTH_SERVER.md §4,
 * TOKEN_VERIFICATION_GUIDE.md §6.4).
 *
 * <p>게이트웨이의 {@code GatewayJwksTokenVerifier} 와 같은 패턴(servlet 버전)이다. {@code framework-oauth-client}
 * 의 {@code JwksKeyResolver} 와도 동일 의미지만, oauth-client 는 <b>로그인(RP)</b> 모듈이라 리소스 서버 검증을 위해
 * 끌어오지 않고 jjwt(이미 보유) + {@link RestClient} 만으로 자립 구현한다.
 *
 * <ul>
 *   <li>JWKS 를 {kid → Key} 스냅샷으로 캐시(TTL).
 *   <li>알 수 없는 kid → <b>1회 강제 재조회</b>(키 회전 대응) + 쿨다운(폭주 방지).
 *   <li>kid 없고 키 1개면 그 키 사용(단일키 관용).
 *   <li>서명(비대칭) + {@code iss}(AS issuer 일치) + (선택){@code aud} + {@code sub} 검증.
 * </ul>
 *
 * <p>HMAC(HS*) 서명은 거부한다 — 자체 JWT 경로({@link JwtProvider})로 가야 한다.
 */
public class ResourceServerJwtVerifier {

    /** 검증 결과(자체 JWT 경로와 동일 신원 형태). */
    public record Verified(String userId, String jti, List<String> roles) {}

    private record Snapshot(Map<String, Key> byKid, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /** 가짜 kid 남용으로 인한 JWKS 재조회 폭주 방지용 강제 재조회 최소 간격. */
    private static final Duration FORCED_REFETCH_COOLDOWN = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final String expectedIssuer;
    private final String jwksUri;
    private final String rolesClaim;
    private final String expectedAudience; // null/blank 이면 aud 검증 생략
    private final long clockSkewSeconds;
    private final Duration cacheTtl;

    private final ConcurrentHashMap<String, Snapshot> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastForcedRefetch = new ConcurrentHashMap<>();

    public ResourceServerJwtVerifier(
            RestClient restClient,
            String expectedIssuer,
            String jwksUri,
            String rolesClaim,
            String expectedAudience,
            Duration clockSkew,
            Duration cacheTtl) {
        this.restClient = restClient;
        this.expectedIssuer = expectedIssuer;
        this.jwksUri = jwksUri;
        this.rolesClaim = (rolesClaim == null || rolesClaim.isBlank()) ? "roles" : rolesClaim;
        this.expectedAudience = (expectedAudience == null || expectedAudience.isBlank()) ? null : expectedAudience;
        this.clockSkewSeconds = (clockSkew == null) ? 60 : Math.max(0, clockSkew.toSeconds());
        this.cacheTtl =
                (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) ? Duration.ofHours(1) : cacheTtl;
    }

    public String expectedIssuer() {
        return expectedIssuer;
    }

    /** AS 토큰 검증. 서명 불일치·만료·iss/aud 불일치·subject 부재는 {@link JwtException}. */
    public Verified verify(String token) {
        Locator<Key> keyLocator = this::locateKey;
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(keyLocator)
                    .clockSkewSeconds(clockSkewSeconds)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (RuntimeException e) {
            throw new JwtException("AS 토큰 검증에 실패했습니다: " + e.getMessage(), e);
        }
        if (expectedIssuer != null && !expectedIssuer.equals(claims.getIssuer())) {
            throw new JwtException("AS 토큰 issuer 불일치(기대=" + expectedIssuer + ", 실제=" + claims.getIssuer() + ").");
        }
        if (expectedAudience != null) {
            var aud = claims.getAudience();
            if (aud == null || !aud.contains(expectedAudience)) {
                throw new JwtException("AS 토큰 audience 불일치(기대=" + expectedAudience + ").");
            }
        }
        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new JwtException("AS 토큰에 subject(sub) 가 없습니다.");
        }
        return new Verified(userId, claims.getId(), extractRoles(claims));
    }

    private List<String> extractRoles(Claims claims) {
        Object roles = claims.get(rolesClaim);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Key locateKey(Header header) {
        String alg = header instanceof JwsHeader jws ? jws.getAlgorithm() : null;
        if (alg != null && alg.startsWith("HS")) {
            throw new JwtException("리소스 서버 AS 경로는 비대칭 서명만 허용합니다(HS 는 자체 JWT 경로 — alg=" + alg + ").");
        }
        String kid = header instanceof JwsHeader jws ? jws.getKeyId() : null;
        return resolve(kid);
    }

    private Key resolve(String kid) {
        Snapshot snapshot = cache.get(jwksUri);
        if (snapshot == null || snapshot.expired()) {
            snapshot = lazyRefresh();
        }
        Key key = pick(snapshot, kid);
        if (key == null) {
            Snapshot refreshed = forcedRefresh();
            if (refreshed != null) {
                key = pick(refreshed, kid);
            }
        }
        if (key == null) {
            throw new JwtException("JWKS 에서 서명키를 찾지 못했습니다(kid=" + kid + ").");
        }
        return key;
    }

    private static Key pick(Snapshot snapshot, String kid) {
        if (snapshot == null) {
            return null;
        }
        if (kid != null && !kid.isBlank()) {
            return snapshot.byKid().get(kid);
        }
        return snapshot.byKid().size() == 1
                ? snapshot.byKid().values().iterator().next()
                : null;
    }

    private synchronized Snapshot lazyRefresh() {
        Snapshot existing = cache.get(jwksUri);
        if (existing != null && !existing.expired()) {
            return existing;
        }
        return doFetch();
    }

    private synchronized Snapshot forcedRefresh() {
        Instant last = lastForcedRefetch.getOrDefault(jwksUri, Instant.EPOCH);
        if (Instant.now().isBefore(last.plus(FORCED_REFETCH_COOLDOWN))) {
            return cache.get(jwksUri);
        }
        lastForcedRefetch.put(jwksUri, Instant.now());
        return doFetch();
    }

    private Snapshot doFetch() {
        String body = fetchJwksJson(jwksUri);
        if (body == null || body.isBlank()) {
            throw new JwtException("JWKS 응답이 비어 있습니다: " + jwksUri);
        }
        Map<String, Key> byKid = new HashMap<>();
        try {
            JwkSet set = Jwks.setParser().build().parse(body);
            for (Jwk<?> jwk : set.getKeys()) {
                byKid.put(jwk.getId(), jwk.toKey());
            }
        } catch (RuntimeException e) {
            throw new JwtException("JWKS 파싱에 실패했습니다(" + jwksUri + "): " + e.getMessage(), e);
        }
        Snapshot snapshot = new Snapshot(Map.copyOf(byKid), Instant.now().plus(cacheTtl));
        cache.put(jwksUri, snapshot);
        return snapshot;
    }

    /** JWKS 문서(JSON) 조회. 테스트에서 네트워크 없이 검증하도록 오버라이드 지점으로 분리. */
    protected String fetchJwksJson(String uri) {
        try {
            return restClient
                    .get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new JwtException("JWKS 조회에 실패했습니다(" + uri + "): " + e.getMessage(), e);
        }
    }
}
