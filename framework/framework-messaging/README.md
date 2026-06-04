# framework-messaging

신뢰성 메시징 — **Transactional Outbox**(발행 유실/중복 방지) + 소비자측 **멱등 소비**. 금융 연계의 토대(saga 가 이 위에 오케스트레이션을 얹는다).

## 켜는 법
```gradle
dependencies {
  implementation project(':framework:framework-messaging')   // spring-kafka 전이
  implementation project(':framework:framework-idempotency') // 멱등 소비용(권장: redis)
}
```
```yaml
framework:
  messaging:
    enabled: true                 # 기본 false
    outbox:
      table: outbox_event
      relay: { enabled: true }    # Outbox → Kafka 릴레이 폴러
    kafka:
      bootstrap-servers: ${KAFKA}
      acks: all
      enable-idempotence: true
    consumer:
      enabled: true               # IdempotentEventProcessor 활성
      ttl: 24h
      key-prefix: "evt:"
```

## 쓰는 법
**발행(원자적)** — 비즈니스 트랜잭션과 같은 트랜잭션에서 Outbox 행을 쓴다. 릴레이가 별도로 Kafka 발행.
```java
private final OutboxEventPublisher outbox;

outbox.publish("payment-topic", "Payment", paymentId, "PaymentApproved", payload);
```
**멱등 소비** — `@KafkaListener` 에서 `IdempotentEventProcessor` 로 감싸면 `x-event-id` 헤더 기준 중복 배달을 1회만 처리(실패 시 키 해제→재처리). 멱등 저장소는 `framework-idempotency`.

### Outbox 테이블
`src/main/resources/db/messaging/outbox-postgres.sql`. Flyway 권장.

## 끄는 법
`framework.messaging.enabled: false`(발행) / `consumer.enabled: false`(소비) 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`OutboxRepository` 등 빈을 재정의해 저장소를 교체할 수 있다.

## 버전 관리
spring-kafka 는 Boot BOM. idempotency/jdbc 는 `compileOnly`(선택 전제). 신규 외부 의존성 없음.
