package com.company.framework.audit.query;

import com.company.framework.core.page.PageRequest;
import java.time.Instant;

/**
 * 감사 로그 조회 조건. null 필드는 조건에서 제외(동적 WHERE).
 */
public record AuditQuery(String actor, String eventType, String result, Instant from, Instant to, PageRequest page) {}
