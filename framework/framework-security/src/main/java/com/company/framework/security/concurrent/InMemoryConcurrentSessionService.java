package com.company.framework.security.concurrent;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단일 인스턴스/로컬용 동시 세션 제어. 재시작 시 소멸, MSA 다중 인스턴스 공유 불가.
 * 다중 인스턴스 운영에서는 jdbc(또는 추후 redis) 구현으로 교체 권장.
 */
public class InMemoryConcurrentSessionService implements ConcurrentSessionService {

    private final ConcurrentSessionProperties props;
    // userId -> (sessionId -> ActiveSession)
    private final ConcurrentHashMap<String, Map<String, ActiveSession>> sessions = new ConcurrentHashMap<>();

    public InMemoryConcurrentSessionService(ConcurrentSessionProperties props) {
        this.props = props;
    }

    @Override
    public synchronized List<ActiveSession> register(ActiveSession session) {
        Map<String, ActiveSession> userSessions =
                sessions.computeIfAbsent(session.userId(), k -> new ConcurrentHashMap<>());
        int max = Math.max(1, props.getMaxSessions());

        List<ActiveSession> evicted = new ArrayList<>();
        if (userSessions.size() >= max) {
            if (props.getStrategy() == ConcurrentSessionProperties.Strategy.REJECT) {
                throw new BusinessException(
                        ErrorCode.Common.CONFLICT, "이미 다른 기기/브라우저에서 로그인되어 있습니다. 기존 세션을 로그아웃한 뒤 다시 시도하세요.");
            }
            // EVICT_OLDEST: 신규 1건이 들어갈 자리를 확보하도록 가장 오래된 (size - max + 1)건 제거
            List<ActiveSession> ordered = new ArrayList<>(userSessions.values());
            ordered.sort(Comparator.comparingLong(ActiveSession::issuedAtEpochMs));
            int toEvict = userSessions.size() - max + 1;
            for (int i = 0; i < toEvict && i < ordered.size(); i++) {
                ActiveSession old = ordered.get(i);
                userSessions.remove(old.sessionId());
                evicted.add(old);
            }
        }
        userSessions.put(session.sessionId(), session);
        return evicted;
    }

    @Override
    public void unregister(String sessionId) {
        if (sessionId == null) return;
        for (Map<String, ActiveSession> userSessions : sessions.values()) {
            if (userSessions.remove(sessionId) != null) return;
        }
    }

    @Override
    public List<ActiveSession> activeSessions(String userId) {
        Map<String, ActiveSession> userSessions = sessions.get(userId);
        if (userSessions == null) return List.of();
        List<ActiveSession> out = new ArrayList<>(userSessions.values());
        out.sort(Comparator.comparingLong(ActiveSession::issuedAtEpochMs).reversed());
        return out;
    }
}
