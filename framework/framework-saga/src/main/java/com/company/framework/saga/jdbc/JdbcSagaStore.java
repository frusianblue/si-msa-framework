package com.company.framework.saga.jdbc;

import com.company.framework.saga.SagaInstance;
import com.company.framework.saga.SagaPhase;
import com.company.framework.saga.SagaStatus;
import com.company.framework.saga.SagaStepRecord;
import com.company.framework.saga.SagaStepStatus;
import com.company.framework.saga.SagaStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * saga 상태 JDBC 영속. JdbcTemplate 으로 @Primary DataSource(routing 켜면 WRITE 노드)에 동작한다.
 * 호출자(오케스트레이터/복구 폴러)의 진행 중 트랜잭션에 그대로 참여한다.
 *
 * <p>{@code findStuck} 은 {@code FOR UPDATE SKIP LOCKED}(PostgreSQL)로 다중 인스턴스 복구 폴러가 같은
 * 인스턴스를 중복 재구동하지 않게 한다. 따라서 복구 폴러는 PostgreSQL 환경에서만 켤 것.
 *
 * <p>단계 적재는 벤더 UPSERT(ON CONFLICT) 대신 UPDATE→없으면 INSERT 의 이식성 패턴을 쓴다.
 */
public class JdbcSagaStore implements SagaStore {

    private final JdbcTemplate jdbc;
    private final String instanceTable;
    private final String stepTable;

    private final RowMapper<SagaInstance> instanceMapper = (rs, n) -> {
        Timestamp dl = rs.getTimestamp("deadline_at");
        return new SagaInstance(
                rs.getString("saga_id"),
                rs.getString("saga_type"),
                SagaStatus.valueOf(rs.getString("status")),
                rs.getInt("current_step"),
                rs.getString("context"),
                dl == null ? null : dl.toInstant());
    };

    public JdbcSagaStore(JdbcTemplate jdbc, String instanceTable, String stepTable) {
        this.jdbc = jdbc;
        this.instanceTable = instanceTable;
        this.stepTable = stepTable;
    }

    @Override
    public void createInstance(String sagaId, String sagaType, String contextJson, Instant deadlineAt) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO " + instanceTable + " (saga_id, saga_type, status, current_step, context, "
                        + "deadline_at, created_at, updated_at) VALUES (?, ?, 'RUNNING', 0, ?, ?, ?, ?)",
                sagaId,
                sagaType,
                contextJson,
                deadlineAt == null ? null : Timestamp.from(deadlineAt),
                now,
                now);
    }

    @Override
    public Optional<SagaInstance> find(String sagaId) {
        List<SagaInstance> rows = jdbc.query(
                "SELECT saga_id, saga_type, status, current_step, context, deadline_at FROM " + instanceTable
                        + " WHERE saga_id = ?",
                instanceMapper,
                sagaId);
        return rows.stream().findFirst();
    }

    @Override
    public void updateStatus(String sagaId, SagaStatus status) {
        jdbc.update(
                "UPDATE " + instanceTable + " SET status = ?, updated_at = ? WHERE saga_id = ?",
                status.name(),
                Timestamp.from(Instant.now()),
                sagaId);
    }

    @Override
    public void updateCurrentStep(String sagaId, int currentStep) {
        jdbc.update(
                "UPDATE " + instanceTable + " SET current_step = ?, updated_at = ? WHERE saga_id = ?",
                currentStep,
                Timestamp.from(Instant.now()),
                sagaId);
    }

    @Override
    public void updateContext(String sagaId, String contextJson) {
        jdbc.update(
                "UPDATE " + instanceTable + " SET context = ?, updated_at = ? WHERE saga_id = ?",
                contextJson,
                Timestamp.from(Instant.now()),
                sagaId);
    }

    @Override
    public void touchDeadline(String sagaId, Instant deadlineAt) {
        jdbc.update(
                "UPDATE " + instanceTable + " SET deadline_at = ?, updated_at = ? WHERE saga_id = ?",
                deadlineAt == null ? null : Timestamp.from(deadlineAt),
                Timestamp.from(Instant.now()),
                sagaId);
    }

    @Override
    public Optional<SagaStepRecord> findStep(String sagaId, int stepIndex, SagaPhase phase) {
        List<SagaStepRecord> rows = jdbc.query(
                "SELECT saga_id, step_index, step_name, phase, status, command_event_id, reply_event_id FROM "
                        + stepTable + " WHERE saga_id = ? AND step_index = ? AND phase = ?",
                (rs, n) -> new SagaStepRecord(
                        rs.getString("saga_id"),
                        rs.getInt("step_index"),
                        rs.getString("step_name"),
                        SagaPhase.valueOf(rs.getString("phase")),
                        SagaStepStatus.valueOf(rs.getString("status")),
                        rs.getString("command_event_id"),
                        rs.getString("reply_event_id")),
                sagaId,
                stepIndex,
                phase.name());
        return rows.stream().findFirst();
    }

    @Override
    public void upsertStepPending(
            String sagaId, int stepIndex, String stepName, SagaPhase phase, String commandEventId) {
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbc.update(
                "UPDATE " + stepTable + " SET status = 'PENDING', command_event_id = ?, updated_at = ? "
                        + "WHERE saga_id = ? AND step_index = ? AND phase = ?",
                commandEventId,
                now,
                sagaId,
                stepIndex,
                phase.name());
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO " + stepTable + " (saga_id, step_index, step_name, phase, status, "
                            + "command_event_id, created_at, updated_at) VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?)",
                    sagaId,
                    stepIndex,
                    stepName,
                    phase.name(),
                    commandEventId,
                    now,
                    now);
        }
    }

    @Override
    public void markStep(String sagaId, int stepIndex, SagaPhase phase, SagaStepStatus status, String replyEventId) {
        jdbc.update(
                "UPDATE " + stepTable + " SET status = ?, reply_event_id = ?, updated_at = ? "
                        + "WHERE saga_id = ? AND step_index = ? AND phase = ?",
                status.name(),
                replyEventId,
                Timestamp.from(Instant.now()),
                sagaId,
                stepIndex,
                phase.name());
    }

    @Override
    public boolean isActionDone(String sagaId, int stepIndex) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + stepTable + " WHERE saga_id = ? AND step_index = ? "
                        + "AND phase = 'ACTION' AND status = 'DONE'",
                Integer.class,
                sagaId,
                stepIndex);
        return count != null && count > 0;
    }

    @Override
    public List<SagaInstance> findStuck(Instant now, int limit) {
        return jdbc.query(
                "SELECT saga_id, saga_type, status, current_step, context, deadline_at FROM " + instanceTable
                        + " WHERE status IN ('RUNNING', 'COMPENSATING') AND deadline_at < ? "
                        + "ORDER BY id FOR UPDATE SKIP LOCKED LIMIT ?",
                instanceMapper,
                Timestamp.from(now),
                limit);
    }
}
