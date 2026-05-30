package com.company.framework.security.token;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 폐쇄망/공공 SI 용. 기존 DataSource(PostgreSQL 등) 재사용.
 * 만료 row 는 조회 시 무시(WHERE expires_at > now)하며, 주기적 청소는 배치/스케줄러 권장.
 * 필요한 테이블: refresh_tokens, token_blacklist (token-store-postgres.sql 참고)
 */
public class JdbcTokenStore implements TokenStore {

    private final JdbcTemplate jdbc;

    public JdbcTokenStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveRefresh(String refreshToken, RefreshEntry entry, Duration ttl) {
        jdbc.update("DELETE FROM refresh_tokens WHERE token = ?", refreshToken);
        jdbc.update(
                "INSERT INTO refresh_tokens (token, user_id, roles, expires_at) VALUES (?, ?, ?, ?)",
                refreshToken,
                entry.userId(),
                String.join(",", entry.roles()),
                Timestamp.from(Instant.now().plus(ttl)));
    }

    @Override
    public Optional<RefreshEntry> findRefresh(String refreshToken) {
        List<RefreshEntry> rows = jdbc.query(
                "SELECT user_id, roles FROM refresh_tokens WHERE token = ? AND expires_at > ?",
                (rs, n) -> {
                    String roles = rs.getString("roles");
                    List<String> roleList =
                            (roles == null || roles.isBlank()) ? List.of() : Arrays.asList(roles.split(","));
                    return new RefreshEntry(rs.getString("user_id"), roleList);
                },
                refreshToken,
                Timestamp.from(Instant.now()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public void removeRefresh(String refreshToken) {
        jdbc.update("DELETE FROM refresh_tokens WHERE token = ?", refreshToken);
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        jdbc.update("DELETE FROM token_blacklist WHERE jti = ?", jti);
        jdbc.update(
                "INSERT INTO token_blacklist (jti, expires_at) VALUES (?, ?)",
                jti,
                Timestamp.from(Instant.now().plus(ttl)));
    }

    @Override
    public boolean isBlacklisted(String jti) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM token_blacklist WHERE jti = ? AND expires_at > ?",
                Integer.class,
                jti,
                Timestamp.from(Instant.now()));
        return cnt != null && cnt > 0;
    }
}
