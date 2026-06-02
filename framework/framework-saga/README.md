# framework-saga

분산 트랜잭션을 위한 **경량 오케스트레이션 Saga 엔진**. 중앙 코디네이터가 단계별 커맨드를 발행하고
참여 서비스의 리플라이로 전진/완료하며, 실패 시 완료된 단계를 **역순으로 보상**한다.

선택형 모듈 — 기본 비활성(`framework.saga.enabled=false`).

## 무엇을 더하는가 (그리고 더하지 않는가)

전송·신뢰성(유실/중복 방지, 멱등 소비)은 이미 **framework-messaging**(Transactional Outbox + 멱등 소비)이
담당한다. 이 모듈은 그 위에 **오케스트레이션만** 얹는다:

- **중앙 상태**: saga 인스턴스/단계 상태를 JDBC 에 영속(재기동·스턱 복구)
- **단계/보상 정의**: `SagaDefinition` 으로 순서·커맨드·보상 선언
- **코디네이터**: 성공 시 전진, 마지막이면 완료, 실패 시 역순 보상

> 단계가 2~4개로 짧고 분기가 없으면 messaging 만으로 코레오그래피가 가능하다. 오케스트레이션은
> 흐름이 길거나 중앙 가시성/보상순서가 필요할 때 값을 한다.

## 동작

```
start(type, ctx)
  └─[tx] saga_instance 생성(RUNNING) + 0번 액션 커맨드 Outbox 적재   ← 상태변경과 발행이 한 트랜잭션(원자적)
         OutboxRelay 가 Kafka 로 발행 → 참여 서비스 처리 → 리플라이 토픽으로 회신
onReply(...)  (앱 @KafkaListener → SagaReplyConsumer)
  ├─ ACTION SUCCESS → 단계 DONE → 다음 액션 / 마지막이면 COMPLETED
  ├─ ACTION FAILURE → COMPENSATING → 완료 단계 역순 보상 착수
  ├─ COMPENSATION SUCCESS → 다음(더 낮은) 보상 / 없으면 COMPENSATED
  └─ COMPENSATION FAILURE → FAILED(운영자 개입)
```

- **원자성**: 상태변경 + 커맨드 발행이 `SagaTransactionRunner`(TransactionTemplate)로 한 트랜잭션. Outbox 덕에
  커밋돼야만 커맨드가 나간다(유령 커맨드 없음).
- **멱등**: 같은 단계 리플라이 중복은 단계 상태가 PENDING 이 아니면 무시. 참여 서비스는 `(x-saga-id, x-saga-step)`
  기준으로 커맨드를 멱등 처리해야 한다(복구 재구동 시 재배달 가능 — x-event-id 는 재발행마다 바뀜).
- **복구**: `recovery.enabled=true` 시 `SagaRecoveryRelay` 가 deadline 지난 진행 중 saga 의 현재 단계 커맨드를
  재발행(PostgreSQL `FOR UPDATE SKIP LOCKED`).

## 상관관계 헤더

Outbox 릴레이는 커스텀 헤더를 단일 `x-headers` JSON 으로 싣는다. 그 안에:

| 헤더 | 의미 |
|---|---|
| `x-saga-id` | saga 인스턴스 ID |
| `x-saga-step` | 단계 인덱스 |
| `x-saga-phase` | `ACTION` / `COMPENSATION` |
| `x-saga-reply-topic` | 참여 서비스가 회신할 토픽 |
| `x-saga-outcome` | (리플라이) `SUCCESS` / `FAILURE` |

## 사용

**1) 의존성** (`build.gradle`) — compileOnly 는 비전이이므로 messaging 도 명시:
```groovy
implementation project(':framework:framework-saga')
implementation project(':framework:framework-messaging') // Outbox 발행
```

**2) DDL** — `db/saga/saga-postgres.sql` 을 서비스 마이그레이션으로 복사.

**3) 설정** (오케스트레이터 서비스):
```yaml
framework:
  messaging:
    enabled: true
    outbox: { relay: { enabled: true } }   # Kafka 발행 워커(어느 인스턴스군이든 1곳 이상)
  saga:
    enabled: true
    reply-topic: saga-replies
    step-timeout: 60s
    recovery: { enabled: true }            # PostgreSQL 전제
```

**4) 정의 등록**:
```java
@Bean
SagaDefinition orderSaga() {
    return SagaDefinition.named("OrderSaga")
        .step("reserveStock",   "stock-cmd",   "ReserveStock",   "stock-cmd",   "ReleaseStock")
        .step("processPayment", "payment-cmd", "ProcessPayment", "payment-cmd", "RefundPayment")
        .step("arrangeShipping","shipping-cmd","ArrangeShipping") // 보상 없는 단계
        .build();
}
```

**5) 시작 + 리플라이 소비**:
```java
sagaOrchestrator.start("OrderSaga", contextJson);

@KafkaListener(topics = "saga-replies", groupId = "order-orchestrator")
public void onReply(ConsumerRecord<String, String> record) {
    sagaReplyConsumer.handle(record);
}
```

**6) 참여 서비스**는 커맨드 토픽을 소비해 처리 후, `x-saga-reply-topic` 으로 `x-saga-id/step/phase` 를
그대로 싣고 `x-saga-outcome` 을 더해 회신한다(자신의 Outbox 로 발행 권장). `(saga-id, step)` 기준 멱등 필수.

## 한계 / 다음 후보

- 컨텍스트 병합은 단순 치환(리플라이가 컨텍스트를 주면 교체). 부분 병합은 앱 책임 또는 추후.
- 보상 실패는 자동 회복 불가 → `FAILED`(운영자 개입). 보상 재시도 정책은 추후.
- 타임아웃은 단계 공통. 단계별 타임아웃/재시도 횟수는 추후.
