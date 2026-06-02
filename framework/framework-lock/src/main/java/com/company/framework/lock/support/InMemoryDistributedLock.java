package com.company.framework.lock.support;

import com.company.framework.lock.DistributedLock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 단일 JVM 락(type=memory, 기본). 개발/단일 인스턴스/테스트용. <b>다중 파드에서는 인스턴스마다 별도 맵이라 상호배제가 되지 않는다</b>
 * — 운영(k8s 다중 replica)에서는 반드시 {@code type=redis} 또는 {@code type=jdbc} 를 쓴다.
 *
 * <p>{@link ConcurrentHashMap#merge}/{@code computeIfPresent} 로 원자적으로 선점/해제한다. 만료된 항목은 다음 선점 시 덮어쓰며,
 * 죽은 키의 능동 청소는 하지 않는다(맵 크기는 사용 키 수에 비례 — 분산 락 키는 보통 소수라 무해).
 */
public class InMemoryDistributedLock implements DistributedLock {

    private record Holder(String token, long expiresAtMillis) {}

    private final ConcurrentMap<String, Holder> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, String token, Duration ttl) {
        long now = System.currentTimeMillis();
        Holder candidate = new Holder(token, now + ttl.toMillis());
        // 살아있는 기존 락이면 보존(incoming 무시), 없거나 만료면 candidate 로 교체. 원자적.
        Holder winner = locks.merge(
                key, candidate, (current, incoming) -> current.expiresAtMillis() > now ? current : incoming);
        return winner.token().equals(token) && winner.expiresAtMillis() == candidate.expiresAtMillis();
    }

    @Override
    public void unlock(String key, String token) {
        locks.computeIfPresent(key, (k, current) -> current.token().equals(token) ? null : current);
    }

    @Override
    public void keepUntil(String key, String token, Duration ttl) {
        long newExpiry = System.currentTimeMillis() + ttl.toMillis();
        locks.computeIfPresent(
                key, (k, current) -> current.token().equals(token) ? new Holder(token, newExpiry) : current);
    }
}
