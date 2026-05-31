package com.company.framework.audit.sink;

import com.company.framework.audit.model.AuditEvent;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * audit_log 테이블 영속(PostgreSQL 등). 적재 실패는 비즈니스 흐름을 깨지 않도록 삼키고 로깅만 한다.
 * 필요한 테이블: audit-log-postgres.sql 참고.
 */
public class JdbcAuditEventSink implements AuditEventSink {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditEventSink.class);

    private final JdbcTemplate jdbc;

    public JdbcAuditEventSink(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AuditEvent e) {
        try {
            jdbc.update(
                    "INSERT INTO audit_log "
                            + "(event_time, event_type, actor, action, target, result, client_ip, trace_id, detail, elapsed_ms) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Timestamp.from(e.eventTime()),
                    e.eventType(),
                    e.actor(),
                    e.action(),
                    e.target(),
                    e.result(),
                    e.clientIp(),
                    e.traceId(),
                    e.detail(),
                    e.elapsedMs());
        } catch (RuntimeException ex) {
            log.warn("감사 로그 적재 실패(무시하고 진행): type={} action={} actor={}", e.eventType(), e.action(), e.actor(), ex);
        }
    }
}
