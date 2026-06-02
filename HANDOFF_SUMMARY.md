# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**`framework-saga` 신설 — 경량 오케스트레이션 Saga 엔진.** 중앙 코디네이터가 단계 커맨드를 **기존 messaging Outbox 로 발행**(상태변경과 한 트랜잭션=원자적), 참여 서비스 리플라이로 전진/완료, 실패 시 완료 단계를 **역순 보상**. 상태는 **JDBC 영속**(`saga_instance`/`saga_step`), **스턱/재기동 복구 폴러**(`FOR UPDATE SKIP LOCKED`, 옵트인). 전송·신뢰성·멱등 소비는 messaging 재사용 — 본 모듈은 **오케스트레이션만** 더한다. 순수 코어(상태머신)는 Spring/Jackson 무의존으로 분리해 JDK **15/15** 실행검증. 3단 토글·`@ConditionalOnMissingBean`·imports·settings 등록까지 컨벤션 그대로. **새 외부 의존성 0**. 빌드 통과, `[this-escape]` 경고 1건 수정.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **신규 모듈 `framework-saga`** (settings.gradle include + imports 등록).
- **순수 코어**(Spring/Jackson 무의존, `com.company.framework.saga`): `SagaDefinition`(+Builder)/`SagaRegistry`/`SagaPlanner`(순서·역순보상 결정, 순수함수) · `SagaOrchestrator`(상태머신: start/onReply/redriveWithinTx/findStuck) · 열거형 `SagaPhase`/`SagaOutcome`/`SagaStatus`/`SagaStepStatus` · 레코드 `SagaInstance`/`SagaStepRecord` · SPI `SagaStore`/`SagaCommandPublisher`/`SagaTransactionRunner` · `SagaHeaders`.
- **Spring 어댑터**: `jdbc/JdbcSagaStore`(JdbcTemplate; 단계 적재는 **UPDATE→없으면 INSERT**=벤더 UPSERT 회피; `findStuck` 은 `FOR UPDATE SKIP LOCKED`) · `messaging/OutboxSagaCommandPublisher`(OutboxEventPublisher 래핑; 컨텍스트 `readValue(.,Object.class)` 로 풀어 페이로드, 이중따옴표 회피) · `messaging/SagaReplyConsumer`(앱 `@KafkaListener` 위임용; `x-headers` JSON 파싱) · `config/SpringSagaTransactionRunner`(TransactionTemplate) · `recovery/SagaRecoveryRelay`(SmartLifecycle, OutboxRelay 미러).
- **오토컨피그** `config/SagaAutoConfiguration`: 3단 — 1단 `@ConditionalOnClass({OutboxEventPublisher, JdbcTemplate})`, 2단 `framework.saga.enabled=true`, 3단 `recovery.enabled=true`. 빈 전부 `@ConditionalOnMissingBean`. `SagaReplyConsumer` 는 `@ConditionalOnClass(ConsumerRecord)`. `SagaRegistry` 는 `ObjectProvider<SagaDefinition>` 수집.
- **`config/SagaProperties`**: `framework.saga.{enabled,instance-table,step-table,reply-topic,step-timeout,recovery.{enabled,poll-interval-ms,batch-size}}`.
- **DDL** `db/saga/saga-postgres.sql`(`saga_instance` + `saga_step`, `UNIQUE(saga_id,step_index,phase)`, deadline 부분 인덱스).
- **build.gradle**(messaging 패턴): `api core` · `compileOnly messaging`(커맨드 발행, **비전이→의존 서비스가 재선언**) · `compileOnly spring-kafka`(ConsumerRecord) · `compileOnly spring-boot-starter-jdbc` · 새 버전 0(Boot BOM).
- **테스트** `SagaOrchestratorTest`(JUnit5, 인메모리 페이크 6케이스=해피패스·단일/다단계 역순보상·보상없는단계 스킵·멱등 중복·종료/미존재 무시·재구동).
- **문서**: 모듈 `README.md` + 본 핸드오프 4종.

## 현재 상태 (적용/검증)
- **gradle 빌드 통과**(`:framework:framework-saga:compileJava` BUILD SUCCESSFUL). `[this-escape]` 경고(SagaRegistry 생성자에서 `this::register`)는 생성자에서 필드 직접 채우기로 **수정 완료**(`-Xlint:this-escape` 무경고 재확인).
- **순수 JDK(JDK21) 상태머신 15/15**: 시작 발행·정방향 전진·COMPLETED·단일/다단계 역순 보상·보상없는 단계 스킵·멱등 중복 무시·종료/미존재 무시·deadline 재구동.
- 정적 점검: 패키지=디렉터리, 괄호 균형, **`com.fasterxml.jackson.databind/core` 0건**(Jackson3 read 는 `readValue(.,Map/Object.class)` 만), FQCN(JdbcTemplate/TransactionTemplate/SmartLifecycle/ConsumerRecord/ObjectProvider) messaging 과 동일.
- ⚠️ Spring 어댑터의 **gradle 단위테스트는 받는 쪽에서**(작성 환경 Maven Central 차단). 받는 쪽: `./gradlew :framework:framework-saga:compileJava :test (+spotlessApply)`.

## 켜는 법
- 오케스트레이터 서비스: `framework.messaging.enabled=true` + `outbox.relay.enabled=true`(어느 인스턴스군이든 1곳 이상) + `framework.saga.enabled=true`(+ `recovery.enabled=true`, PostgreSQL 전제). `reply-topic`/`step-timeout` 설정.
- `build.gradle` 에 `framework-messaging` **명시**(compileOnly 비전이). DDL 을 서비스 마이그레이션으로 복사.
- `@Bean SagaDefinition` 으로 단계 선언 → `sagaOrchestrator.start(type, ctxJson)`. 리플라이 토픽 `@KafkaListener` 에서 `sagaReplyConsumer.handle(record)`.
- 참여 서비스: 커맨드 처리 후 `x-saga-reply-topic` 으로 `x-saga-id/step/phase` + `x-saga-outcome` 회신(자신의 Outbox 권장). **`(saga-id, step)` 기준 멱등 필수**.

## 바로 다음 할 일 (Next)
1. 받는 쪽 `:framework:framework-saga:test (+spotlessApply)` — 오케스트레이터 6케이스 그린 확인.
2. (선택·devops) 실DB(H2/PostgreSQL) 통합테스트: `JdbcSagaStore` upsert/`SKIP LOCKED`·복구 폴러 재구동 e2e. 종료 saga 행 정리 잡(스케줄러).
3. (선택) 예제 흐름(예: 주문→재고→결제) user-service/샘플 서비스에 실적용 — 커맨드/리플라이 토픽 + 참여자 멱등 `(saga-id,step)` 검증.
4. (선택·강화) 단계별 타임아웃/보상 재시도 정책, 컨텍스트 부분 병합(현재 단순 치환), 보상 실패(FAILED) 운영 알림.
5. **그릇 정비** 잔여: 게이트웨이 런타임 점검(CORS preflight/rate-limit 429) · k8s 멀티서비스/CI-CD.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **saga = 오케스트레이션 전용.** 전송/신뢰성/멱등 소비는 messaging(Outbox + IdempotentEventProcessor)이 이미 제공 → **재발명 금지**. 짧은 2~4단계 흐름은 코레오그래피(messaging 단독)로 충분, 오케스트레이션 엔진은 과설계 주의.
- **saga 커맨드 멱등 키 = `(x-saga-id, x-saga-step)`** — `x-event-id` 아님. 복구 재구동 시 같은 단계를 재발행하며 x-event-id 는 매번 새로 생성되므로, 참여 서비스는 saga-id+step 으로 멱등해야 한다.
- **커맨드와 상태변경은 한 트랜잭션**(`SagaTransactionRunner`=TransactionTemplate). Outbox 적재가 그 안에서 일어나 커밋돼야만 커맨드가 나간다(유령 커맨드 없음). 리플라이는 인터셉터/디스패처가 아니라 **앱 소유 `@KafkaListener`** → `SagaReplyConsumer`(messaging 의 IdempotentEventProcessor 와 동일 철학: 컨테이너는 앱 소유).
- **중복 리플라이 멱등 = 단계 status≠PENDING 이면 무시**(영속 상태로 판정, 별도 멱등 저장소 불필요). 종료(COMPLETED/COMPENSATED/FAILED)·미존재 saga 리플라이도 무시.
- **보상은 완료 단계만 역순**, 보상 미정의 단계는 스킵. **보상 실패는 자동 회복 불가 → `FAILED`**(운영자 개입).
- **Outbox 커스텀 헤더는 단일 `x-headers` JSON** 으로 실린다(개별 Kafka 헤더는 x-event-id/type/aggregate-type 뿐). saga 상관관계는 그 JSON 안에 담아 보내고 소비 측은 파싱한다.
- **벤더 UPSERT(ON CONFLICT) 금지** 재확인 — 단계 적재는 `UPDATE→0이면 INSERT`. JDBC 다중 인스턴스 잠금은 `FOR UPDATE SKIP LOCKED`(PostgreSQL 전제).
- **`[this-escape]`(Java 21 lint)**: 생성자에서 오버라이드 가능한 인스턴스 메서드 참조(`this::register`) 금지 → 생성자에선 필드를 직접 채운다.
- (지난) `compileOnly` 비전이(테스트/의존 서비스 재선언) · 새 의존성 0(Boot BOM) · 신규 모듈 settings/imports 등록 · JsonUtils=Jackson3(`readValue(.,Class/TypeReference)`) · 필터/리스너 컨테이너는 앱 소유.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. **순수 로직(상태머신·코덱·선점)은 Spring/Jackson 무의존 코어로 분리**하고 SPI(스토어/발행/트랜잭션)로 경계를 그으면 인메모리 페이크로 JDK 단독 검증 가능(이번 사례).
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(messaging/kafka/jdbc/redis/web). **테스트나 의존 서비스가 그 compileOnly 클래스를 쓰면 재선언**.
3. `settings.gradle`/`imports` 등록(신규 모듈이면 필수).
4. 코드 전 Boot4/Spring7/Jackson3 + 통합 대상(messaging Outbox/IdempotencyStore 등) 실제 시그니처 확인. 조용히 틀리는 로직은 순수 JDK 또는 H2 로 실행 검증.
5. 오토컨피그: `@AutoConfiguration` + `@ConditionalOnClass/Property` 3단 + 빈 `@ConditionalOnMissingBean`. 백그라운드 워커는 SmartLifecycle + 자체 스케줄러(OutboxRelay/SagaRecoveryRelay 컨벤션).
6. 검증: `./gradlew :...:compileJava (+:test) (+spotlessApply)` — 경고(`this-escape` 등)도 확인.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 5종 동기화.

<!-- 갱신 끝 -->
