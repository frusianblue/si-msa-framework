package com.company.framework.security.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단일 인스턴스/로컬 개발용. 재시작 시 소멸, MSA 다중 인스턴스 공유 불가.
 */
public class InMemoryTokenStore implements TokenStore {

    private record Holder<T>(T value, Instant expireAt) {
        boolean expired() {
            return Instant.now().isAfter(expireAt);
        }
    }

    private final ConcurrentHashMap<String, Holder<RefreshEntry>> refresh = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> blacklist = new ConcurrentHashMap<>();

    @Override
    public void saveRefresh(String refreshToken, RefreshEntry entry, Duration ttl) {
        refresh.put(refreshToken, new Holder<>(entry, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<RefreshEntry> findRefresh(String refreshToken) {
        Holder<RefreshEntry> h = refresh.get(refreshToken);
        if (h == null) return Optional.empty();
        if (h.expired()) {
            refresh.remove(refreshToken);
            return Optional.empty();
        }
        return Optional.of(h.value());
    }

    @Override
    public void removeRefresh(String refreshToken) {
        refresh.remove(refreshToken);
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        blacklist.put(jti, Instant.now().plus(ttl));
    }

    @Override
    public boolean isBlacklisted(String jti) {
        Instant exp = blacklist.get(jti);
        if (exp == null) return false;
        if (Instant.now().isAfter(exp)) {
            blacklist.remove(jti);
            return false;
        }
        return true;
    }
}
