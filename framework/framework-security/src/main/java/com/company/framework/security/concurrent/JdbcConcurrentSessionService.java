package com.company.framework.security.concurrent;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폐쇄망/공공·다중 인스턴스 SI 용. 기존 DataSource 재사용. 필요한 테이블: active_sessions
 * (security-extras-postgres.sql 참고). 등록/한도판정을 한 트랜잭션으로 묶어 인스턴스 간 일관성을 확보.
 */
public class JdbcConcurrentSessionService implements ConcurrentSessionService {

    private final JdbcTemplate jdbc;
    private final ConcurrentSessionProperties props;

    public JdbcConcurrentSessionService(JdbcTemplate jdbc, ConcurrentSessionProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override
    @Transactional
    public List<ActiveSession> register(ActiveSession session) {
        int max = Math.max(1, props.getMaxSessions());
        List<ActiveSession> current = activeSessions(session.userId());

        List<ActiveSession> evicted = new ArrayList<>();
        if (current.size() >= max) {
            if (props.getStrategy() == ConcurrentSessionProperties.Strategy.REJECT) {
                throw new BusinessException(
                        ErrorCode.Common.CONFLICT, "이미 다른 기기/브라우저에서 로그인되어 있습니다. 기존 세션을 로그아웃한 뒤 다시 시도하세요.");
            }
            // activeSessions 는 최신순 → 오래된 것이 뒤. 오래된 (size - max + 1)건 제거.
            int toEvict = current.size() - max + 1;
            for (int i = 0; i < toEvict; i++) {
                ActiveSession old = current.get(current.size() - 1 - i);
                jdbc.update("DELETE FROM active_sessions WHERE session_id = ?", old.sessionId());
                evicted.add(old);
            }
        }
        jdbc.update(
                "INSERT INTO active_sessions (session_id, user_id, access_jti, refresh_token, issued_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                session.sessionId(),
                session.userId(),
                session.accessJti(),
                session.refreshToken(),
                Timestamp.from(Instant.ofEpochMilli(session.issuedAtEpochMs())));
        return evicted;
    }

    @Override
    public void unregister(String sessionId) {
        if (sessionId == null) return;
        try {
            jdbc.update("DELETE FROM active_sessions WHERE session_id = ?", sessionId);
        } catch (DataAccessException ignore) {
            // 세션 테이블 미구성 등은 로그인 흐름을 깨지 않도록 무시(감사 로그로 추적 권장)
        }
    }

    @Override
    public List<ActiveSession> activeSessions(String userId) {
        return jdbc.query(
                "SELECT user_id, session_id, access_jti, refresh_token, issued_at "
                        + "FROM active_sessions WHERE user_id = ? ORDER BY issued_at DESC, session_id DESC",
                (rs, n) -> new ActiveSession(
                        rs.getString("user_id"),
                        rs.getString("session_id"),
                        rs.getString("access_jti"),
                        rs.getString("refresh_token"),
                        rs.getTimestamp("issued_at").getTime()),
                userId);
    }
}
