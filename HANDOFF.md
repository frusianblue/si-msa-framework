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
  - `framework-idempotency` : 정확히-한번/멱등키(`@Idempotent`+`Idempotency-Key`, store=memory|redis). 금융 ★
  - `framework-i18n` : 메시지 외부화/다국어(`MessageSource`+`MessageResolver`, `ErrorCode` 로케일 해석)
  - `framework-idgen` : 채번 — Snowflake `IdGenerator`(분산 PK) + 업무코드 `CodeGenerator`(테이블 기반, DataSource 있을 때만)
  - `framework-client` : 외부 API 표준 호출(`RestClient` + 타임아웃·재시도·서킷·연계로그·트레이스전파). 새 외부 의존성 0
  - 전체 카탈로그/구축순서/사업유형 프리셋은 `docs/FRAMEWORK_MODULES.md`.
- **보안 완성 모듈(2026-05 추가, 선택형)** — ISMS-P/보안성 심의 대비:
  - `framework-audit` : 접속/감사 로그 표준 적재·조회(`@AuditLog` AOP 영속화 + 로그인 이벤트). store=logging(기본)|jdbc(조회 API)|**kafka**(messaging Outbox 발행, 연동 완료). 새 외부 의존성 0
  - `framework-secure-web` : 웹 보안 필터 — 보안헤더·경로조작 차단·인젝션 스크리닝·CSRF 더블서브밋(XSS 본문은 core). 새 외부 의존성 0
- **데이터/연계 모듈(2026-05~06, 선택형)** — 금융 핵심:
  - `framework-datasource` : 읽기/쓰기 분리 라우팅(`@Transactional(readOnly)`→READ, 그 외 WRITE). `LazyConnectionDataSourceProxy`. 독립 다중 DB 는 미구현(추후). 새 외부 의존성 0
  - `framework-messaging` : Kafka + **Transactional Outbox**(발행자 DB 적재 + 릴레이 SKIP LOCKED) **+ 소비자측 멱등 소비**(`IdempotentEventProcessor`: `x-event-id` 헤더로 중복 배달 1회 처리, 멱등 저장소=framework-idempotency). 새 외부 의존성 0(spring-kafka=BOM)
- **업무 생산성 모듈(2026-06, 선택형)**:
  - `framework-excel` : POI 업/다운로드 — 다운로드 SXSSF 스트리밍·업로드 양식검증(헤더/타입/필수/길이·패턴, 행별 오류수집). **POI 5.5.1 = BOM 밖 유일 신규 의존성**(implementation, 비노출)
  - `framework-batch` : Spring Batch 6 실행(`JobLaunchSupport`=JobOperator 래핑+run.id)·표준 로깅 리스너·**Quartz cron 스케줄**(yaml 선언만으로 Job 기동). 새 외부 의존성 0(BOM)
  - `framework-notification` : 메일/SMS/알림톡 **채널 추상화**(`NotificationService` 라우팅, 메일=JavaMailSender, SMS·알림톡=벤더 SPI+기본 로깅구현). 새 외부 의존성 0(BOM)
- **규제특화 모듈(2026-06, 선택형 — 해당 사업만)**:
  - `framework-mfa` : **2단계 인증** — TOTP(RFC 6238)·OTP(`OtpSender` SPI: SMS/메일/알림톡)·**ISMS-P 일회용 복구코드**(SHA-256). security 로그인이 `MfaGate` SPI 로 2단계 분기(미사용 시 단일단계 그대로, 완전 하위호환). 챌린지 store=memory|redis(멀티 인스턴스는 redis 필수), 등록 store=memory|jdbc. **새 외부 의존성 0**(Base32/HOTP/TOTP/복구코드 전부 JDK `javax.crypto`/`SecureRandom`)

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
- **벤더 SPI + 기본 로깅구현 패턴(notification/file)**: 프레임워크는 로깅용 기본 구현만 제공, 서비스가 동일 타입 빈 등록 시 `@ConditionalOnMissingBean(타입)` 으로 교체. "켰는데 조용히 로그만" = 벤더 빈 미등록 점검 포인트.
- **2단계 인증(framework-mfa) — security 에 SPI 추가**: framework-security 에 **`MfaGate`(인터페이스)·`MfaTicket`(record)·`LoginOutcome`(sealed) 신설**. `LoginService` 는 nullable `MfaGate` 를 **3번째 생성자(9-arg)**로 받음 — 기존 5-arg/8-arg 생성자는 null 위임으로 유지(하위호환 깨지 말 것). `AuthAutoConfiguration` 의 loginService 빈은 `ObjectProvider<MfaGate>` 로 선택 주입. **MFA 미의존/비활성이면 MfaGate 빈이 없어 단일단계 로그인 그대로**(non-MFA JSON 동일).
- **`AuthController#login` 반환형이 `ApiResponse<Object>` 로 변경**: MFA 필요 시 토큰 대신 `MfaTicket` 을 data 로 반환(메시지 "2단계 인증이 필요합니다."). 토큰 발급 케이스의 JSON 형태는 기존과 동일.
- **MFA 경로 보안 경계**: 2단계 검증은 아직 JWT 가 없으므로 `/api/v1/auth/mfa/**`(기존 permitAll 매처 `/api/*/auth/**` 에 포함, **SecurityAutoConfiguration 무수정**). 등록/관리는 `/api/v1/mfa/**`(인증 필요). 현재 사용자=JWT subject=`CurrentUserProvider.getCurrentUser()`.
- **MFA 챌린지 저장소 = 로그인 1·2단계 사이 단기 상태** → 인메모리는 멀티 파드에서 1단계/2단계가 다른 파드로 가면 무력 → **멀티 인스턴스는 `challenge.store.type=redis` 필수**. 등록(enrollment)은 영속 → 운영은 `enrollment.store.type=jdbc`(`mfa-postgres.sql`). `LoginAuditEvent.Type` 에 MFA_CHALLENGE/SUCCESS/FAILURE/ENROLLED/DISABLED 추가, `LoginAuditListener` 는 실패 판정을 `name().endsWith("FAILURE")` 로 일반화(미래 이벤트 대비). **새 외부 의존성 0**(TOTP/Base32/복구코드 전부 JDK).
- **`util` vs `support` 패키지 구분(코어 컨벤션)**: `core/util` = **상태 없는 순수 정적 헬퍼**(스프링/요청 컨텍스트 무의존) → SI 공통 유틸은 여기. `<module>/support` = **그 모듈 전용·컨텍스트 결합 헬퍼**(`security/support/ClientIpResolver`, `mybatis/support/CurrentUserProvider`, `secureweb/support/SecureWebResponder`, `audit/support/AuditContext`). **core 에는 support 없음** — 범용 유틸을 support 로 만들지 말 것. util 은 빈/오토컨피그 없음 → `imports` 무변경.
- **SI 공통 유틸(2026-06, `core/util`)**: 외국인등록번호는 2020-10 이후 체크섬 폐지 → `isValidForeignerNo` 는 형식만(체크섬 검증 금지). 음력/대체공휴일은 양력 변환표 필요 → `HolidayUtils` 가 자동계산 안 함, `Set<LocalDate> extraHolidays` 주입식(특일정보 API/사내 휴일표). 범용 `StringUtils`/`CollectionUtils` 류는 Spring 표준 사용(재발명 금지). JSON 은 `JsonUtils`(Jackson 3) — `JacksonConfig` 와 규칙 동일, `com.fasterxml.*` import 금지.
- **JUnit Platform launcher 필수(Gradle 9, 전 모듈 공통)**: `spring-boot-starter-test` 는 `junit-platform-launcher` 를 전이하지 않고, Gradle 9 + 최신 JUnit Platform 에서는 `useJUnitPlatform()` 도 launcher 를 자동 주입하지 않는다. 누락 시 **테스트가 있는 모듈**에서 `OutputDirectoryCreator not available ... unaligned versions of junit-platform-engine and junit-platform-launcher` 로 테스트 **발견(discover) 단계에서 통째 실패**(어서션 이전). → 루트 `build.gradle` 의 `subprojects { dependencies { testRuntimeOnly 'org.junit.platform:junit-platform-launcher' } }` 로 전 모듈 일괄 적용(버전은 Boot BOM 관리). 지금까지 안 터진 건 core 에 테스트가 없었기 때문 — 첫 테스트(`CoreUtilsTest`) 추가로 드러남. 새 모듈에 테스트 넣을 때 재발 주의.

## 7. 현재 상태 / 다음 작업
- 보안 골격(비번 정책·BCrypt·로그인 잠금 memory/redis·키 전략·인가 분리)까지 일단락.
- **공통기능 토대 4종 완료**(idempotency·i18n·idgen·client) — 선택형, 3단 토글.
- **보안 완성(ISMS-P) 완료**: framework-security 확장(비번 **만료/이력**·**동시로그인 제어**) + **framework-audit**(logging|jdbc|**kafka**) + **framework-secure-web**.
- **데이터/연계(금융 핵심) 완료**: **framework-datasource**(읽기/쓰기 분리) · **framework-messaging**(Outbox 발행+릴레이 **+ 소비자측 멱등 소비**) · audit↔messaging(`store.type=kafka`) 연동.
- **업무 생산성 3종 완료**: **framework-excel**(POI 스트리밍/양식검증) · **framework-batch**(Batch6+Quartz) · **framework-notification**(메일/SMS/알림톡 채널).
- **규제특화 시작**: **framework-mfa**(2단계 인증 — TOTP/OTP + ISMS-P 복구코드). security 로그인에 `MfaGate` SPI 로 2단계 분기 연결(미사용 시 단일단계 그대로). 새 외부 의존성 0.
- ⚠️ 위 신규/확장 모듈은 작성 환경 제약(Maven Central/Gradle 배포 차단)으로 **여기선 gradle 컴파일 미검증** → 받는 쪽에서 `./gradlew :framework:framework-<X>:compileJava` + `spotlessApply` 확인(단, framework-excel 은 사용자 환경에서 BUILD SUCCESSFUL 확인됨). 최신 세션 상세·켜는 법·함정은 `HANDOFF_SUMMARY.md`.
- **다음 우선순위**: (1) **규제특화 잔여**(pki/hsm/recon/egov, 해당 사업만) 또는 **관측(observability)** — 분산추적은 core 에 micrometer-tracing-otel 보유, 메트릭/로그 표준화·대시보드가 후보. (2) (선택) idempotency 에 `JdbcIdempotencyStore` 추가(현재 memory/redis만). (3) 이후 게이트웨이/k8s/CI-CD 멀티서비스화. (상세 순서는 `docs/FRAMEWORK_MODULES.md` 4절)
- **기존 미해결(유효)**: user-service 통합 테스트 0개 보강(Testcontainers-PostgreSQL), 관리자 초기화 인가 단일 소스 통일, 운영 프로파일 redis/`key-strategy` 명시.

## 8. 문서 갱신 규칙
- **문서 역할**: `HANDOFF_SUMMARY.md` = *세션 한 장*(휘발) — 직전에 한 것·바로 다음 할 것·이번에 새로 밟은 함정. 매 세션 통째로 새로 씀. **`HANDOFF.md`(이 문서)** = *누적 정본*(영속) — 구조·원칙·함정·현재상태의 전체 그림. 구조/원칙/함정이 바뀔 때만 갱신.
- **새 세션 시작 워크플로**: 새 대화에 `HANDOFF_SUMMARY.md` 를 먼저 제시(즉시 이어하기) → 더 깊은 맥락 필요하면 `HANDOFF.md` 참조. (둘은 보완 관계: SUMMARY 로 연결, HANDOFF 로 전체 파악)
- **세션을 넘길 때마다** `HANDOFF_SUMMARY.md` 의 `<!-- 갱신 -->` 구간을 새로 쓴다. 양식은 **`HANDOFF_SUMMARY_TEMPLATE.md`** 를 복사해 `〈…〉` 만 채운다(B 절을 갱신 구간에 붙여넣기). 템플릿 A 절(고정 베이스라인: 완료 모듈·다음 후보·고정 함정·레시피)은 구조가 바뀐 세션에만 갱신. 새 모듈 추가 등 구조가 바뀐 세션이면 `HANDOFF.md`(1·6·7절)도 함께 갱신.
- 라이브러리/플러그인 **버전을 바꾸면** `gradle/libs.versions.toml`(단일 소스) → `STACK.md` 표 갱신.
- 구조/원칙/함정이 바뀌면 **이 문서**를, 사용법/데모가 바뀌면 `README.md` 를 갱신. 모듈 카탈로그/토글은 `docs/FRAMEWORK_MODULES.md`.
