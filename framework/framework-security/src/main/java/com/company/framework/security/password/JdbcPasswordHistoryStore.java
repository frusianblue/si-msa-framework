package com.company.framework.security.password;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 폐쇄망/공공 SI 용. 기존 DataSource 재사용. 필요한 테이블: password_history
 * (security-extras-postgres.sql 참고). created_at DESC 가 최신순.
 */
public class JdbcPasswordHistoryStore implements PasswordHistoryStore {

    private final JdbcTemplate jdbc;

    public JdbcPasswordHistoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String userId, String encodedPassword, int keepCount) {
        jdbc.update(
                "INSERT INTO password_history (user_id, password_hash, created_at) VALUES (?, ?, ?)",
                userId,
                encodedPassword,
                Timestamp.from(Instant.now()));
        // count 초과 이력 정리(최신 keepCount 개만 유지)
        jdbc.update(
                "DELETE FROM password_history WHERE user_id = ? AND id NOT IN ("
                        + "SELECT id FROM password_history WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?)",
                userId,
                userId,
                Math.max(1, keepCount));
    }

    @Override
    public List<String> recentEncoded(String userId, int count) {
        return jdbc.query(
                "SELECT password_hash FROM password_history WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, n) -> rs.getString("password_hash"),
                userId,
                count);
    }

    @Override
    public Optional<Instant> lastChangedAt(String userId) {
        List<Timestamp> rows = jdbc.query(
                "SELECT created_at FROM password_history WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 1",
                (rs, n) -> rs.getTimestamp("created_at"),
                userId);
        return rows.isEmpty() || rows.get(0) == null
                ? Optional.empty()
                : Optional.of(rows.get(0).toInstant());
    }
}
