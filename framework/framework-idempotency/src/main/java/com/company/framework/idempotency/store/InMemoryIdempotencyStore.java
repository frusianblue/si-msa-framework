package com.company.framework.idempotency.store;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 기본 구현(store.type=memory). 단일 인스턴스 전용 — 다중 인스턴스 운영은 redis 필수. */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Entry(String result, Instant expiresAt) {}

    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    @Override
    public boolean putIfAbsent(String key, Duration ttl) {
        sweep();
        return map.putIfAbsent(key, new Entry(null, Instant.now().plus(ttl))) == null;
    }

    @Override
    public void saveResult(String key, String resultJson, Duration ttl) {
        map.put(key, new Entry(resultJson, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<String> findResult(String key) {
        Entry e = map.get(key);
        if (e == null || e.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.ofNullable(e.result());
    }

    @Override
    public void remove(String key) {
        map.remove(key);
    }

    private void sweep() {
        Instant now = Instant.now();
        map.entrySet().removeIf(en -> en.getValue().expiresAt().isBefore(now));
    }
}
