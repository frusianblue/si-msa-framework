package com.company.framework.security.password;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단일 인스턴스/로컬용. 재시작 시 소멸, MSA 다중 인스턴스 공유 불가.
 * 운영(다중 인스턴스·감사보존)에서는 jdbc 구현 권장.
 */
public class InMemoryPasswordHistoryStore implements PasswordHistoryStore {

    private static final class Entry {
        final Deque<String> hashes = new ArrayDeque<>();
        volatile Instant lastChangedAt;
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void record(String userId, String encodedPassword, int keepCount) {
        store.compute(userId, (k, e) -> {
            if (e == null) e = new Entry();
            e.hashes.addFirst(encodedPassword);
            while (e.hashes.size() > Math.max(1, keepCount)) {
                e.hashes.removeLast();
            }
            e.lastChangedAt = Instant.now();
            return e;
        });
    }

    @Override
    public List<String> recentEncoded(String userId, int count) {
        Entry e = store.get(userId);
        if (e == null) return List.of();
        List<String> out = new ArrayList<>(e.hashes);
        return out.size() > count ? out.subList(0, count) : out;
    }

    @Override
    public Optional<Instant> lastChangedAt(String userId) {
        Entry e = store.get(userId);
        return (e == null || e.lastChangedAt == null) ? Optional.empty() : Optional.of(e.lastChangedAt);
    }
}
