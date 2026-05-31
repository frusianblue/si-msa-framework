# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**금융 핵심 데이터/연계 3종**을 끝냈다 — **framework-datasource**(읽기/쓰기 분리 라우팅), **framework-messaging**(Transactional Outbox + Kafka 릴레이), 그리고 **audit↔messaging 연동**(`store.type=kafka` 싱크). DB 범위는 *읽기/쓰기 분리까지*로 정리하고, **서로 다른 독립 DB 다중 연결은 미구현(추후 필요 시)** 로 합의.

## 최종 갱신
- 일자: 2026-05-31 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **신규 `framework/framework-datasource/`** ([선택], `framework.datasource.routing.enabled=false` 기본). 새 외부 의존성 0.
  - `RoutingDataSource`(AbstractRoutingDataSource) — `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` 로 WRITE/READ 분기. `DataSourceContextHolder`(ThreadLocal) 로 강제 라우팅(고급, try/finally 필수).
  - `DataSourceRoutingAutoConfiguration` — **`@AutoConfiguration(before = DataSourceAutoConfiguration.class)`** 로 Boot보다 먼저 평가 → `@Primary` 라우팅 DataSource 등록 → Boot 기본 DS 백오프. `LazyConnectionDataSourceProxy` 로 감싸 readOnly 확정 이후 물리 커넥션 획득.
  - `write.url`(필수) + `read.url`(선택, 비우면 READ→WRITE 폴백). Hikari 수기 빌드.
- **신규 `framework/framework-messaging/`** ([선택], `framework.messaging.enabled=false` 기본). **버전 0**(spring-kafka 는 Boot BOM 관리).
  - `OutboxEventPublisher.publish(topic, aggregateType, aggregateId, eventType, payload)` — 호출자 트랜잭션에 참여(JdbcTemplate)해 outbox 에 INSERT(비즈니스 커밋과 원자적). payload 직렬화는 core 가 노출하는 Jackson 3 `JsonMapper` 빈 사용.
  - `OutboxRepository`(JdbcTemplate, `FOR UPDATE SKIP LOCKED`) · `OutboxRelay`(SmartLifecycle 자체 스케줄러, 전역 @EnableScheduling 미의존; 한 폴링=한 트랜잭션, 동기 발행 후 PUBLISHED, 건별 예외는 삼켜 배치 롤백 방지).
  - 토글 분리: `messaging.enabled`(발행자, Kafka 불필요·DB 적재만) vs `outbox.relay.enabled`(KafkaTemplate/릴레이, PostgreSQL 전제).
  - DDL: `resources/db/messaging/outbox-postgres.sql`(서비스 Flyway 로 복사). payload/headers 는 `TEXT`(H2-PG모드 호환).
- **audit↔messaging 연동**(audit 모듈 내부 변경만): `KafkaAuditEventSink`(Outbox 로 발행, 실패는 삼킴) + `AuditAutoConfiguration` 에 kafka 싱크를 jdbc/logging 앞에 추가(우선순위 kafka→jdbc→logging). `@AutoConfiguration(afterName="...MessagingAutoConfiguration")` + `@ConditionalOnClass/@ConditionalOnBean(OutboxEventPublisher)`. audit `build.gradle` 에 `compileOnly project(':framework:framework-messaging')`. `AuditProperties.kafka.topic`(기본 `audit-events`).
- **문서/등록**: `settings.gradle`(datasource·messaging include) · `STACK.md` 3.2 에 spring-kafka 행(BOM 관리) · `docs/FRAMEWORK_MODULES.md`(진행현황·2.3/2.5 카탈로그·금융 프리셋) 갱신.

## 현재 상태 (적용/검증)
- 신규/변경 파일 모두 repo 반영. 정적 점검 통과(중괄호/괄호 균형, 패키지/임포트 FQCN 일치, **Jackson2 import 0**, 싱크 우선순위 순서, publish 시그니처 일치).
- ⚠️ **실제 gradle 컴파일 미검증**(작성 환경 Maven Central 차단). 받는 쪽에서:
  - `./gradlew :framework:framework-datasource:compileJava`
  - `./gradlew :framework:framework-messaging:compileJava`
  - `./gradlew :framework:framework-audit:compileJava`
  - 권장: `./gradlew spotlessApply`.
- **트랜잭션 매니저는 누구도 직접 정의 안 함** → Boot 자동구성에 위임(단일 `@Primary` DataSource 기준 `DataSourceTransactionManager`). datasource OFF=단일DS, ON=라우팅DS를 감쌈. idgen/messaging 릴레이는 그 매니저 위에 자기 `TransactionTemplate`(REQUIRES_NEW)만 생성.

## 켜는 법 (application.yml)
```yaml
framework:
  datasource:
    routing:
      enabled: true
      write: { url: jdbc:postgresql://primary:5432/sidb, username: ${DB_USER}, password: ${DB_PASSWORD} }
      read:  { url: jdbc:postgresql://replica:5432/sidb, username: ${DB_USER}, password: ${DB_PASSWORD} }  # 생략 시 WRITE 폴백
  messaging:
    enabled: true
    kafka: { bootstrap-servers: kafka:9092 }
    outbox:
      relay: { enabled: true }   # 발행 워커(릴레이) 켠 인스턴스에서만. PostgreSQL 전제.
  audit:
    enabled: true
    store: { type: kafka }       # kafka 쓰려면 서비스가 audit+messaging 둘 다 의존
    kafka: { topic: audit-events }
```

## 바로 다음 할 일 (Next)
1. 받는 쪽에서 **3개 모듈 컴파일 확인** + `spotlessApply`. messaging 릴레이는 PostgreSQL 환경에서만 켤 것(SKIP LOCKED).
2. **업무 생산성** — framework-excel(POI 스트리밍/양식검증) · framework-batch(Spring Batch+스케줄러) · framework-notification(mail/sms/알림톡 채널 추상화).
   - 또는 **messaging 소비자측**: 멱등 소비(컨슈머) — Kafka 헤더 `x-event-id` 를 키로 기존 **framework-idempotency** 와 연계.
3. 이후: 규제특화(pki/mfa/hsm/recon/egov) → observability → 게이트웨이/k8s/CI-CD 멀티서비스화. (상세 순서 `docs/FRAMEWORK_MODULES.md` 4절)

### (보류) 독립 다중 DB
- 한 서비스가 **서로 다른 DB 여러 개**를 붙는 구성은 미구현. 필요해지면 DB별로 `DataSource`+`SqlSessionFactory`/`JdbcTemplate`+`DataSourceTransactionManager`+`@MapperScan(sqlSessionFactoryRef=...)` 세트가 필요(보일러플레이트는 framework-datasource 가 명명 DS 맵으로 흡수, DB-매퍼 매핑만 서비스가 선언). 두 DB 원자성은 XA 대신 **Outbox/Saga**.
- 운영 replica 가 여러 대면: `read.url` 을 LB/VIP 또는 pgjdbc 멀티호스트 URL 로(코드 변경 없음). 앱 레벨 다중 read 노드 라운드로빈은 미지원(enum 확장 필요).

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **Boot 4 패키지 이동**: `DataSourceAutoConfiguration` 은 **`org.springframework.boot.jdbc.autoconfigure.*`**(구 `...autoconfigure.jdbc.*` 아님). 4.0.6 API 문서로 확정. compileOnly `starter-jdbc` 에 있어 `before=` 참조 가능.
- **Jackson 3 매퍼 = `tools.jackson.databind.json.JsonMapper`**(extends `tools.jackson.databind.ObjectMapper`). Boot 가 자동구성, core `JacksonConfig`(`JsonMapperBuilderCustomizer`)가 공통 규칙 입힘. `writeValueAsString` 은 Jackson 3 에서 **unchecked** 예외 → try/catch 불필요. (기존 함정 재확인: `com.fasterxml.jackson.databind/core` import 금지.)
- **spring-kafka 는 Boot 4 BOM 관리** → `libs.versions.toml` **버전 핀 금지**(버전 0 유지). STACK.md 3.2(BOM 관리)에 행만.
- **읽기/쓰기 라우팅엔 LazyConnectionDataSourceProxy 필수**. 안 감싸면 트랜잭션 시작 시 커넥션을 먼저 잡아 readOnly 확정 전이라 WRITE 고정.
- **한 `@Transactional` 안에서 write+read 혼용 불가**(라우팅은 트랜잭션 단위 1회 결정). readOnly=true 직후 read 는 **복제 지연(replica lag)** 가능 → 직후 정합성 중요한 읽기는 write 트랜잭션 안에 두거나 `DataSourceContextHolder.set(WRITE)`.
- **Outbox 발행자는 호출자 트랜잭션에 참여**(JdbcTemplate→DataSourceUtils). 트랜잭션 밖 호출은 원자성 미보장(debug 로그). 릴레이 발행은 ack 확인 후 PUBLISHED(at-least-once) — **소비자는 멱등 처리 전제**(`x-event-id`).
- **audit kafka 싱크는 우아한 축소**: `store.type=kafka` 인데 messaging 미의존/미활성이면 `OutboxEventPublisher` 빈이 없어 kafka 싱크 비활성 → jdbc/logging 폴백(예외 없음). "kafka 로 설정했는데 조용히 logging" 가능 → 운영 점검 포인트.
- **트랜잭션 매니저 새로 정의 금지** — Boot 자동구성(단일 @Primary DS)에 위임이 정답. 모듈은 `PlatformTransactionManager` **주입**만(idgen/messaging 패턴).
- (기존) **새 모듈은 `settings.gradle` 등록 필수** · 필터에서 BusinessException 금지(디스패처 이전) · "core 가 노출하니 다 된다" 가정 주의(직접 쓰는 web/jdbc 계열은 `compileOnly` 명시) · bash 중괄호 확장 `{a,b}` 미동작 → `for` 루프.

## 모듈 추가 레시피 (검증된 반복 절차)
1. `framework/framework-<X>/` 생성: `config`(Properties+AutoConfiguration) · 도메인 패키지 · `resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`(FQCN 등록).
2. `build.gradle`: `api project(':framework:framework-core')`(+필요 시 다른 framework 모듈) + starter 는 `compileOnly`(이미 core 가 노출하면 생략). BOM 밖 새 버전 의존성 지양(BOM 관리 라이브러리는 버전 미명시).
3. **`settings.gradle` 에 include 추가**(잊지 말 것).
4. 코드 작성 전 **Boot 4/Spring 7/Jackson 3 변경 API 확인**(`...jdbc.autoconfigure.DataSourceAutoConfiguration`, `tools.jackson.databind.json.JsonMapper`, `HttpHeaders`, starter-aop→starter-aspectj 등). 모르면 추측 말고 공식 API 문서/소스로 확인(컴파일 미검증 환경이므로 FQCN 오류 = 빌드 깨짐).
5. 오토컨피그: `@AutoConfiguration` + `@ConditionalOnClass(모듈마커)` + `@ConditionalOnProperty(framework.<x>.enabled=true)` + 빈은 `@ConditionalOnMissingBean`. 3단 impl 은 `store.type`/세부 토글로 분기. 다른 모듈 빈에 의존하면 `@AutoConfiguration(afterName=...)` + `@ConditionalOnBean` (선택 의존은 `afterName` 문자열로).
6. 검증: `./gradlew :framework:framework-<X>:compileJava` (Configuration Cache 꼬이면 `--no-configuration-cache` 또는 `clean`).
7. 드롭인 배포: 모듈 폴더 + 변경 파일 + **완성 `settings.gradle`** 을 한 zip 에 담아 루트에서 `unzip -o`.

<!-- 갱신 끝 -->
