package com.company.framework.oauthclient.store;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로컬/단일 인스턴스용 state 저장소. 만료는 lazy(소비 시점 체크). 다중 파드에서는 redis 구현을 사용해야 한다.
 */
public class InMemoryOAuthStateStore implements OAuthStateStore {

    private record Entry(String providerId, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void save(String state, String providerId, Duration ttl) {
        store.put(state, new Entry(providerId, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<String> consume(String state) {
        Entry entry = store.remove(state); // 1회용
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(entry.providerId());
    }
}
