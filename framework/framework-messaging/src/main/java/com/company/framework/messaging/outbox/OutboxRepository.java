package com.company.framework.messaging.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Outbox 테이블 영속. JdbcTemplate 으로 @Primary DataSource(routing 켜면 WRITE 노드) 에 동작한다.
 *
 * <p><b>append</b> 는 호출자의 진행 중 트랜잭션에 그대로 참여한다(JdbcTemplate → DataSourceUtils 가 트랜잭션 바인딩
 * 커넥션을 재사용). 즉 비즈니스 데이터와 같은 커밋 단위로 INSERT 되어 원자성이 보장된다.
 *
 * <p><b>fetchAndLockPending</b> 는 {@code FOR UPDATE SKIP LOCKED}(PostgreSQL)로 다중 인스턴스 릴레이가 같은 행을
 * 중복 발행하지 않게 한다. 따라서 릴레이는 PostgreSQL 환경에서만 켤 것(H2 등은 SKIP LOCKED 미지원).
 */
public class OutboxRepository {

    private final JdbcTemplate jdbc;
    private final String table;

    private final RowMapper<OutboxEvent> rowMapper = (rs, n) -> new OutboxEvent(
            rs.getLong("id"),
            rs.getString("event_id"),
            rs.getString("aggregate_type"),
            rs.getString("aggregate_id"),
            rs.getString("event_type"),
            rs.getString("topic"),
            rs.getString("message_key"),
            rs.getString("payload"),
            rs.getString("headers"),
            rs.getInt("attempts"));

    public OutboxRepository(JdbcTemplate jdbc, String table) {
        this.jdbc = jdbc;
        this.table = table;
    }

    /** 진행 중 트랜잭션 안에서 PENDING 행을 적재한다(비즈니스 커밋과 원자적). */
    public void append(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String messageKey,
            String payload,
            String headers,
            String traceId) {
        jdbc.update(
                "INSERT INTO " + table + " (event_id, aggregate_type, aggregate_id, event_type, topic, "
                        + "message_key, payload, headers, status, attempts, trace_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)",
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                messageKey,
                payload,
                headers,
                traceId,
                Timestamp.from(Instant.now()));
    }

    /** 발행 대기 행을 잠그고(다중 인스턴스 중복 방지) 가져온다. 릴레이의 트랜잭션 안에서 호출할 것. */
    public List<OutboxEvent> fetchAndLockPending(int batchSize, int maxAttempts) {
        return jdbc.query(
                "SELECT id, event_id, aggregate_type, aggregate_id, event_type, topic, message_key, "
                        + "payload, headers, attempts FROM " + table + " "
                        + "WHERE status = 'PENDING' AND attempts < ? "
                        + "ORDER BY id FOR UPDATE SKIP LOCKED LIMIT ?",
                rowMapper,
                maxAttempts,
                batchSize);
    }

    /** 발행 성공 처리. */
    public void markPublished(long id) {
        jdbc.update(
                "UPDATE " + table + " SET status = 'PUBLISHED', published_at = ?, last_error = NULL WHERE id = ?",
                Timestamp.from(Instant.now()),
                id);
    }

    /**
     * 발행 실패 처리. 시도 횟수를 올리고, 소진되면 FAILED(운영자 확인 대상)로, 아니면 PENDING 유지(다음 폴링 재시도).
     */
    public void markFailure(long id, int newAttempts, boolean exhausted, String error) {
        jdbc.update(
                "UPDATE " + table + " SET attempts = ?, last_error = ?, status = ? WHERE id = ?",
                newAttempts,
                truncate(error, 2000),
                exhausted ? "FAILED" : "PENDING",
                id);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
