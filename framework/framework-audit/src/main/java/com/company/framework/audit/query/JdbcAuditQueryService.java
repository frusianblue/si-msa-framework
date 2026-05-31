package com.company.framework.audit.query;

import com.company.framework.audit.model.AuditEvent;
import com.company.framework.core.page.PageResponse;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * audit_log 조회(동적 WHERE + 페이징). actor/eventType/result/기간으로 필터.
 */
public class JdbcAuditQueryService implements AuditQueryService {

    private final JdbcTemplate jdbc;

    public JdbcAuditQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResponse<AuditEvent> search(AuditQuery q) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (q.actor() != null && !q.actor().isBlank()) {
            where.append(" AND actor = ?");
            args.add(q.actor());
        }
        if (q.eventType() != null && !q.eventType().isBlank()) {
            where.append(" AND event_type = ?");
            args.add(q.eventType());
        }
        if (q.result() != null && !q.result().isBlank()) {
            where.append(" AND result = ?");
            args.add(q.result());
        }
        if (q.from() != null) {
            where.append(" AND event_time >= ?");
            args.add(Timestamp.from(q.from()));
        }
        if (q.to() != null) {
            where.append(" AND event_time <= ?");
            args.add(Timestamp.from(q.to()));
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log" + where, Long.class, args.toArray());
        long totalElements = total == null ? 0L : total;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.page().size());
        pageArgs.add(q.page().offset());
        List<AuditEvent> content = jdbc.query(
                "SELECT event_time, event_type, actor, action, target, result, client_ip, trace_id, detail, elapsed_ms "
                        + "FROM audit_log" + where + " ORDER BY event_time DESC, id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new AuditEvent(
                        rs.getTimestamp("event_time").toInstant(),
                        rs.getString("event_type"),
                        rs.getString("actor"),
                        rs.getString("action"),
                        rs.getString("target"),
                        rs.getString("result"),
                        rs.getString("client_ip"),
                        rs.getString("trace_id"),
                        rs.getString("detail"),
                        (Long) rs.getObject("elapsed_ms")),
                pageArgs.toArray());

        return PageResponse.of(content, q.page(), totalElements);
    }
}
