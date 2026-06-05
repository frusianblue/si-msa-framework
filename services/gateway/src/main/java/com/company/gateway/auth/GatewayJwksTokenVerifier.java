package com.company.gateway.auth;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Authorization Server(OP)가 발급한 <b>RS256 JWT</b> 를 JWKS(공개키)로 검증한다. {@code services/auth-server} 가 발급하는
 * access/id 토큰을 게이트웨이 엣지에서 받기 위한 경로(이중 발급기 — AUTH_SERVER.md §4).
 *
 * <p>설계상 {@code framework-oauth-client.JwksKeyResolver}(servlet 모듈)와 <b>같은 패턴</b>을 게이트웨이 안에 자립적으로
 * 재현한다(WebFlux 게이트웨이에 servlet/Tomcat 의존을 끌어오지 않기 위함 — 게이트웨이는 jjwt 만 직접 쓴다).
 *
 * <ul>
 *   <li>JWKS 를 {@code jwks-uri} 별로 {kid → Key} 스냅샷으로 캐시(TTL).
 *   <li>알 수 없는 kid 가 오면 <b>1회 강제 재조회</b>(IdP 키 회전 대응) — 단 쿨다운으로 폭주 방지.
 *   <li>kid 가 없고 키가 1개뿐이면 그 키 사용(단일키 JWKS 관용).
 *   <li>서명(RS/ES/PS) + {@code iss}(AS issuer 일치) + {@code exp}(clock-skew) 검증. 자체 JWT 의 jti 블랙리스트와는 무관.
 * </ul>
 *
 * <p><b>블로킹 주의</b>: JWKS 조회는 {@link RestClient}(블로킹 IO)다. WebFlux 이벤트 루프를 막지 않도록, 호출 측
 * ({@code GatewayAuthGlobalFilter})에서 AS 토큰일 때만 {@code boundedElastic} 스케줄러로 오프로드한다. 캐시 적중 시에는
 * 네트워크가 없으므로 비용이 거의 없다.
 */
public class GatewayJwksTokenVerifier {

    private record Snapshot(Map<String, Key> byKid, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /** 가짜 kid 남용으로 인한 JWKS 재조회 폭주를 막기 위한 강제 재조회 최소 간격. */
    private static final Duration FORCED_REFETCH_COOLDOWN = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final String expectedIssuer;
    private final String jwksUri;
    private final String rolesClaim;
    private final long clockSkewSeconds;
    private final Duration cacheTtl;
    private final List<String> expectedAudiences;

    private final ConcurrentHashMap<String, Snapshot> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastForcedRefetch = new ConcurrentHashMap<>();

    public GatewayJwksTokenVerifier(
            RestClient restClient,
            String expectedIssuer,
            String jwksUri,
            String rolesClaim,
            Duration clockSkew,
            Duration cacheTtl,
            List<String> expectedAudiences) {
        this.restClient = restClient;
        this.expectedIssuer = expectedIssuer;
        this.jwksUri = jwksUri;
        this.rolesClaim = (rolesClaim == null || rolesClaim.isBlank()) ? "roles" : rolesClaim;
        this.clockSkewSeconds = (clockSkew == null) ? 60 : Math.max(0, clockSkew.toSeconds());
        this.cacheTtl =
                (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) ? Duration.ofHours(1) : cacheTtl;
        this.expectedAudiences = (expectedAudiences == null) ? List.of() : List.copyOf(expectedAudiences);
    }

    public String expectedIssuer() {
        return expectedIssuer;
    }

    /**
     * AS 토큰을 검증해 신원/권한을 반환. 서명 불일치·만료·iss 불일치·subject 부재는 {@link JwtException}.
     * 반환 타입은 자체 JWT 경로와 동일한 {@link GatewayTokenVerifier.Verified} — 다운스트림 헤더 주입이 동일하게 동작한다.
     */
    public GatewayTokenVerifier.Verified verify(String token) {
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
            throw new JwtException(
                    "AS 토큰 issuer 가 일치하지 않습니다(기대=" + expectedIssuer + ", 실제=" + claims.getIssuer() + ").");
        }
        if (!expectedAudiences.isEmpty()) {
            // 혼동된 대리(confused deputy) 방지 — 다른 RP/리소스용 토큰을 게이트웨이가 받아주지 않도록.
            Set<String> tokenAud = claims.getAudience();
            if (tokenAud == null || expectedAudiences.stream().noneMatch(tokenAud::contains)) {
                throw new JwtException(
                        "AS 토큰 audience 가 허용 목록과 일치하지 않습니다(기대 중 하나=" + expectedAudiences + ", 실제=" + tokenAud + ").");
            }
        }
        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            // user 위임=userId, client_credentials=client_id. 둘 다 sub 로 들어온다.
            throw new JwtException("AS 토큰에 subject(sub) 가 없습니다.");
        }
        return new GatewayTokenVerifier.Verified(userId, claims.getId(), extractRoles(claims));
    }

    private List<String> extractRoles(Claims claims) {
        Object roles = claims.get(rolesClaim);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Key locateKey(Header header) {
        // AS 는 비대칭(RS256) 발급. HS 계열은 자체 JWT 경로로 가야 하므로 여기서는 받지 않는다.
        String alg = header instanceof JwsHeader jws ? jws.getAlgorithm() : null;
        if (alg != null && alg.startsWith("HS")) {
            throw new JwtException("AS 경로는 비대칭 서명만 허용합니다(HS 토큰은 자체 JWT 경로 — alg=" + alg + ").");
        }
        String kid = header instanceof JwsHeader jws ? jws.getKeyId() : null;
        return resolve(kid);
    }

    /** kid(없으면 null)로 공개키 해석. 미발견 시 키 회전 가능성 → 강제 재조회(쿨다운 throttle) 후 1회 더 시도. */
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

    /** JWKS 문서(JSON) 조회. 테스트에서 네트워크 없이 검증하도록 오버라이드 지점으로 분리(JwksKeyResolver 와 동일 규약). */
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
