# framework-audit

접속/감사 로그 표준 **적재·조회**. 메서드 감사(AOP)·로그인 감사를 수집해 logging/JDBC/Kafka 싱크로 보낸다. ISMS-P·보안성 심의 대비.

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-audit') }   // framework-security 전제
```
```yaml
framework:
  audit:
    enabled: true            # 기본 false
    method-audit: true       # @Audit 메서드 감사(AuditTrailAspect)
    login-audit: true        # 로그인 성공/실패 감사
    store:
      type: jdbc             # logging(기본) | jdbc | kafka
    kafka:
      topic: audit-events    # store.type=kafka 시
```

## 쓰는 법
**자동 수집** — 로그인 이벤트와 `@Audit` 표시 메서드가 자동 기록된다. `store.type=jdbc` 면 `framework_audit_log` 에 적재.
**조회** — `AuditQueryService`(JDBC) 또는 `AuditController` 로 기간/사용자/액션 필터 조회.
**Kafka 싱크** — `store.type=kafka` 면 `framework-messaging` 의 Outbox 로 발행(유실/중복 방지).

### JDBC 스토어 — 테이블 생성
`src/main/resources/db/audit-log-postgres.sql`. 운영은 Flyway 권장.


## 실전 사용 예 (코드)

**1) 선언적 감사** — 메서드에 `@AuditLog` 만 붙이면 `AuditTrailAspect` 가 성공/실패를 `AuditEventSink` 로 기록한다.
```java
// com.company.framework.core.annotation.AuditLog
@AuditLog(action = "USER_ROLE_CHANGE", target = "user")
public void changeRole(Long userId, String role) { ... }
```
**2) 직접 이벤트 발행** — 도메인 이벤트를 코드에서 남길 때 `AuditEventSink.save(AuditEvent)`:
```java
// com.company.framework.audit.{sink.AuditEventSink, model.AuditEvent}
private final AuditEventSink sink;
public void onLargeTransfer(String actor, long amount, String ip) {
    sink.save(new AuditEvent(
        Instant.now(), "MONEY_TRANSFER", actor, "TRANSFER", "account",
        AuditEvent.RESULT_SUCCESS, ip, MDC.get("traceId"),
        "amount=" + amount, null));
}
```
**3) 조회** — 프로그램에서 `AuditQueryService`, 또는 내장 엔드포인트:
```java
PageResponse<AuditEvent> page = auditQueryService.search(
    new AuditQuery("alice", null, null, from, to, PageRequest.of(0, 20)));
```
```bash
curl "http://localhost:8081/api/v1/audit/logs?actor=alice&from=2026-06-01T00:00:00Z" \
  -H 'Authorization: Bearer <admin-token>'
```

## 끄는 법
`framework.audit.enabled: false` 또는 의존성 미포함. `method-audit`/`login-audit` 개별 토글 가능.

## 덮어쓰기(프로젝트 커스텀)
`AuditEventSink` 빈을 등록하면 적재 대상을 교체/추가(`@ConditionalOnMissingBean` 또는 다중 sink).

## 버전 관리
jdbc/web/messaging 는 `compileOnly` — 해당 store 를 쓸 때만 호스트에 전제. 신규 런타임 의존성 없음.
