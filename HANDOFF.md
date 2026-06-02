# HANDOFF.md — 인계 전반 (SI MSA 공통 프레임워크)

> 목적: 처음 인계받는 사람이 **구조·실행·설계 원칙·함정**을 한 번에 파악하도록 한다.
> 문서 지도: **이 문서**(인계 전반) · `README.md`(사용 가이드/데모) · `STACK.md`(라이브러리/버전) · `HANDOFF_SUMMARY.md`(세션 단위 한 장 — 매 세션 갱신) · `HANDOFF_SUMMARY_TEMPLATE.md`(그 양식·고정 베이스라인) · `docs/FRAMEWORK_MODULES.md`(전 기능 on/off 모듈 설계서·카탈로그).
> 최종 갱신: 2026-06-01 · 갱신자: <!-- 채우기 -->

---

## 1. 한눈에 보는 구조
Spring Boot 4.0.6 / Java 21 / Spring Cloud 2025.1.1(Oakwood) / MyBatis 기반 멀티모듈.

**공통 프레임워크(라이브러리)** — `framework/`
- `framework-core` : 응답표준(`ApiResponse`)·에러(`ErrorCode`/`BusinessException`)·페이징·AOP(`@AuditLog`)·로깅/트레이스·XSS·캐시
- `framework-mybatis` : MyBatis 연동, `CurrentUserProvider`(감사필드/현재 사용자)
- `framework-security` : 인증추상화·JWT·TokenStore·동적 RBAC·비밀번호 정책(+**만료/이력**)·로그인 시도 제한·**동시(중복)로그인 제어**
- `framework-openapi` / `framework-commoncode` / `framework-file` / `framework-file-s3` : 선택형
- `framework-redis` : 선택형. Redis 기반 `TokenStore` + `LoginAttemptService`(다중 인스턴스 공유)
- **공통기능 모듈(2026-05 추가, 선택형)** — 토대 4종:
  - `framework-idempotency` : 정확히-한번/멱등키(`@Idempotent`+`Idempotency-Key`, store=memory|redis|**jdbc**, **응답 재생(replay) 모드** 옵트인). 금융 ★
  - `framework-i18n` : 메시지 외부화/다국어(`MessageSource`+`MessageResolver`, `ErrorCode` 로케일 해석)
  - `framework-idgen` : 채번 — Snowflake `IdGenerator`(분산 PK) + 업무코드 `CodeGenerator`(테이블 기반, DataSource 있을 때만)
  - `framework-client` : 외부 API 표준 호출(`RestClient` + 타임아웃·재시도·서킷·연계로그·트레이스전파). 새 외부 의존성 0
  - 전체 카탈로그/구축순서/사업유형 프리셋은 `docs/FRAMEWORK_MODULES.md`.
- **보안 완성 모듈(2026-05 추가, 선택형)** — ISMS-P/보안성 심의 대비:
  - `framework-audit` : 접속/감사 로그 표준 적재·조회(`@AuditLog` AOP 영속화 + 로그인 이벤트). store=logging(기본)|jdbc(조회 API)|**kafka**(messaging Outbox 발행, 연동 완료). 새 외부 의존성 0
  - `framework-secure-web` : 웹 보안 필터 — 보안헤더·경로조작 차단·인젝션 스크리닝·CSRF 더블서브밋(XSS 본문은 core). 새 외부 의존성 0
- **데이터/연계 모듈(2026-05~06, 선택형)** — 금융 핵심:
  - `framework-datasource` : (1) 읽기/쓰기 분리 라우팅(`@Transactional(readOnly)`→READ, 그 외 WRITE, `LazyConnectionDataSourceProxy`) (2) **독립 다중 DB**(`multi.*`, DB키별 `<k>DataSource`/`<k>SqlSessionFactory`/`<k>SqlSessionTemplate`/`<k>TransactionManager` 세트 동적 등록). 두 기능은 **상호 배타**(둘 다 `@Primary` DataSource→충돌, 기동 시 fail-fast). 새 외부 의존성 0
  - `framework-messaging` : Kafka + **Transactional Outbox**(발행자 DB 적재 + 릴레이 SKIP LOCKED) **+ 소비자측 멱등 소비**(`IdempotentEventProcessor`: `x-event-id` 헤더로 중복 배달 1회 처리, 멱등 저장소=framework-idempotency). 새 외부 의존성 0(spring-kafka=BOM)
  - `framework-saga` : **경량 오케스트레이션 Saga**(2026-06 추가) — 중앙 코디네이터가 단계 커맨드를 **messaging Outbox 로 발행**(상태변경과 한 트랜잭션=원자적), 참여 서비스 리플라이로 전진/완료, 실패 시 완료 단계를 **역순 보상**. 상태 **JDBC 영속**(`saga_instance`/`saga_step`), **스턱/재기동 복구 폴러**(`FOR UPDATE SKIP LOCKED`, 옵트인). 전송·신뢰성·멱등 소비는 messaging 재사용 — 본 모듈은 **오케스트레이션만**. 리플라이는 앱 `@KafkaListener`→`SagaReplyConsumer`, 참여자 멱등 키=`(saga-id,step)`. 순수 코어(상태머신)는 Spring/Jackson 무의존. 새 외부 의존성 0(kafka/jdbc=compileOnly, BOM)
- **업무 생산성 모듈(2026-06, 선택형)**:
  - `framework-excel` : POI 업/다운로드 — 다운로드 SXSSF 스트리밍·업로드 양식검증(헤더/타입/필수/길이·패턴, 행별 오류수집). **POI 5.5.1 = BOM 밖 유일 신규 의존성**(implementation, 비노출)
  - `framework-batch` : Spring Batch 6 실행(`JobLaunchSupport`=JobOperator 래핑+run.id)·표준 로깅 리스너·**Quartz cron 스케줄**(yaml 선언만으로 Job 기동). 새 외부 의존성 0(BOM)
  - `framework-notification` : 메일/SMS/알림톡 **채널 추상화**(`NotificationService` 라우팅, 메일=JavaMailSender, SMS·알림톡=벤더 SPI+기본 로깅구현). 새 외부 의존성 0(BOM)
- **규제특화 모듈(2026-06, 선택형 — 해당 사업만)**:
  - `framework-mfa` : **2단계 인증** — TOTP(RFC 6238)·OTP(`OtpSender` SPI: SMS/메일/알림톡)·**ISMS-P 일회용 복구코드**(SHA-256). security 로그인이 `MfaGate` SPI 로 2단계 분기(미사용 시 단일단계 그대로, 완전 하위호환). 챌린지 store=memory|redis(멀티 인스턴스는 redis 필수), 등록 store=memory|jdbc. **새 외부 의존성 0**(Base32/HOTP/TOTP/복구코드 전부 JDK `javax.crypto`/`SecureRandom`)
- **운영/관측 모듈(2026-06, 선택형)**:
  - `framework-observability` : 관측 표준 — **공통 메트릭 태그**(`MeterRegistryCustomizer` 로 service/env/version+extra, 모든 레지스트리)·**구조화(JSON) 로그**(Boot4 네이티브 `logging.structured.format` ecs/logstash/gelf, 인코더 라이브러리 불필요)·**OTel 익스포터 표준**(메트릭 OTLP·트레이스 OTLP, 기본 off). core 의 `micrometer-tracing-bridge-otel`/`MdcTraceFilter` 위에 얹음. 프로퍼티성 표준값은 `EnvironmentPostProcessor`(로깅/액추에이터 초기화 전)로 주입, 빈은 공통태그 커스터마이저뿐. **새 외부 의존성 0**(레지스트리/익스포터는 호스트가 runtimeOnly opt-in, 모두 Boot BOM 관리). 토글 `framework.observability.enabled=false`

**서비스** — `services/`
- `gateway` (:8000, WebFlux+Resilience4j) · `user-service` (:8080) · `admin-service` (:8081)

## 2. 핵심 설계 원칙
- **계약은 공통, 구현은 프로젝트**: 예) 인증은 `Authenticator` 인터페이스만 공통, 프로젝트가 구현(user-service 의 `DbAuthenticationProvider`). 구현 빈이 있으면 `AuthAutoConfiguration` 이 공통 로그인(`LoginService`+`AuthController`)을 자동 활성화.
- **빈 등록은 `@AutoConfiguration` + `.imports`** (컴포넌트 스캔 밖). 신규 프레임워크 빈도 동일 패턴. 프로젝트는 같은 타입 빈을 등록해 `@ConditionalOnMissingBean` 으로 덮어쓸 수 있다.
- **선택형 모듈은 의존성 추가로 활성화**: 모듈을 의존성에 넣고 `type` 류 프로퍼티로 켠다(예: TokenStore/LoginAttempt 의 `type=redis`). property 상호배제라 오토컨피그 순서에 의존하지 않음.
- **동적 인가(RBAC)는 DB 기반**: `resources`/`role_resources` 매핑을 `DynamicAuthorizationManager` 가 평가. **매핑이 없으면 인증된 사용자에게 허용**(deny-by-default 아님)에 유의.

## 3. 보안 골격 (현재 상태)
- **JWT + TokenStore**: access(jti 부여)/refresh(1회용 회전), 로그아웃 시 jti 블랙리스트. TokenStore `type`: memory(기본)/redis/jdbc. 권한 authority 는 `ROLE_` 접두사(JwtProvider 부여, DB `role_name` 도 `ROLE_ADMIN`/`ROLE_USER`).
- **비밀번호**: `PasswordPolicy`(min-length·문자종류 검증) + `BcryptEnforcingPasswordEncoder`(신규 인코딩=BCrypt, `allow-noop` 토글). 회원가입/본인변경/관리자초기화 플로우에 정책 연결됨. 저장값은 `{bcrypt}...`(또는 로컬 시드 `{noop}...`).
- **로그인 시도 제한**: `LoginAttemptService` — in-memory(기본, 단일 인스턴스) / redis(다중 인스턴스 공유). 임계치 초과 시 `429 LOGIN_LOCKED`. 실패 카운트 키 정책 `key-strategy`: `login-id`(기본) | `login-id-and-ip`(IP 는 `ClientIpResolver` 가 `X-Forwarded-For`→`getRemoteAddr()` 폴백으로 추출, **XFF 위조 가능**이라 신뢰 프록시 환경 한정).
- **비밀번호 변경 인가 분리** (user-service):
  - `PATCH /api/v1/users/me/password` — 본인만(현재 비번 필요, 컨텍스트 사용자 해석)
  - `PATCH /api/v1/users/{id}/password/reset` — 관리자 강제 초기화(`@PreAuthorize("hasRole('ADMIN')")`, 현재 비번 불요)
- **비밀번호 만료/이력**(ISMS-P, framework-security 확장): `PasswordLifecycleService` — 직전 N개 재사용 금지·변경주기 만료 판정(`framework.security.password.{expiry,history}`). 기능 off 면 no-op, 이력 없는 레거시 사용자는 강제 만료 안 함. 업무코드가 비번 변경 시점에 호출.
- **동시(중복) 로그인 제어**(framework-security 확장): `ConcurrentSessionService` — 사용자당 세션 수 제한, 초과 시 `EVICT_OLDEST`(기존 토큰 블랙리스트) 또는 `REJECT`(409). `framework.security.concurrent-session.*`, store=memory|jdbc. 기본 off.
- **감사/접속 로그**(framework-audit, 선택): `@AuditLog` 메서드 + 로그인 성공/실패/로그아웃 이벤트를 표준 적재. security→audit 는 이벤트(`LoginAuditEvent`) 단방향(순환 회피). store=logging|jdbc(jdbc 일 때 `GET /api/v1/audit/logs`).
- **웹 보안 필터**(framework-secure-web, 선택): 보안헤더·경로조작·인젝션 스크리닝·CSRF 더블서브밋. 필터 계층(디스패처 이전)이라 거부 응답은 `SecureWebResponder` 가 표준 JSON 직접 기록. CSRF 는 Spring Security(csrf disable)와 독립.
- **dev-auth**: 개발 초기 토큰 없이 권한만 바꿔 호출하는 우회 모드(`local,dev` 프로파일). 운영 비활성.

## 4. 빌드 / 실행 (요약 — 자세한 데모는 README "처음 실행하기")
```bash
./gradlew spotlessApply && ./gradlew clean build          # 빌드(테스트 생략: -x test)
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local'   # :8080
```
- **로컬**: 프로파일 `local` **명시 필수**. H2 인메모리 + Flyway 자동 마이그레이션/시드(시드 계정 `admin/admin123`, `hong/hong123`). 외부 DB/Redis 불필요(토큰·잠금 memory).
- **게이트웨이(:8000)**: 라우트가 `lb://user-service` 인데 디스커버리 의존성이 없어 로컬에선 미해석 → 로컬은 서비스에 직접 호출. k8s 에선 `http://user-service:8080` 직접 URI 로 전환.

## 5. 환경 / 배포
- **운영 프로파일**: PostgreSQL 드라이버 + Flyway(`db/migration` 만, `db/seed-local` 제외). 비번 `allow-noop:false`(평문 차단).
- **컨테이너**: `deploy/docker/Dockerfile` — jar 를 CI(Jenkins)가 먼저 `bootJar` → `JAR_FILE` 로 주입, 레이어 추출 후 엔트리포인트 `org.springframework.boot.loader.launch.JarLauncher`. 비루트 실행.
- **k8s**: `deploy/k8s/*` — `user-service replicas: 2`, actuator 프로브(`/actuator/health/liveness|readiness`), 설정은 configmap/secret.
- **다중 인스턴스(중요)**: replicas≥2 이므로 운영은 `login-attempt.type=redis` + `token-store.type=redis` 필수. in-memory 는 인스턴스별이라 잠금/세션 공유 안 됨(잠금 우회 가능).
- **CI/CD**: `deploy/cicd/Jenkinsfile` 한 곳에서 빌드·테스트·게이트(spotlessCheck/jacoco/dependencyCheck/sonar)·이미지·롤아웃.

## 6. 컨벤션 & 함정 (되돌리지 말 것)
- enum `ErrorCode.Common` 상수 추가 시 종결 `;` 위치 주의(직전 줄을 `,` 로).
- 프레임워크 빈은 `@AutoConfiguration` + `META-INF/.../AutoConfiguration.imports` 로 등록. 신규 모듈도 동일.
- `@PreAuthorize` 는 `@EnableMethodSecurity`(SecurityAutoConfiguration)로 전역 적용. authority 에 `ROLE_` 접두사 있으므로 `hasRole('ADMIN')`(접두사 중복 X).
- `BcryptEnforcingPasswordEncoder`: `encode()` 는 `{bcrypt}` 접두사 포함 저장, `matches()` 가 접두사로 알고리즘 식별 → 저장값에서 접두사 임의 제거 금지. `allow-noop=false` 면 `{noop}` 매칭 거부(예외 아님, false).
- 로그인 잠금: in-memory 는 임계치 전 카운터 만료 없음 / redis 는 `la:fail` 에 lock-duration 롤링 윈도우 TTL(의도적 차이). 분산 환경은 redis.
- IP 키: `X-Forwarded-For` 위조 가능 → 신뢰 프록시가 헤더 세팅하는 환경에서만 `login-id-and-ip` 의미. 외부 직접 노출 시 헤더 신뢰 끄기.
- **API 파괴적 변경 이력**: (a) 회원가입 `password` 필수가 됨(이전 누락) — 비번 없는 바디는 400. (b) 구 `PATCH /users/{id}/password` 제거 → `/me/password` + `/{id}/password/reset` 로 분리.
- `LoginService.login(command)` 단일인자 시그니처 유지(하위호환). IP 결합은 `login(command, clientIp)`.
- **신규 모듈 등록 필수**: 모듈 폴더를 추가하면 반드시 `settings.gradle` 에 `include 'framework:framework-<X>'` 를 넣는다. 누락 시 `project '<X>' not found in project ':framework'`. 드롭인 배포는 모듈 폴더 + 완성 `settings.gradle` 을 한 zip 으로.
- **Boot 4 모듈 분리 주의**: `org.springframework.boot.http.client.*`(ClientHttpRequestFactoryBuilder/Settings)는 `spring-boot-starter-web` 컴파일 경로로 안 딸려온다 → RestClient 타임아웃은 spring-web 의 `SimpleClientHttpRequestFactory` 로 설정. `RestTemplateBuilder` 도 `org.springframework.boot.restclient` 로 이동.
- **Spring 7 `HttpHeaders` 변경**: 더 이상 `MultiValueMap` 미구현 → `containsKey()`→`containsHeader()`, `keySet()`→`headerNames()`, `forEach()/entrySet()` 제거. 헤더 다루는 신규 코드는 이 API 로.
- **새 외부 의존성 0 기조**: 공통기능 모듈은 web/jdbc/redis 를 `compileOnly`(호스트 제공)로 받고, 서킷브레이커 등도 자체 구현 → BOM 밖 새 라이브러리/버전 추가 없음(`libs.versions.toml`/`STACK.md` 무변경).
- **⚠️ 이 스택은 Jackson 3 (`tools.jackson.*`)**: `com.fasterxml.jackson.core/databind` import 금지(클래스패스에 없음 — 컴파일 에러). 애너테이션(`@JsonInclude` 등)만 `com.fasterxml.jackson.annotation` 유지 OK. 필터/인프라 레벨의 단순 JSON 응답은 Jackson 빈 주입 대신 수기 직렬화가 견고(`SecureWebResponder`). (STACK.md 5절)
- **필터에서 `BusinessException` 던지지 말 것**: 디스패처 이전 필터 예외는 `GlobalExceptionHandler`(@RestControllerAdvice)가 못 잡음 → 표준 JSON 을 직접 기록(`SecureWebResponder`).
- **모듈 간 의존 단방향**: framework-security 는 audit 을 모름 → 로그인 감사는 `LoginAuditEvent`(ApplicationEvent)로만 전달(audit 이 구독). 반대 방향 import 금지(순환).
- **CSRF 는 Spring Security 와 독립**: 보안 체인이 `csrf().disable()`(stateless JWT)라 secure-web 의 더블서브밋은 자체 구현. CSRF 쿠키는 JS 가 읽어야 하므로 **HttpOnly 금지**. 인젝션 스크리닝은 오탐 가능 → 기본 off, 운영은 log-only 부터(JSON 본문 미검사).
- (작업 환경) bash 중괄호 확장 `{a,b}` 미동작 → `for` 루프.
- **Boot 4 autoconfigure 패키지 분리(일반 패턴)**: Boot 의 자동구성이 모듈별 `org.springframework.boot.<module>.autoconfigure.*` 로 이동했다 — jdbc(`...jdbc.autoconfigure.DataSourceAutoConfiguration`), batch(`...batch.autoconfigure.BatchAutoConfiguration`), quartz(`...quartz.autoconfigure.QuartzAutoConfiguration`), mail(`...mail.autoconfigure.MailSenderAutoConfiguration`). `@AutoConfiguration(afterName=...)` 에 이 FQCN 사용. **추측 금지 — 공식 소스(GitHub raw)로 확인**(컴파일 미검증 환경).
- **Spring Batch 6 패키지 대이동(Boot 4)**: `core.job.{Job,JobExecution,JobExecutionException}` · `core.job.parameters.{JobParameters,JobParametersBuilder,RunIdIncrementer}` · `core.listener.JobExecutionListener` · `core.launch.{JobOperator, JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException}`. `core.{Entity,ExitStatus}` 는 그대로. **`JobLauncher` 는 6.0 deprecated → `JobOperator`**(JobOperator extends JobLauncher; 실행=`start(Job,JobParameters)`). Boot 가 JobOperator 자동구성. 배치 메타테이블 필요(`spring.batch.jdbc.initialize-schema` 또는 Flyway).
- **POI(framework-excel)는 Boot BOM 밖** → `libs.versions.toml` 에 `poi` 버전 고정(유일한 BOM 밖 신규 의존성). `poi-ooxml` 은 `implementation`(타입 비노출). **SXSSF 종료는 `close()`**(try-with-resources) — POI 5.5+ 에서 `dispose()` deprecated(close 가 flush+임시파일 삭제). autoSizeColumn 금지(비용).
- **Spring 6/7 메일은 `jakarta.mail.*`**(javax 아님). 메일 클래스 `org.springframework.mail.javamail.*`(starter-mail 전이). JavaMailSender 빈은 `spring.mail.host` 설정 시에만 → `@ConditionalOnBean(JavaMailSender)` 로 우아 비활성.
- **소비자측 멱등(framework-messaging)**: `IdempotencyStore` SPI 에 **`remove(String)` 추가됨(브레이킹)** — 커스텀 구현은 구현 필수. Kafka 헤더명은 `MessagingHeaders` 단일 소스(발행/소비 공유). `putIfAbsent` 성공~핸들러 완료 전 비정상 종료 시 키가 TTL 까지 남아 그 사이 재배달 스킵 가능(at-least-once 한계, TTL 을 재시도 주기보다 길게). **인메모리 멱등은 멀티 인스턴스 컨슈머에서 무력 → redis 필수.**
- **멱등 JDBC 스토어(framework-idempotency, 2026-06)**: `JdbcIdempotencyStore`(store.type=jdbc) — Redis 없이 기존 DataSource 로 다중 인스턴스/재기동 공유. 선점은 **PK(idem_key) 유니크 + INSERT 충돌(`DataIntegrityViolationException`) 캐치**(idgen 채번과 동일 관용, 트랜잭션/락 불요). 만료행은 **선점 직전 동일 키만** 정리 후 INSERT → 동시 정리에도 INSERT 는 하나만 성공(소유자 1개). `saveResult` 는 벤더 UPSERT 금지("UPDATE 먼저, 0행이면 INSERT", MFA 스토어와 동일), `VARCHAR/TIMESTAMP` 만 써 H2/PostgreSQL/Oracle 공통(DDL `db/idempotency-postgres.sql`). `result NULL`(선점만)→`findResult` empty(InMemory 의미 일치). 전체 만료 청소는 운영 잡으로 별도(`DELETE WHERE expires_at<=now`).
- **멱등 응답 재생(replay, framework-idempotency, 2026-06)**: `framework.idempotency.replay.enabled=true`(기본 off=기존 409, 하위호환). 완료된 동일 키 요청은 **저장된 응답(상태/콘텐츠타입/본문)을 재생**, 처리중 409, 최초 통과 후 `afterCompletion` 에서 캡처→`saveResult`. **인터셉터는 응답을 교체 못 하므로** 캡처는 필터 전제: `IdempotencyResponseFilter`(재생 모드에서만 등록, 평범한 `@Bean`+`@Order(LOWEST_PRECEDENCE)`=secure-web 컨벤션)가 **헤더 있는 요청만** `ContentCachingResponseWrapper` 로 감싸고(`finally` 에서 `copyBodyToResponse()`), 인터셉터가 `WebUtils.getNativeResponse(...)` 로 본문 회수. 저장 포맷은 **`status\ncontentType\nbase64(body)` 고정 셰이프**(`ResponseSnapshot`) — body 를 개행 없는 Base64 로 인코딩해 앞 두 개행만으로 무손실 분리(임의 바이너리/문자셋 안전, Jackson/이스케이프 불요). **실패는 캐시 금지**: `ex!=null||status>=500||wrapper==null` → `remove`(선점 해제, 재시도 재처리). GlobalExceptionHandler 가 처리한 예외는 `ex`가 null 이라 **status≥500 으로 판정**. 본문 메모리 버퍼링이라 작은 JSON 응답 권장(스트리밍 부적합).
- **`compileOnly` 는 test 클래스패스로 전이되지 않는다(전 모듈 공통)**: main 을 `compileOnly`(jdbc/web/redis)로 받는 모듈에서 **테스트가 그 클래스를 직접 import 하면** `testCompileJava` 가 `package … does not exist` 로 실패(main 은 통과). 해결: 해당 의존을 test 소스셋에 **재선언**(`testImplementation`). idempotency: `JdbcIdempotencyStoreTest`(JdbcTemplate/DriverManagerDataSource)→`testImplementation spring-boot-starter-jdbc`, `IdempotencyInterceptorTest`(HandlerMethod/ContentCachingResponseWrapper)→`testImplementation spring-boot-starter-web`, 실DB 검증은 `testRuntimeOnly com.h2database:h2`. "테스트 API 모듈마다 선언"과 같은 결(test 소스셋은 main 의 compileOnly 를 못 봄).
- **벤더 SPI + 기본 로깅구현 패턴(notification/file)**: 프레임워크는 로깅용 기본 구현만 제공, 서비스가 동일 타입 빈 등록 시 `@ConditionalOnMissingBean(타입)` 으로 교체. "켰는데 조용히 로그만" = 벤더 빈 미등록 점검 포인트.
- **2단계 인증(framework-mfa) — security 에 SPI 추가**: framework-security 에 **`MfaGate`(인터페이스)·`MfaTicket`(record)·`LoginOutcome`(sealed) 신설**. `LoginService` 는 nullable `MfaGate` 를 **3번째 생성자(9-arg)**로 받음 — 기존 5-arg/8-arg 생성자는 null 위임으로 유지(하위호환 깨지 말 것). `AuthAutoConfiguration` 의 loginService 빈은 `ObjectProvider<MfaGate>` 로 선택 주입. **MFA 미의존/비활성이면 MfaGate 빈이 없어 단일단계 로그인 그대로**(non-MFA JSON 동일).
- **`AuthController#login` 반환형이 `ApiResponse<Object>` 로 변경**: MFA 필요 시 토큰 대신 `MfaTicket` 을 data 로 반환(메시지 "2단계 인증이 필요합니다."). 토큰 발급 케이스의 JSON 형태는 기존과 동일.
- **MFA 경로 보안 경계**: 2단계 검증은 아직 JWT 가 없으므로 `/api/v1/auth/mfa/**`(기존 permitAll 매처 `/api/*/auth/**` 에 포함, **SecurityAutoConfiguration 무수정**). 등록/관리는 `/api/v1/mfa/**`(인증 필요). 현재 사용자=JWT subject=`CurrentUserProvider.getCurrentUser()`.
- **MFA 챌린지 저장소 = 로그인 1·2단계 사이 단기 상태** → 인메모리는 멀티 파드에서 1단계/2단계가 다른 파드로 가면 무력 → **멀티 인스턴스는 `challenge.store.type=redis` 필수**. 등록(enrollment)은 영속 → 운영은 `enrollment.store.type=jdbc`(`mfa-postgres.sql`). `LoginAuditEvent.Type` 에 MFA_CHALLENGE/SUCCESS/FAILURE/ENROLLED/DISABLED 추가, `LoginAuditListener` 는 실패 판정을 `name().endsWith("FAILURE")` 로 일반화(미래 이벤트 대비). **새 외부 의존성 0**(TOTP/Base32/복구코드 전부 JDK).
- **`util` vs `support` 패키지 구분(코어 컨벤션)**: `core/util` = **상태 없는 순수 정적 헬퍼**(스프링/요청 컨텍스트 무의존) → SI 공통 유틸은 여기. `<module>/support` = **그 모듈 전용·컨텍스트 결합 헬퍼**(`security/support/ClientIpResolver`, `mybatis/support/CurrentUserProvider`, `secureweb/support/SecureWebResponder`, `audit/support/AuditContext`). **core 에는 support 없음** — 범용 유틸을 support 로 만들지 말 것. util 은 빈/오토컨피그 없음 → `imports` 무변경.
- **SI 공통 유틸(2026-06, `core/util`)**: 외국인등록번호는 2020-10 이후 체크섬 폐지 → `isValidForeignerNo` 는 형식만(체크섬 검증 금지). 음력/대체공휴일은 양력 변환표 필요 → `HolidayUtils` 가 자동계산 안 함, `Set<LocalDate> extraHolidays` 주입식(특일정보 API/사내 휴일표). 범용 `StringUtils`/`CollectionUtils` 류는 Spring 표준 사용(재발명 금지). JSON 은 `JsonUtils`(Jackson 3) — `JacksonConfig` 와 규칙 동일, `com.fasterxml.*` import 금지.
- **JUnit Platform launcher 필수(Gradle 9, 전 모듈 공통)**: `spring-boot-starter-test` 는 `junit-platform-launcher` 를 전이하지 않고, Gradle 9 + 최신 JUnit Platform 에서는 `useJUnitPlatform()` 도 launcher 를 자동 주입하지 않는다. 누락 시 **테스트가 있는 모듈**에서 `OutputDirectoryCreator not available ... unaligned versions of junit-platform-engine and junit-platform-launcher` 로 테스트 **발견(discover) 단계에서 통째 실패**(어서션 이전). → 루트 `build.gradle` 의 `subprojects { dependencies { testRuntimeOnly 'org.junit.platform:junit-platform-launcher' } }` 로 전 모듈 일괄 적용(버전은 Boot BOM 관리). 지금까지 안 터진 건 core 에 테스트가 없었기 때문 — 첫 테스트(`CoreUtilsTest`) 추가로 드러남. 새 모듈에 테스트 넣을 때 재발 주의.
- **모듈마다 `testImplementation 'org.springframework.boot:spring-boot-starter-test'` 필수**: 루트 `subprojects` 가 깔아주는 건 **실행 런처**(`junit-platform-launcher`)뿐 — 테스트가 import 하는 **API**(`org.junit.jupiter.api.*`, `org.assertj.*`)는 모듈 `build.gradle` 에서 직접 선언해야 한다. 누락 시 `package org.junit.jupiter.api does not exist` / `package org.assertj.core.api does not exist` / `cannot find symbol method assertThat` 로 **테스트 컴파일 실패**(메인 컴파일은 무관, 별개 통과). 버전은 Boot BOM 이 잡으니 명시 불필요. 테스트 있는 기존 13개 모듈 모두 보유 — 새 모듈에 테스트 넣을 때 한 줄 같이 추가(observability 작성 시 이 줄을 빠뜨려 19건 컴파일 에러 발생, 추가로 해소).
- **Boot 4 actuator/micrometer 패키지 재편(관측)**: `MeterRegistryCustomizer` 가 Boot 3.x 의 `org.springframework.boot.actuate.autoconfigure.metrics` 에서 **Boot 4 는 `org.springframework.boot.micrometer.metrics.autoconfigure`** 로 이동했다(`framework-observability` 에서 사용). 추측 금지 — actuator 계열 FQCN 은 공식 API 문서로 확인. `MeterRegistry`/`Tag`/`Tags` 는 그대로 `io.micrometer.core.instrument.*`.
- **구조화 로그는 빈이 아니라 프로퍼티/EPP 레벨**: `logging.structured.format.*` 는 로깅 시스템이 ApplicationContext 보다 먼저 읽으므로 빈으로는 늦다 → `framework-observability` 는 `ObservabilityEnvironmentPostProcessor`(ConfigData 이후·로깅 초기화 전 실행, `getOrder=LOWEST_PRECEDENCE`)가 표준값을 `addLast`(최저 우선순위)로 심는다 = 앱 값이 항상 우선. EPP 등록은 `META-INF/spring.factories` 의 `org.springframework.boot.env.EnvironmentPostProcessor` 키(오토컨피그 `.imports` 아님).
- **OTLP 트레이스 키 이중 컨벤션(Boot 4)**: core 가 쓰는 **브리지 방식**(`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`)은 `management.otlp.tracing.endpoint`. 신규 공식 스타터(`spring-boot-starter-opentelemetry`)는 `management.opentelemetry.tracing.export.otlp.endpoint`. 모듈은 core 일치(브리지 키)를 기본. 메트릭 OTLP 는 `management.otlp.metrics.export.{enabled,url}`. **메트릭 OTLP 레지스트리와 OTel 메트릭 브리지 동시 사용 시 중복** — 한쪽만.
- **관측 모듈도 새 외부 의존성 0**: 코드가 참조하는 건 micrometer-core/actuator/boot(전부 core `api` 전이)뿐 → build.gradle 은 `api project(core)` + configuration-processor 만. prometheus/otlp 레지스트리·OTel 익스포터는 **클래스 직접 참조 없음**(프로퍼티/런타임 classpath 로만) → 호스트가 `runtimeOnly` opt-in(README 레시피). 카탈로그/STACK 무변경.
- **Saga = 오케스트레이션만(2026-06, `framework-saga`)**: 전송/신뢰성(유실·중복)·멱등 소비는 **messaging(Outbox + IdempotentEventProcessor)이 이미 제공** → 재발명 금지. 짧은 2~4단계 흐름은 코레오그래피(messaging 단독)로 충분, 오케스트레이션 엔진은 과설계 주의. saga 는 그 위에 **중앙 상태 + 단계/보상 정의 + 코디네이터**만 더한다.
- **Saga 커맨드 멱등 키 = `(x-saga-id, x-saga-step)`**(x-event-id 아님): 복구 폴러가 deadline 지난 단계를 재발행할 때 x-event-id 는 매번 새로 생성되므로, 참여 서비스는 saga-id+step 으로 멱등해야 한다. 커맨드는 Outbox 로 **상태변경과 한 트랜잭션**(`SagaTransactionRunner`=TransactionTemplate) 발행 → 커밋돼야만 나감(유령 커맨드 없음). 리플라이는 디스패처가 아니라 **앱 소유 `@KafkaListener`** → `SagaReplyConsumer`(messaging IdempotentEventProcessor 와 동일 철학). 중복/종료/미존재 리플라이는 단계 status≠PENDING·terminal 판정으로 무시(영속 상태 기반, 별도 멱등 저장소 불필요). 보상은 완료 단계만 역순·보상 미정의 단계 스킵·**보상 실패는 FAILED**(운영자). Outbox 커스텀 헤더는 단일 `x-headers` JSON 으로 실리므로 saga 상관관계는 그 안에 담아 보내고 소비 측이 파싱. 단계 적재는 **벤더 UPSERT 금지→UPDATE→0이면 INSERT**, 잠금 `FOR UPDATE SKIP LOCKED`(PostgreSQL).
- **`[this-escape]`(Java 21 lint)**: 생성자에서 오버라이드 가능한 인스턴스 메서드 참조(`this::register` 등)를 넘기면 하위클래스 초기화 전 `this` 누출 경고. 생성자에서는 메서드 호출 대신 **필드를 직접** 채운다(`SagaRegistry` 에서 발생→수정). 빌드는 통과해도 경고 0 을 목표.
- **독립 다중 DB = 동적 빈 등록(2026-06, `framework-datasource` `multi.*`)**: DB 키별 4빈 세트(`<k>DataSource`/`<k>SqlSessionFactory`/`<k>SqlSessionTemplate`/`<k>TransactionManager`)는 키 개수가 런타임 설정이라 정적 `@Bean` 으로 못 쓴다 → **`ImportBeanDefinitionRegistrar`** 로 동적 등록. **`BeanDefinitionRegistryPostProcessor`(BDRPP)가 아니라 Registrar 인 이유**: BDRPP 는 `ConfigurationClassPostProcessor` 이후라 `@AutoConfiguration` before-순서를 못 지킴 → Boot 의 `@ConditionalOnMissingBean(DataSource)` 가 우리 `@Primary` 정의를 못 보고 자기 DataSource 를 만들어 충돌. Registrar 는 config-class 파싱 중 실행돼 before-순서 보존 → Boot 백오프. `@AutoConfiguration(before=DataSourceAutoConfiguration, beforeName="…MybatisAutoConfiguration")`.
- **routing 과 multi 는 상호 배타**: 둘 다 `@Primary` DataSource 를 등록 → 동시 활성 시 어느 쪽이 primary 인지 모호. Registrar 가 `framework.datasource.routing.enabled` 를 읽어 **기동 시 fail-fast**(`MultiDataSourcePlan.assertNotConflictingWithRouting`). 순수 판단 로직(primary 키 결정·충돌·빈이름 규약)은 Spring 무의존 `MultiDataSourcePlan` 으로 분리해 JDK 단독 13케이스 검증.
- **`@MapperScan` 은 프레임워크가 못 한다**: 앱 패키지(매퍼 위치)를 알아야 하므로 앱이 DB 별로 선언 — `@MapperScan(basePackages="…", sqlSessionFactoryRef="<k>SqlSessionFactory")`. 보조 DB 트랜잭션도 `@Transactional("<k>TransactionManager")` 로 **매니저 명시**(primary 만 무인자 `@Transactional`). 매퍼 패키지는 DB 별로 겹치지 않게 분리(같은 매퍼를 두 팩토리가 잡으면 모호).
- **primary 키 규칙**: 소스 1개면 자동 primary, **2개 이상이면 `primary` 필수**(누락/빈칸/미존재 키 → fail-fast). `@Primary` 빈이 Boot 기본 DS·Flyway·이름없는 `@Autowired DataSource` 를 해소. **Flyway 는 primary DB 에만** 자동 적용 → 보조 DB 마이그레이션은 앱 책임.
- **독립 SqlSessionFactory 도 단일 DB 동작 복제**: 각 팩토리에 single-DB MyBatis 기본값(`mapUnderscoreToCamelCase` 등) + 컨텍스트의 모든 `ConfigurationCustomizer`/`Interceptor` 빈(framework-mybatis 감사 인터셉터 포함)을 적용 → 모든 DB 에서 매핑/감사 동작 동일. `ImportBeanDefinitionRegistrar` 는 `EnvironmentAware`/`BeanFactoryAware` 주입 가능 → 빈 공급자(Supplier)에서 `ConfigurableListableBeanFactory` 로 협력자(by-name) 해소.
- **ArchUnit Jackson 규칙은 "이동된 패키지"만 금지(2026-06-03, `framework-archtest`)**: Jackson 3 에서 `jackson-annotations` 는 **`com.fasterxml.jackson.annotation` 에 그대로 유지**된다(databind/core/dataformat/datatype/module 만 `tools.jackson.*` 로 이동). 따라서 `ApiResponse` 의 `com.fasterxml.jackson.annotation.JsonInclude` 는 **정상**이며, 규칙은 이동된 5개 패키지만 금지하고 `...annotation` 은 허용해야 한다(아니면 오탐). 조건부 규칙엔 `.allowEmptyShould(true)`. **새 라이브러리 모듈을 추가하면 `framework-archtest/build.gradle` 에 `testImplementation project(...)` 한 줄을 반드시 추가**(누락 시 ArchUnit 검사 사각지대).
- **client 인터셉터 합성 순서 = Trace→CircuitBreaker→Retry→Logging(바깥→안)**: 서킷이 재시도를 감싸므로 재시도 루프 내부의 중간 5xx 는 서킷에 집계되지 않고 **최종 결과만** 집계된다. → 서킷 단독 검증은 재시도 OFF. 차단은 `CircuitOpenException`(IOException 계열)→RestClient 가 `ResourceAccessException` 으로 래핑(원인 체인 탐색). **WireMock 은 `wiremock-standalone`(shaded)** 으로 — Boot4 가 끌어오는 Jetty 12.1(EE10)·Jackson 3 과 일반 jetty12 모듈이 충돌.

## 7. 현재 상태 / 다음 작업
- 보안 골격(비번 정책·BCrypt·로그인 잠금 memory/redis·키 전략·인가 분리)까지 일단락.
- **공통기능 토대 4종 완료**(idempotency·i18n·idgen·client) — 선택형, 3단 토글.
- **멱등성 확장 완료(2026-06)**: framework-idempotency 에 **JDBC 스토어**(store.type=jdbc, 영속·다중 인스턴스 공유) + **응답 재생(replay) 모드**(중복 시 409 대신 저장 응답 재생, 기본 off=하위호환). SPI/imports/settings 무변경, 새 외부 의존성 0(jdbc/web=compileOnly, H2=test-scope BOM). 코덱·선점·재생 분기는 순수 JDK 실행검증.
- **보안 완성(ISMS-P) 완료**: framework-security 확장(비번 **만료/이력**·**동시로그인 제어**) + **framework-audit**(logging|jdbc|**kafka**) + **framework-secure-web**.
- **데이터/연계(금융 핵심) 완료**: **framework-datasource**(읽기/쓰기 분리 **+ 독립 다중 DB**, 2026-06-03) · **framework-messaging**(Outbox 발행+릴레이 **+ 소비자측 멱등 소비**) · audit↔messaging(`store.type=kafka`) 연동.
- **분산 트랜잭션 오케스트레이션 완료(2026-06)**: **framework-saga** — 경량 오케스트레이션(중앙 상태 + 역순 보상). 단계 커맨드/리플라이는 messaging Outbox 재사용(상태변경과 한 트랜잭션), 상태 JDBC 영속(`saga_instance`/`saga_step`, DDL `db/saga/saga-postgres.sql`), 스턱/재기동 복구 폴러(SKIP LOCKED, 옵트인). 순수 코어 상태머신 JDK **15/15** 실행검증, **gradle 컴파일 통과**(this-escape 경고 1건 수정). 새 외부 의존성 0.
- **독립 다중 DB 완료(2026-06-03)**: framework-datasource 에 `multi.*` 추가 — DB키별 `<k>DataSource`/`<k>SqlSessionFactory`/`<k>SqlSessionTemplate`/`<k>TransactionManager` 세트를 `ImportBeanDefinitionRegistrar` 로 동적 등록(Boot `@ConditionalOnMissingBean(DataSource)` 백오프 유지). routing 과 **상호 배타**(fail-fast). 앱이 `@MapperScan(sqlSessionFactoryRef)`/`@Transactional("<k>TransactionManager")` 배선. 순수 결정/검증 로직(`MultiDataSourcePlan`) JDK **13/13** 실행검증. 새 외부 의존성 0.
- **CORS/Rate-Limit 완료(2026-06)**: 게이트웨이=전역 1선(globalcors + Redis RequestRateLimiter), framework-secure-web=직접 노출 서비스 2선(Spring CorsFilter 옵트인 + 파드-로컬 토큰버킷). 게이트웨이 빌드 통과(런타임 점검 보류).
- **업무 생산성 3종 완료**: **framework-excel**(POI 스트리밍/양식검증) · **framework-batch**(Batch6+Quartz) · **framework-notification**(메일/SMS/알림톡 채널).
- **규제특화 시작**: **framework-mfa**(2단계 인증 — TOTP/OTP + ISMS-P 복구코드). security 로그인에 `MfaGate` SPI 로 2단계 분기 연결(미사용 시 단일단계 그대로). 새 외부 의존성 0.
- **운영/관측 완료**: **framework-observability**(공통 메트릭 태그 `MeterRegistryCustomizer` · Boot4 네이티브 구조화 JSON 로그 · 메트릭/트레이스 OTLP 익스포터 표준, 전부 토글·기본 off). core 의 micrometer-tracing-bridge-otel/MdcTraceFilter 위에 얹음. 프로퍼티성 표준값은 EnvironmentPostProcessor 로 주입(로깅 초기화 전). 새 외부 의존성 0(레지스트리/익스포터는 호스트 runtimeOnly opt-in, Boot BOM 관리). k8s 샘플 `deploy/k8s/observability.yaml`(ServiceMonitor + 스크레이프/프로브).
- **테스트/아키텍처 검증 도입(2026-06-03)**: 신규 **테스트전용 모듈 `framework-archtest`**(ArchUnit 7규칙: 모듈 순환금지·Jackson3 규약·mapper/domain 레이어 격리·`*AutoConfiguration`/`*Properties` 네이밍·필드주입 금지, 전 모듈 main 임포트). + 핵심 알고리즘 단위테스트(JWT `JwtProviderTest`·TOTP/Base32 `TotpTest`/`Base32Test`·RBAC `DynamicAuthorizationManagerTest`·마스킹 `MaskingUtilsTest`) + 오토컨피그 로딩(`MfaAutoConfigurationTest`, client 로딩) + **WireMock(standalone) 서비스간 연동 테스트**(`ClientResilienceWireMockTest`: 503 재시도→200·서킷 OPEN 차단·POST 비재시도). 카탈로그에 archunit 1.4.2·wiremock 3.13.2 추가(**둘 다 test 전용, 런타임 무영향**). 알고리즘 벡터·의존 DAG·7규칙은 작성 환경에서 정적/JDK 교차검증, gradle 테스트 실행은 받는 쪽.
- ⚠️ 위 신규/확장 모듈은 작성 환경 제약(Maven Central/Gradle 배포 차단)으로 **여기선 gradle 컴파일 미검증** → 받는 쪽에서 `./gradlew :framework:framework-<X>:compileJava` + `spotlessApply` 확인(단, framework-excel 은 사용자 환경에서 BUILD SUCCESSFUL 확인됨). observability 의 공통태그 해석 로직은 순수 JDK 로 실행검증 완료(5케이스), 빈/EPP/Boot4 FQCN 은 받는 쪽 컴파일로 확인 필요. 최신 세션 상세·켜는 법·함정은 `HANDOFF_SUMMARY.md`.
- **다음 우선순위**: (1) **규제특화 잔여**(pki/hsm/recon/egov, 해당 사업만). (2) **그릇 정비** — 게이트웨이 **런타임 점검**(CORS preflight `Access-Control-*`·rate-limit 429, 빌드는 통과)·k8s(redis/secret/멀티서비스, observability ServiceMonitor 실배포)·CI-CD 멀티서비스화. (3) (선택) saga 단계별 타임아웃/보상 재시도·컨텍스트 부분 병합·실DB(H2/PostgreSQL) 통합테스트. (4) (선택) 멱등 재생 페이로드 지문(payload hash)로 "같은 키+다른 본문→422" 강제. (상세 순서는 `docs/FRAMEWORK_MODULES.md` 4절)
- **기존 미해결(유효)**: user-service 통합 테스트 0개 보강(Testcontainers-PostgreSQL), 관리자 초기화 인가 단일 소스 통일, 운영 프로파일 redis/`key-strategy` 명시.

## 8. 문서 갱신 규칙
- **문서 역할**: `HANDOFF_SUMMARY.md` = *세션 한 장*(휘발) — 직전에 한 것·바로 다음 할 것·이번에 새로 밟은 함정. 매 세션 통째로 새로 씀. **`HANDOFF.md`(이 문서)** = *누적 정본*(영속) — 구조·원칙·함정·현재상태의 전체 그림. 구조/원칙/함정이 바뀔 때만 갱신.
- **새 세션 시작 워크플로**: 새 대화에 `HANDOFF_SUMMARY.md` 를 먼저 제시(즉시 이어하기) → 더 깊은 맥락 필요하면 `HANDOFF.md` 참조. (둘은 보완 관계: SUMMARY 로 연결, HANDOFF 로 전체 파악)
- **세션을 넘길 때마다** `HANDOFF_SUMMARY.md` 의 `<!-- 갱신 -->` 구간을 새로 쓴다. 양식은 **`HANDOFF_SUMMARY_TEMPLATE.md`** 를 복사해 `〈…〉` 만 채운다(B 절을 갱신 구간에 붙여넣기). 템플릿 A 절(고정 베이스라인: 완료 모듈·다음 후보·고정 함정·레시피)은 구조가 바뀐 세션에만 갱신. 새 모듈 추가 등 구조가 바뀐 세션이면 `HANDOFF.md`(1·6·7절)도 함께 갱신.
- 라이브러리/플러그인 **버전을 바꾸면** `gradle/libs.versions.toml`(단일 소스) → `STACK.md` 표 갱신.
- 구조/원칙/함정이 바뀌면 **이 문서**를, 사용법/데모가 바뀌면 `README.md` 를 갱신. 모듈 카탈로그/토글은 `docs/FRAMEWORK_MODULES.md`.
