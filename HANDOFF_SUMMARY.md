# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**messaging 소비자측 멱등 소비** 완성 — 발행측이 싣는 **`x-event-id`** 헤더를 키로 `IdempotencyStore` 에 선점해 **중복 배달(at-least-once)을 1회만 처리**(`IdempotentEventProcessor`). **처리 실패 시 키 해제→재배달 재처리**를 위해 `IdempotencyStore` SPI 에 **`remove(key)` 추가**(InMemory/Redis 구현). 헤더명 단일화(`MessagingHeaders`)로 발행/소비 드리프트 차단. **새 외부 의존성 0**(spring-kafka 재사용 + framework-idempotency compileOnly).

## 최종 갱신
- 일자: 2026-06-01 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **framework-messaging 확장(소비자측)** — 발행측(Outbox/relay)과 **독립** 동작.
  - **`consumer/IdempotentEventProcessor`** — `process(ConsumerRecord, Consumer<EventEnvelope>)` / `processByEventId(String, Runnable)`. 흐름: `putIfAbsent(evt:<eventId>, ttl)` 성공=처리주체→핸들러, 실패=중복→스킵(false), 핸들러 예외→`store.remove(key)` 후 rethrow(재배달 재처리). `x-event-id` 없으면 멱등 불가로 보고 그대로 처리(스킵보다 안전).
  - **`consumer/EventEnvelope`**(record) — `ConsumerRecord` 에서 `x-event-id/x-event-type/x-aggregate-type`+key+payload 파싱(`from(record)`).
  - **`MessagingHeaders`** 상수(X_EVENT_ID 등) 신설 → **OutboxRelay 가 이 상수 사용하도록 리팩터**(헤더명 단일 소스, 발행/소비 일치 보장).
  - **`MessagingProperties.Consumer`** 추가: `consumer.enabled`(기본 false)·`ttl`(기본 24h)·`key-prefix`(기본 `evt:`).
  - **`MessagingConsumerAutoConfiguration`** — `@AutoConfiguration(afterName=IdempotencyAutoConfiguration)` + `@ConditionalOnClass({IdempotencyStore, ConsumerRecord})` + `@ConditionalOnProperty(messaging.consumer.enabled=true)` + 빈 `@ConditionalOnBean(IdempotencyStore)`. imports 에 등록.
  - messaging `build.gradle`: `compileOnly project(':framework:framework-idempotency')`(+test). spring-kafka 는 기존 api.
- **framework-idempotency 확장(SPI)** — `IdempotencyStore.remove(String key)` 추가 + InMemory(`map.remove`)·Redis(`delete LOCK+RESULT`) 구현. (웹 인터셉터엔 영향 없음.)
- **문서**: `docs/FRAMEWORK_MODULES.md`(messaging 행/진행현황에 소비자측 추가). **STACK.md/libs.versions.toml/루트 build.gradle 무변경**(버전 0).

## 현재 상태 (적용/검증)
- 신규/변경 파일 모두 repo 반영. 정적 점검 통과(괄호 균형, 패키지=디렉터리, Jackson2 import 0, `remove()` SPI+양 구현 존재, relay 가 헤더 상수 사용=문자열 리터럴 0).
- 레포 내 `IdempotencyStore` 커스텀 구현 없음(프레임워크 2개뿐, 둘 다 갱신) → SPI 변경 안전. 기존 `@KafkaListener` 없음.
- ⚠️ **실제 gradle 컴파일 미검증**(작성 환경 차단). 받는 쪽: `./gradlew :framework:framework-idempotency:compileJava :framework:framework-messaging:compileJava` + `./gradlew spotlessApply`.

## 추가 환경(질문 답) — "새로 깔 라이브러리는 없다"
- **빌드**: 새 의존성 0(spring-kafka 재사용 + idempotency compileOnly).
- **런타임**: ① Kafka 컨슈머 설정(`spring.kafka.consumer.group-id` 등) ② **멱등 저장소** — **멀티 인스턴스 컨슈머면 Redis 사실상 필수**(`framework.idempotency.store.type=redis` + framework-redis). 인메모리는 단일 인스턴스/로컬 전용(인스턴스별·재기동 휘발). idempotency 는 현재 **memory/redis만 구현**(jdbc 미구현). ③ **소비 서비스가 `framework-idempotency` 의존 추가** 필요(없으면 컨슈머 자동구성 우아하게 비활성).
- 테스트: Testcontainers(kafka/redis, 이미 카탈로그).

## 켜는 법 (소비 서비스 application.yml)
```yaml
framework:
  messaging:
    consumer: { enabled: true, ttl: PT24H, key-prefix: "evt:" }   # 발행측(messaging.enabled)과 독립
  idempotency:
    enabled: true
    store: { type: redis }          # 멀티 인스턴스 컨슈머는 redis (memory=단일 전용)
spring:
  kafka:
    bootstrap-servers: kafka:9092
    consumer: { group-id: billing, auto-offset-reset: earliest }
```
build.gradle(소비 서비스): `implementation project(':framework:framework-messaging')` + `implementation project(':framework:framework-idempotency')` + redis면 `:framework:framework-redis`.
사용:
```java
@KafkaListener(topics = "orders", groupId = "billing")
public void onMessage(ConsumerRecord<String,String> record) {
    eventProcessor.process(record, env -> { /* env.payload() 처리 */ });
}
```

## 바로 다음 할 일 (Next)
1. 받는 쪽 컴파일 확인 + `spotlessApply`. 멀티 인스턴스면 redis 스토어 설정.
2. **다음 묶음 선택**: 규제특화(pki/mfa/hsm/recon/egov) 또는 **관측(observability)** — 분산추적은 이미 core 에 micrometer-tracing-otel 있음, 메트릭/로그 표준화·대시보드가 후보.
   - (선택) idempotency 에 **JdbcIdempotencyStore** 추가(현재 memory/redis만) — DB 기반 멱등 필요 시.
3. 이후: 게이트웨이/k8s/CI-CD 멀티서비스화. (상세 `docs/FRAMEWORK_MODULES.md` 4절)

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **`IdempotencyStore` SPI 에 `remove(String)` 추가됨(브레이킹)** — 커스텀 구현체는 `remove` 구현 필수(레포 내엔 없음). 소비자 멱등의 "실패 시 키 해제→재처리"에 필수.
- **멱등 키 선점 타이밍의 본질적 한계**: `putIfAbsent` 성공 후 핸들러 완료 전 인스턴스 비정상 종료 시 키가 TTL 까지 남아 그 사이 재배달이 스킵될 수 있음(at-least-once). TTL 을 재시도 주기보다 길게(무한 회피). `x-event-id` 부재 시 멱등 불가→그대로 처리.
- **인메모리 멱등은 컨슈머 다중화에서 무력**: 인스턴스별 맵이라 교차 중복 미차단 + 재기동 휘발 → 멀티 파드 컨슈머는 **redis 필수**.
- **소비자 자동구성은 발행측과 독립**: `messaging.consumer.enabled` 만으로 동작(JdbcTemplate/Outbox 불요). 순수 소비 서비스 지원.
- **Kafka 헤더명은 `MessagingHeaders` 단일 소스**: 발행(relay)/소비(EventEnvelope) 양측이 공유 — 리터럴 흩뿌리지 말 것.
- (기존) 새 모듈/변경은 `settings.gradle`/`imports` 등록 · BOM 밖 새 라이브러리만 카탈로그 핀(이번 0) · SXSSF 종료 `close()` · Batch6 패키지 이동 & JobLauncher→JobOperator · Spring7 메일 jakarta.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규: `framework/framework-<X>/`(config Properties+AutoConfiguration · 도메인 패키지 · imports FQCN). 확장: 기존 모듈에 패키지/빈 추가 + imports 에 새 autoconfig 줄 추가.
2. `build.gradle`: 능력 전이=`api`, 내부구현=`implementation`, 호스트/선택 의존=`compileOnly`(+test 에 동일 모듈 추가). **BOM 밖 새 라이브러리만** 카탈로그+ext 핀.
3. `settings.gradle`(신규 모듈) / `imports`(새 autoconfig) 등록 잊지 말 것.
4. 코드 전 **Boot4/Spring7/Jackson3 + 외부 라이브러리 API 를 공식 소스(GitHub raw)로 확정**(메이저 버전업=패키지 이동 잦음, 컴파일 미검증 환경).
5. 오토컨피그: `@AutoConfiguration(afterName=관련 Boot/프레임워크 autoconfig)` + `@ConditionalOnClass/Property` + 빈 `@ConditionalOnMissingBean`. 선택 의존 모듈 연계는 `@ConditionalOnClass(타 모듈 마커)`+`@ConditionalOnBean`(+compileOnly).
6. 검증: `./gradlew :...:compileJava` (+`spotlessApply`).
7. 드롭인: 변경 파일 전부(모듈 폴더 + 변경된 기존 파일 + `settings.gradle`/`imports`/필요 시 카탈로그·문서) → 한 zip, 루트에서 `unzip -o`.


<!-- 갱신 끝 -->
