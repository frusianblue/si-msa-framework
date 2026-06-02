package com.company.framework.lock.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 실제 DB(인메모리 H2)로 JDBC 락 의미를 검증한다. 획득/만료-재획득은 PK 제약·TIMESTAMP 비교에 의존하므로
 * 순수 모킹이 아니라 실DB 로 확인한다(틀리면 조용히 잘못되는 부류 — JdbcIdempotencyStoreTest 와 동일 결).
 */
class JdbcDistributedLockTest {

    private JdbcTemplate jdbc;
    private JdbcDistributedLock lock;

    @BeforeEach
    void setUp() {
        // 테스트마다 격리된 인메모리 DB. PostgreSQL 호환 모드로 운영 DDL 과 동일 의미 보장.
        DataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:lock-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE " + JdbcDistributedLock.TABLE + " ("
                + "lock_key VARCHAR(200) PRIMARY KEY, lock_owner VARCHAR(100) NOT NULL, "
                + "expires_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL)");
        lock = new JdbcDistributedLock(jdbc);
    }

    @Test
    @DisplayName("최초 획득은 true, 다른 소유자의 동시 획득은 false(상호배제)")
    void acquiresOnceThenBlocks() {
        assertThat(lock.tryLock("k", "owner-A", Duration.ofMinutes(10))).isTrue();
        assertThat(lock.tryLock("k", "owner-B", Duration.ofMinutes(10))).isFalse();
    }

    @Test
    @DisplayName("만료된 락은 다른 소유자가 재획득할 수 있다")
    void expiredLockCanBeReacquired() {
        assertThat(lock.tryLock("k", "owner-A", Duration.ofSeconds(-1))).isTrue(); // 이미 만료
        assertThat(lock.tryLock("k", "owner-B", Duration.ofMinutes(10))).isTrue();
        assertThat(lock.tryLock("k", "owner-C", Duration.ofMinutes(10))).isFalse(); // 다시 살아있음
    }

    @Test
    @DisplayName("소유자 토큰만 해제할 수 있다(타 토큰 해제는 무시)")
    void onlyOwnerCanUnlock() {
        lock.tryLock("k", "owner-A", Duration.ofMinutes(10));
        lock.unlock("k", "intruder"); // 비소유 → 행 미삭제
        assertThat(lock.tryLock("k", "owner-B", Duration.ofMinutes(10))).isFalse(); // 여전히 A 보유
        lock.unlock("k", "owner-A"); // 소유자 해제
        assertThat(lock.tryLock("k", "owner-B", Duration.ofMinutes(10))).isTrue();
    }

    @Test
    @DisplayName("keepUntil 로 만료를 미래로 미루면 재획득이 막힌다(소유자만)")
    void keepUntilExtendsForOwnerOnly() {
        lock.tryLock("k", "owner-A", Duration.ofSeconds(-1)); // 곧 만료
        lock.keepUntil("k", "owner-A", Duration.ofMinutes(10)); // 미래로 연장
        assertThat(lock.tryLock("k", "owner-B", Duration.ofMinutes(10))).isFalse();
        // 비소유 keepUntil 은 무효 → 만료 상태가 유지되어야 함.
        lock.tryLock("x", "owner-A", Duration.ofSeconds(-1));
        lock.keepUntil("x", "intruder", Duration.ofMinutes(10));
        assertThat(lock.tryLock("x", "owner-B", Duration.ofMinutes(10))).isTrue();
    }
}
