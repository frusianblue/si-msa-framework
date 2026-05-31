package com.company.framework.audit.sink;

import com.company.framework.audit.model.AuditEvent;

/**
 * 감사 이벤트 적재 대상. 구현 교체로 백엔드를 바꾼다.
 *  - logging : 별도 인프라 0(AUDIT 로거로 구조화 출력). 기본값.
 *  - jdbc    : audit_log 테이블 영속(조회 API 제공).
 *  - kafka   : (예정) framework-messaging 도입 시 추가.
 * 적재 실패가 비즈니스 트랜잭션을 깨지 않도록 구현은 예외를 삼키고 자체 로깅한다.
 */
public interface AuditEventSink {
    void save(AuditEvent event);
}
