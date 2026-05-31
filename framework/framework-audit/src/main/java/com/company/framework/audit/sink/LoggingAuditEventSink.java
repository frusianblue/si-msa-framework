package com.company.framework.audit.sink;

import com.company.framework.audit.model.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 기본 싱크(인프라 0). 전용 "AUDIT" 로거로 구조화 출력 → 운영에서 별도 appender/수집기로 분리 적재 가능.
 */
public class LoggingAuditEventSink implements AuditEventSink {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    @Override
    public void save(AuditEvent e) {
        audit.info(
                "eventTime={} type={} actor={} action={} target={} result={} clientIp={} traceId={} elapsedMs={} detail={}",
                e.eventTime(),
                e.eventType(),
                e.actor(),
                e.action(),
                e.target(),
                e.result(),
                e.clientIp(),
                e.traceId(),
                e.elapsedMs(),
                e.detail());
    }
}
