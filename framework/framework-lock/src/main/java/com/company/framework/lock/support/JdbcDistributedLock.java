package com.company.framework.lock.support;

import com.company.framework.lock.DistributedLock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 영속 분산 락(type=jdbc). 기존 DataSource 를 재사용해 별도 인프라(Redis) 없이 다중 인스턴스 상호배제. Redis 가 없는
 * 폐쇄망/온프레 환경에 적합. 필요한 테이블: {@code framework_lock} (db/lock-postgres.sql — H2/PostgreSQL/Oracle 공통).
 *
 * <p>설계 요점({@code JdbcIdempotencyStore} 와 동일 관용)
 *
 * <ul>
 *   <li><b>원자적 획득</b>: PK({@code lock_key}) 유니크 + INSERT 충돌로 상호배제. 동시에 둘이 INSERT 해도 정확히 하나만 성공 → 소유자 1개.
 *   <li><b>만료 재획득</b>: 획득 전 만료된({@code expires_at <= now}) <i>동일 키</i> 행만 정리한다. 살아있는 락은 건드리지 않는다.
 *   <li><b>소유 검증</b>: 해제/연장은 {@code WHERE lock_key=? AND lock_owner=?} 로 토큰 일치 시에만 작동 → 만료 후 타 인스턴스가
 *       재획득한 락을 잘못 해제하지 않는다.
 *   <li><b>이식성</b>: 벤더별 UPSERT 미사용. VARCHAR/TIMESTAMP 만 사용.
 * </ul>
 */
public class JdbcDistributedLock implements DistributedLock {

    /** 락 테이블명. DDL(db/lock-postgres.sql)과 일치. */
    static final String TABLE = "framework_lock";

    private final JdbcTemplate jdbc;

    public JdbcDistributedLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryLock(String key, String token, Duration ttl) {
        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);
        Timestamp expiresAt = Timestamp.from(now.plus(ttl));
        // 만료된 동일 키만 정리(살아있는 락 보존). 이후 INSERT 가 PK 충돌이면 누군가 보유 중.
        jdbc.update("DELETE FROM " + TABLE + " WHERE lock_key = ? AND expires_at <= ?", key, nowTs);
        try {
            jdbc.update(
                    "INSERT INTO " + TABLE + " (lock_key, lock_owner, expires_at, created_at) VALUES (?, ?, ?, ?)",
                    key,
                    token,
                    expiresAt,
                    nowTs);
            return true;
        } catch (DataIntegrityViolationException held) {
            // 다른 인스턴스가 먼저 선점(살아있는 행 또는 동시 INSERT) → 획득 실패.
            return false;
        }
    }

    @Override
    public void unlock(String key, String token) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE lock_key = ? AND lock_owner = ?", key, token);
    }

    @Override
    public void keepUntil(String key, String token, Duration ttl) {
        Timestamp newExpiry = Timestamp.from(Instant.now().plus(ttl));
        jdbc.update(
                "UPDATE " + TABLE + " SET expires_at = ? WHERE lock_key = ? AND lock_owner = ?", newExpiry, key, token);
    }
}
