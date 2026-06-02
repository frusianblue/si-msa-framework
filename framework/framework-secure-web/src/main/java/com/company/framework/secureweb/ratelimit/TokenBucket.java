package com.company.framework.secureweb.ratelimit;

/**
 * 지연 보충(lazy refill) 토큰버킷. 호출 시점에 경과 시간만큼 토큰을 채운 뒤 소비를 시도한다.
 * 스케줄러/백그라운드 스레드 없이 동작한다.
 *
 * <p><b>인스턴스 로컬(파드 단위)</b> 한도다. K8s 다중 레플리카에서는 파드 수만큼 한도가 곱해지므로,
 * "전역 한도"가 필요하면 게이트웨이의 Redis 기반 RequestRateLimiter 를 써야 한다. 본 버킷은 단일 파드를
 * 보호하는 보조 안전망 용도다.
 *
 * <p>시간 인자는 호출자가 {@code System.nanoTime()}/{@code System.currentTimeMillis()} 로 주입한다
 * (테스트에서 가상 시계 주입 가능).
 */
final class TokenBucket {

    private final double capacity;
    private final double refillPerNano;
    private double tokens;
    private long lastRefillNanos;
    private volatile long lastAccessMillis;

    TokenBucket(double capacity, double refillPerSecond, long nowNanos, long nowMillis) {
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000.0;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
        this.lastAccessMillis = nowMillis;
    }

    /** {@code n} 토큰 소비 시도. 충분하면 차감 후 true, 부족하면 false(상태 불변). */
    synchronized boolean tryConsume(double n, long nowNanos, long nowMillis) {
        this.lastAccessMillis = nowMillis;
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
            lastRefillNanos = nowNanos;
        }
        if (tokens >= n) {
            tokens -= n;
            return true;
        }
        return false;
    }

    /** 마지막 접근 시각(ms). 유휴 버킷 정리 판단용. */
    long lastAccessMillis() {
        return lastAccessMillis;
    }
}
