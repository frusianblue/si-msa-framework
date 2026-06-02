package com.company.framework.secureweb.ratelimit;

import com.company.framework.secureweb.config.SecureWebProperties;
import com.company.framework.secureweb.support.PathSupport;
import com.company.framework.secureweb.support.SecureWebResponder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 인스턴스 로컬(파드 단위) 토큰버킷 레이트리밋 필터. 키(주체 또는 IP)별 버킷을 메모리에 두고,
 * 토큰 부족 시 표준 {@code ApiResponse.fail} 형태의 429 를 직접 기록한다(필터 계층 → GlobalExceptionHandler 미적용).
 *
 * <p><b>전역 한도가 아니다.</b> 다중 레플리카 환경의 전역 레이트리밋은 게이트웨이의 Redis 기반
 * RequestRateLimiter 가 담당하고, 이 필터는 단일 파드를 보호하는 보조 방어선이다.
 *
 * <p>순서: CORS 필터(HIGHEST_PRECEDENCE) 다음, 본 필터({@code HIGHEST_PRECEDENCE + 1})에서
 * 가능한 한 앞단에서 거부한다. CORS 프리플라이트(OPTIONS)는 CORS 필터가 먼저 처리하므로 카운트하지 않도록
 * OPTIONS 는 스킵한다.
 *
 * <p>메모리 가드: 버킷 수가 {@code maxEntries} 를 넘으면 유휴 버킷을 정리하고, 그래도 넘치면 새 키는
 * <b>fail-open</b>(허용)으로 처리해 메모리 폭주로 파드가 죽지 않게 한다(보조 방어선이므로 가용성 우선).
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String ANONYMOUS = "anon";

    private final SecureWebResponder responder;
    private final SecureWebProperties.RateLimit cfg;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong opCounter = new AtomicLong();
    private volatile long lastSweepMillis = 0L;

    public RateLimitFilter(SecureWebResponder responder, SecureWebProperties.RateLimit cfg) {
        this.responder = responder;
        this.cfg = cfg;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 프리플라이트는 CORS 필터가 처리 → 레이트리밋 대상에서 제외
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = PathSupport.relativePath(request);
        if (!applies(path)) {
            chain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);
        TokenBucket bucket = acquireBucket(key);
        long now = System.nanoTime();
        long nowMs = System.currentTimeMillis();

        // bucket == null → 메모리 가드 발동(가득 참). fail-open 으로 통과시키되 경고.
        if (bucket == null || bucket.tryConsume(cfg.getRequestedTokens(), now, nowMs)) {
            chain.doFilter(request, response);
            return;
        }

        if (cfg.isIncludeRetryAfter() && !response.isCommitted()) {
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds()));
        }
        if (log.isDebugEnabled()) {
            log.debug("rate limit exceeded key={} path={}", PathSupport.forLog(key), PathSupport.forLog(path));
        }
        responder.writeError(response, RateLimitErrorCode.RATE_LIMITED, RateLimitErrorCode.RATE_LIMITED.message());
    }

    private boolean applies(String path) {
        if (PathSupport.matchesAny(cfg.getExcludePaths(), path)) {
            return false;
        }
        if (cfg.getIncludePaths() != null && !cfg.getIncludePaths().isEmpty()) {
            return PathSupport.matchesAny(cfg.getIncludePaths(), path);
        }
        return true;
    }

    /** 버킷 확보. 메모리 가드 초과 시 유휴 정리 후에도 넘치면 null(=fail-open) 반환. */
    private TokenBucket acquireBucket(String key) {
        TokenBucket existing = buckets.get(key);
        if (existing != null) {
            return existing;
        }
        maybeSweep();
        if (buckets.size() >= cfg.getMaxEntries() && !buckets.containsKey(key)) {
            sweepIdle();
            if (buckets.size() >= cfg.getMaxEntries() && !buckets.containsKey(key)) {
                log.warn("rate limit bucket map saturated (size={}); failing open for new key", buckets.size());
                return null;
            }
        }
        long now = System.nanoTime();
        long nowMs = System.currentTimeMillis();
        return buckets.computeIfAbsent(
                key, k -> new TokenBucket(cfg.getCapacity(), cfg.getRefillPerSecond(), now, nowMs));
    }

    /** 주기적(매 2048 요청 또는 60초)으로 유휴 버킷 정리. */
    private void maybeSweep() {
        long ops = opCounter.incrementAndGet();
        long nowMs = System.currentTimeMillis();
        if ((ops & 0x7FF) == 0 || nowMs - lastSweepMillis > 60_000L) {
            sweepIdle();
        }
    }

    private void sweepIdle() {
        long nowMs = System.currentTimeMillis();
        lastSweepMillis = nowMs;
        long idleCutoff = cfg.getIdleEvictionSeconds() * 1000L;
        Iterator<Map.Entry<String, TokenBucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TokenBucket> e = it.next();
            if (nowMs - e.getValue().lastAccessMillis() > idleCutoff) {
                it.remove();
            }
        }
    }

    private long retryAfterSeconds() {
        double rps = cfg.getRefillPerSecond();
        if (rps <= 0) {
            return 1L;
        }
        long secs = (long) Math.ceil(cfg.getRequestedTokens() / rps);
        return Math.max(1L, secs);
    }

    private String resolveKey(HttpServletRequest request) {
        SecureWebProperties.RateLimit.KeyStrategy strategy = cfg.getKeyStrategy();
        if (strategy == SecureWebProperties.RateLimit.KeyStrategy.IP) {
            return "ip:" + clientIp(request);
        }
        String principal = principalName(request);
        if (strategy == SecureWebProperties.RateLimit.KeyStrategy.PRINCIPAL) {
            return "u:" + (principal != null ? principal : ANONYMOUS);
        }
        // PRINCIPAL_OR_IP
        return principal != null ? "u:" + principal : "ip:" + clientIp(request);
    }

    private String principalName(HttpServletRequest request) {
        Principal p = request.getUserPrincipal();
        if (p != null && p.getName() != null && !p.getName().isBlank()) {
            return p.getName();
        }
        return null;
    }

    /** trustForwardedFor 일 때만 X-Forwarded-For 첫 홉을 신뢰. 아니면 remoteAddr. */
    private String clientIp(HttpServletRequest request) {
        if (cfg.isTrustForwardedFor()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        String addr = request.getRemoteAddr();
        return addr != null ? addr : "unknown";
    }
}
