package com.company.framework.idempotency.store;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 영속 멱등 저장소(store.type=jdbc). 기존 DataSource 재사용 — 다중 인스턴스/재기동 간 공유.
 * 필요한 테이블: framework_idempotency (db/idempotency-postgres.sql 참고. H2/PostgreSQL 공통 DDL).
 *
 * <p>설계 요점
 *
 * <ul>
 *   <li><b>원자적 선점</b>: {@link #putIfAbsent}는 PK(idem_key) 유니크 제약 + INSERT 충돌로 상호배제를 보장한다.
 *       살아있는 행이 있으면 충돌({@link DataIntegrityViolationException}) → false. idgen 의 채번 선점과 동일 관용.
 *   <li><b>만료 재선점</b>: 선점 전 만료된(expires_at &le; now) 행만 정리하고 INSERT 한다. 살아있는 행은 건드리지 않는다.
 *       동시에 두 인스턴스가 만료행을 정리해도 INSERT 는 정확히 하나만 성공(PK) → 멱등키 소유자는 항상 1개.
 *   <li><b>이식성</b>: 벤더별 UPSERT(MERGE/ON CONFLICT/ON DUPLICATE) 미사용. {@link #saveResult}는 "UPDATE 먼저,
 *       0행이면 INSERT" 패턴(JdbcMfaEnrollmentStore 와 동일). VARCHAR/TIMESTAMP 만 사용 → H2/PostgreSQL/Oracle 공통.
 * </ul>
 */
public class JdbcIdempotencyStore implements IdempotencyStore {

    /** 멱등 저장 테이블명. DDL(db/idempotency-postgres.sql)과 일치. */
    static final String TABLE = "framework_idempotency";

    private final JdbcTemplate jdbc;

    public JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean putIfAbsent(String key, Duration ttl) {
        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);
        Timestamp expiresAt = Timestamp.from(now.plus(ttl));
        // 만료된 동일 키만 정리(살아있는 선점은 보존). 이후 INSERT 가 PK 충돌이면 중복.
        jdbc.update("DELETE FROM " + TABLE + " WHERE idem_key = ? AND expires_at <= ?", key, nowTs);
        try {
            jdbc.update(
                    "INSERT INTO " + TABLE + " (idem_key, result, expires_at, created_at) VALUES (?, NULL, ?, ?)",
                    key,
                    expiresAt,
                    nowTs);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            // 다른 인스턴스가 먼저 선점(살아있는 행 또는 동시 INSERT) → 이번 요청은 중복.
            return false;
        }
    }

    @Override
    public void saveResult(String key, String resultJson, Duration ttl) {
        Instant now = Instant.now();
        Timestamp expiresAt = Timestamp.from(now.plus(ttl));
        int updated = jdbc.update(
                "UPDATE " + TABLE + " SET result = ?, expires_at = ? WHERE idem_key = ?", resultJson, expiresAt, key);
        if (updated == 0) {
            try {
                jdbc.update(
                        "INSERT INTO " + TABLE + " (idem_key, result, expires_at, created_at) VALUES (?, ?, ?, ?)",
                        key,
                        resultJson,
                        expiresAt,
                        Timestamp.from(now));
            } catch (DataIntegrityViolationException raceInsert) {
                // 선점만 된 행이 사이에 생성됨 → 다시 UPDATE 로 결과 반영.
                jdbc.update(
                        "UPDATE " + TABLE + " SET result = ?, expires_at = ? WHERE idem_key = ?",
                        resultJson,
                        expiresAt,
                        key);
            }
        }
    }

    @Override
    public Optional<String> findResult(String key) {
        Timestamp nowTs = Timestamp.from(Instant.now());
        List<String> rows = jdbc.query(
                "SELECT result FROM " + TABLE + " WHERE idem_key = ? AND expires_at > ?",
                (rs, n) -> rs.getString("result"),
                key,
                nowTs);
        // 행 없음/만료 → empty. 선점만 되고 결과 미저장(result NULL) → empty(InMemory 와 동일 의미).
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    @Override
    public void remove(String key) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE idem_key = ?", key);
    }
}
