package com.company.framework.audit.query;

import com.company.framework.audit.model.AuditEvent;
import com.company.framework.core.page.PageResponse;

/** 감사 로그 조회(영속 싱크에서만 의미 있음 → jdbc 일 때 등록). */
public interface AuditQueryService {
    PageResponse<AuditEvent> search(AuditQuery query);
}
