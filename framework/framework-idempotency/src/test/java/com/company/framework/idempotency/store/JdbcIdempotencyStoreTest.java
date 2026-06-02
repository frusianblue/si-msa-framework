package com.company.framework.idempotency.store;

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
 * 실제 DB(인메모리 H2)로 멱등 스토어 의미를 검증한다. 선점/만료-재선점은 PK 제약·TIMESTAMP 비교에 의존하므로
 * 순수 로직 모킹이 아니라 실DB 로 확인한다(틀리면 조용히 잘못되는 부류).
 */
class JdbcIdempotencyStoreTest {

    private JdbcTemplate jdbc;
    private JdbcIdempotencyStore store;

    @BeforeEach
    void setUp() {
        // 테스트마다 격리된 인메모리 DB. PostgreSQL 호환 모드로 운영 DDL 과 동일 의미 보장.
        DataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:idem-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE " + JdbcIdempotencyStore.TABLE + " ("
                + "idem_key VARCHAR(200) PRIMARY KEY, result VARCHAR(4000), "
                + "expires_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL)");
        store = new JdbcIdempotencyStore(jdbc);
    }

    @Test
    @DisplayName("최초 선점은 true, 동일 키 재선점은 false(상호배제)")
    void claimsOnceThenBlocks() {
        String key = "k-1";
        assertThat(store.putIfAbsent(key, Duration.ofMinutes(10))).isTrue();
        assertThat(store.putIfAbsent(key, Duration.ofMinutes(10))).isFalse();
    }

    @Test
    @DisplayName("결과 저장 후 findResult 는 저장값을 반환한다")
    void savesAndFindsResult() {
        String key = "k-2";
        store.putIfAbsent(key, Duration.ofMinutes(10));
        store.saveResult(key, "{\"ok\":true}", Duration.ofMinutes(10));
        assertThat(store.findResult(key)).contains("{\"ok\":true}");
    }

    @Test
    @DisplayName("선점만 하고 결과 미저장이면 findResult 는 empty(result NULL)")
    void claimedButNoResultIsEmpty() {
        String key = "k-3";
        store.putIfAbsent(key, Duration.ofMinutes(10));
        assertThat(store.findResult(key)).isEmpty();
    }

    @Test
    @DisplayName("만료된 선점은 동일 키로 다시 선점할 수 있다(재선점)")
    void expiredKeyCanBeReclaimed() {
        String key = "k-4";
        // 음수 TTL → 이미 만료. 다음 선점은 만료행을 정리하고 성공해야 한다.
        assertThat(store.putIfAbsent(key, Duration.ofSeconds(-1))).isTrue();
        assertThat(store.putIfAbsent(key, Duration.ofMinutes(10))).isTrue();
        // 재선점 후에는 다시 살아있으므로 중복은 차단.
        assertThat(store.putIfAbsent(key, Duration.ofMinutes(10))).isFalse();
    }

    @Test
    @DisplayName("만료된 결과는 findResult 에서 반환되지 않는다")
    void expiredResultIsNotReturned() {
        String key = "k-5";
        store.putIfAbsent(key, Duration.ofMinutes(10));
        store.saveResult(key, "stale", Duration.ofSeconds(-1));
        assertThat(store.findResult(key)).isEmpty();
    }

    @Test
    @DisplayName("remove 후에는 결과가 사라지고 재선점이 가능하다")
    void removeClearsAndAllowsReclaim() {
        String key = "k-6";
        store.putIfAbsent(key, Duration.ofMinutes(10));
        store.saveResult(key, "v", Duration.ofMinutes(10));
        store.remove(key);
        assertThat(store.findResult(key)).isEmpty();
        assertThat(store.putIfAbsent(key, Duration.ofMinutes(10))).isTrue();
    }
}
