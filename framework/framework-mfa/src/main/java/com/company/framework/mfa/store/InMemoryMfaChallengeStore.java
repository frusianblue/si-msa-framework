package com.company.framework.mfa.store;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단일 인스턴스/로컬 전용 챌린지 저장소. 만료는 조회 시 lazy 로 처리. 다중 파드에서는 redis 를 써야 한다.
 */
public class InMemoryMfaChallengeStore implements MfaChallengeStore {

    private record Entry(PendingAuth pending, Instant expiresAt) {}

    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    @Override
    public void save(String ticket, PendingAuth pending, Duration ttl) {
        map.put(ticket, new Entry(pending, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<PendingAuth> find(String ticket) {
        Entry e = map.get(ticket);
        if (e == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(e.expiresAt())) {
            map.remove(ticket);
            return Optional.empty();
        }
        return Optional.of(e.pending());
    }

    @Override
    public void remove(String ticket) {
        map.remove(ticket);
    }
}
