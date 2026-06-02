package com.company.framework.mfa.store;

import com.company.framework.mfa.core.MfaMethod;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 폐쇄망/공공·운영용 등록 저장소. 기존 DataSource 재사용. 필요한 테이블: mfa_enrollment (mfa-postgres.sql 참고).
 *
 * <p>복구코드 해시는 쉼표 구분 단일 컬럼(recovery_hashes)으로 저장한다(해시는 hex 라 쉼표 충돌 없음).
 */
public class JdbcMfaEnrollmentStore implements MfaEnrollmentStore {

    private final JdbcTemplate jdbc;

    public JdbcMfaEnrollmentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<MfaEnrollment> mapper = (rs, n) -> {
        String hashesCsv = rs.getString("recovery_hashes");
        List<String> hashes =
                (hashesCsv == null || hashesCsv.isBlank()) ? List.of() : Arrays.asList(hashesCsv.split(","));
        Timestamp created = rs.getTimestamp("created_at");
        return new MfaEnrollment(
                rs.getString("user_id"),
                MfaMethod.valueOf(rs.getString("method")),
                rs.getString("secret"),
                hashes,
                rs.getBoolean("confirmed"),
                created == null ? Instant.now() : created.toInstant());
    };

    @Override
    public Optional<MfaEnrollment> find(String userId, MfaMethod method) {
        List<MfaEnrollment> rows = jdbc.query(
                "SELECT user_id, method, secret, recovery_hashes, confirmed, created_at "
                        + "FROM mfa_enrollment WHERE user_id = ? AND method = ?",
                mapper,
                userId,
                method.name());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<MfaEnrollment> findByUser(String userId) {
        return jdbc.query(
                "SELECT user_id, method, secret, recovery_hashes, confirmed, created_at "
                        + "FROM mfa_enrollment WHERE user_id = ?",
                mapper,
                userId);
    }

    @Override
    public void save(MfaEnrollment enrollment) {
        String hashesCsv =
                enrollment.recoveryCodeHashes() == null ? null : String.join(",", enrollment.recoveryCodeHashes());
        int updated = jdbc.update(
                "UPDATE mfa_enrollment SET secret = ?, recovery_hashes = ?, confirmed = ?, created_at = ? "
                        + "WHERE user_id = ? AND method = ?",
                enrollment.secret(),
                hashesCsv,
                enrollment.confirmed(),
                Timestamp.from(enrollment.createdAt() == null ? Instant.now() : enrollment.createdAt()),
                enrollment.userId(),
                enrollment.method().name());
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO mfa_enrollment (user_id, method, secret, recovery_hashes, confirmed, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    enrollment.userId(),
                    enrollment.method().name(),
                    enrollment.secret(),
                    hashesCsv,
                    enrollment.confirmed(),
                    Timestamp.from(enrollment.createdAt() == null ? Instant.now() : enrollment.createdAt()));
        }
    }

    @Override
    public void delete(String userId, MfaMethod method) {
        jdbc.update("DELETE FROM mfa_enrollment WHERE user_id = ? AND method = ?", userId, method.name());
    }
}
