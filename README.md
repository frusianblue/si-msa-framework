# SI MSA Common Framework (Spring Boot 4.0.6 / Java 21 / MyBatis)

SI 프로젝트용 MSA 공통 프레임워크 스켈레톤.

## 스택
| 항목 | 버전 |
|------|------|
| Spring Boot | 4.0.6 (Spring Framework 7.0.7) |
| Java | 21 (가상 스레드) |
| MyBatis | mybatis-spring-boot-starter 4.0.1 |
| Spring Cloud | 2025.1.1 (Oakwood) — Gateway |
| 빌드 | Gradle 8.14 (Groovy DSL), 멀티모듈 + 버전 카탈로그(`gradle/libs.versions.toml`) |

## 모듈 구조
```
framework/
  framework-core      공통 응답/예외/페이징/트레이스/로깅/AOP/XSS/시큐어유틸/가상스레드 (auto-config)
  framework-mybatis   MyBatis 공통 설정(카멜케이스 등) + BaseEntity(감사컬럼)
  framework-security  JWT 인증 + DB기반 RBAC(동적 인가) + 메뉴관리 + 시큐어 헤더 (auto-config)
services/
  gateway             Spring Cloud Gateway (라우팅 + CircuitBreaker)
  user-service        샘플 업무 서비스 (프레임워크 사용 예시)
deploy/
  docker/ k8s/ cicd/  런타임 Dockerfile(레이어드/JarLauncher), K8s 매니페스트(프로브/HPA), Jenkins 파이프라인
```
> 위는 핵심 모듈만 표기한 단순도. 선택 모듈과 `services/admin-service`(8081)는 아래 상세 섹션 참고.
> 선택 모듈 전체: `framework-openapi/redis/commoncode/file/file-s3`(기본) · `framework-idempotency/i18n/idgen/client`(토대) · `framework-audit/secure-web`(보안완성) · `framework-datasource/messaging/saga`(데이터·연계) · `framework-excel/batch/notification`(업무 생산성).

## 핵심 설계
- 각 서비스는 `framework-*` 의존성만 추가하면 표준 응답/예외/보안/MyBatis가 **자동 적용**된다
  (Spring Boot auto-configuration `.imports` 방식).
- 모든 응답은 `ApiResponse<T>` 표준 포맷. 예외는 `GlobalExceptionHandler`가 통일 변환.
- 요청마다 `traceId`(MDC)를 부여하고 로그 패턴/응답 헤더에 노출 → MSA 추적.
- Java 21 가상 스레드: `spring.threads.virtual.enabled=true` + `@Async` 가상스레드 executor.
- 보안: `Authorization: Bearer <JWT>` 무상태 인증. `/actuator/**`, `/api/*/auth/**` 는 permitAll.

## 빌드 / 품질 도구
- **버전 단일 소스**: `gradle/libs.versions.toml`. 루트 `build.gradle`의 `ext{}` 브리지로 기존 모듈의 `${...Version}` 참조도 그대로 동작(점진 이관). 상세는 `STACK.md`.
- **Spotless**(Palantir Java Format): 최초 1회 `./gradlew spotlessApply`로 전체 정렬, CI는 `spotlessCheck` 게이트.
- **JaCoCo**: 테스트 후 커버리지 XML 자동 생성 → SonarQube가 수집.
- **테스트 실행(JUnit5)**: 루트 `subprojects`에서 `testRuntimeOnly junit-platform-launcher`를 일괄 제공한다(Gradle 9 + 최신 JUnit Platform은 launcher를 자동 주입하지 않아, 없으면 테스트가 있는 모듈에서 "OutputDirectoryCreator not available … unaligned versions"로 발견 단계 실패). 새 모듈에 테스트를 추가해도 별도 의존성 추가는 불필요.
- **아키텍처 검증(ArchUnit)**: 테스트전용 모듈 `framework-archtest`가 전 모듈을 임포트해 모듈 순환 의존·Jackson 3 규약(`tools.jackson.*`만, 이동된 `com.fasterxml.jackson.*` 금지·`.annotation`은 예외)·mapper/domain 레이어 격리·`*AutoConfiguration`/`*Properties` 네이밍·필드주입 금지를 강제한다(`./gradlew :framework:framework-archtest:test`). **새 라이브러리 모듈 추가 시 `framework-archtest/build.gradle`에 `testImplementation project(...)` 한 줄을 추가**해야 검사에 포함된다.
- **서비스간 연동 테스트(WireMock)**: `framework-client`는 `wiremock-standalone`(Boot4 Jetty12.1/Jackson3 충돌 회피)로 가짜 업스트림을 띄워 재시도/서킷브레이커/비재시도(POST)를 실제 HTTP로 검증한다. archunit/wiremock 모두 **test 전용 → 런타임·배포 산출물 무영향**.
- **OWASP Dependency-Check**: CVSS 7.0+ 발견 시 빌드 실패. `./gradlew dependencyCheckAggregate`.
- **SonarQube**: 정적분석/보안 핫스팟/커버리지.
- **CI = Jenkins**(`deploy/cicd/Jenkinsfile`): Build&Test(Testcontainers+JaCoCo) → 품질 게이트(Spotless/OWASP/Sonar 병렬) → Flyway Validate(운영DB) → 이미지 빌드/푸시 → K8s 롤아웃. (구 `ci-cd.yml`(GitHub Actions)은 레거시 — 사용 시 정리 권장)

## 처음 실행하기

**사전 준비**: JDK 21 만 있으면 된다. 로컬은 H2 인메모리 + Flyway 자동 마이그레이션/시드라 **DB·Redis 등 외부 인프라가 불필요**하다(토큰/로그인 잠금도 로컬은 `memory`). Docker 는 통합테스트(Testcontainers-PostgreSQL)나 이미지 빌드 때만 필요하다.

```bash
# 빌드 (gradlew 동봉 — 별도 gradle 설치 불필요)
./gradlew spotlessApply        # 포맷 정렬(최초/포맷 변경 시)
./gradlew clean build          # 컴파일 + 테스트 + 커버리지   (테스트 생략: build -x test)
```

```bash
# 로컬 기동: 기본 활성 프로파일이 없으므로 local 을 반드시 명시해야 H2+시드가 적용된다.
./gradlew :services:user-service:bootRun  --args='--spring.profiles.active=local'   # → :8080
./gradlew :services:admin-service:bootRun --args='--spring.profiles.active=local'   # → :8081 (다른 터미널)
# 환경변수도 가능: SPRING_PROFILES_ACTIVE=local  /  devtools 포함이라 코드 변경 시 핫리로드
```

```bash
# 동작 확인 — 시드 계정: admin/admin123(ADMIN), hong/hong123(USER)
curl -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"loginId":"admin","password":"admin123"}'
# 같은 loginId 로 5회 실패하면 6번째부터 429(LOGIN_LOCKED)

# 회원가입: password 필수 + 강도 정책(min-length 9, 3종 이상) 충족해야 201, 미달이면 400
curl -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' \
     -d '{"loginId":"kim","password":"Passw0rd!","name":"김","email":"kim@test.com","phone":"010-1234-5678"}'

# 본인 비밀번호 변경(현재 비번 필요) / 관리자 강제 초기화(ADMIN 토큰 필요)
# PATCH /api/v1/users/me/password        body: {"currentPassword","newPassword"}
# PATCH /api/v1/users/{id}/password/reset body: {"newPassword"}        ← 비ADMIN 은 403
```

> **게이트웨이(:8000) 로컬 주의**: 라우트가 `lb://user-service` 인데 디스커버리 의존성이 없어 로컬에선 `lb://` 가 해석되지 않는다. **로컬은 8080/8081 로 서비스에 직접 호출**하고, k8s 에선 `http://user-service:8080` 직접 URI 로 전환해 쓴다.

## 컨테이너 / 배포
```bash
# 런타임 전용 Dockerfile: jar 는 CI(Jenkins)가 먼저 빌드 → JAR_FILE 로 주입(레이어 추출, JarLauncher 기동)
./gradlew :services:user-service:bootJar
docker build -f deploy/docker/Dockerfile \
  --build-arg JAR_FILE=services/user-service/build/libs/*.jar -t user-service .
kubectl apply -f deploy/k8s/
```
> 실제 운영 흐름은 `deploy/cicd/Jenkinsfile` 한 곳에서 빌드·테스트·게이트·이미지·롤아웃을 수행한다.
> **다중 인스턴스 주의**: k8s 매니페스트는 `replicas: 2` 다. 인스턴스가 둘 이상이면 로그인 잠금/토큰을 공유해야 하므로 운영 프로파일에서 `framework.security.login-attempt.type=redis` 와 `token-store.type=redis` 를 켠다(기본 `memory` 는 인스턴스별이라 잠금 우회 가능).

## 확장 가이드
- 새 업무 서비스: `services/` 아래 모듈 추가 → `settings.gradle`에 include → `framework-*` 의존.
- 업무별 에러코드: `ErrorCode` 인터페이스를 각 서비스 enum으로 구현.
- 설정 외부화/서비스 디스커버리가 필요하면 Spring Cloud Config / Eureka 또는 K8s 네이티브(Service DNS) 추가.


## 보안 / 로깅 / AOP / 시큐어코딩 (추가됨)

### framework-security — 권한관리
- **JWT 무상태 인증** + 토큰 roles → 권한 매핑
- **DB 기반 동적 인가**: `resources(URL패턴-메서드)` ↔ `roles` 매핑을 DB에서 읽어
  `DynamicAuthorizationManager`가 요청마다 판단. 권한 변경 시 코드/배포 없이 테이블만 수정 후
  `SecurityMetadataService.reload()` 호출로 반영.
- **메뉴 관리**: 역할별 메뉴 매핑 → `GET /api/v1/menus/me` 로 로그인 사용자 권한에 맞는 메뉴 트리 반환.
- **메서드 보안**: `@PreAuthorize("hasRole('ADMIN')")` 사용 가능(`@EnableMethodSecurity`).
- **시큐어 응답 헤더**: HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, CSP.
- 테이블: `users / roles / user_roles / resources / role_resources / menus / role_menus`
  (스키마+시드: user-service `schema.sql` / `data.sql`).

### framework-core — 로깅 / AOP / 시큐어코딩
- **공통 로깅**: `logback-common.xml`(traceId 패턴, 일자별 롤링, AUDIT 전용 파일). 서비스는 `<include>` 만.
- **요청/응답 로깅 필터**: 본문 로깅 + 민감정보(비밀번호/토큰/주민번호/카드) 자동 마스킹.
- **AOP**: `ExecutionTimeAspect`(슬로우 로직 탐지), `@AuditLog`+`AuditLogAspect`(감사추적: 누가/언제/무엇).
- **XSS 방어**: `XssRequestFilter`(파라미터/헤더 HTML 이스케이프).
- **시큐어코딩 유틸**: `SecureUtils`(경로조작 방어 파일명 정제, ORDER BY 컬럼 화이트리스트로 SQL인젝션 방어).

### framework-core — SI 공통 유틸 (`core/util`, 2026-06 추가)
한국 공공/금융 SI 에서 반복되는 정적 헬퍼. **빈/오토컨피그 없음**(그냥 클래스), core 가 전이 노출되므로 별도 의존 추가 없이 어디서나 사용. **새 외부 의존성 0**(전부 JDK), JSON 만 Jackson 3(`tools.jackson.*`).
- **검증** — `KoreanRegNoUtils`(주민번호 체크섬·외국인번호 형식·사업자번호·법인번호), `ValidationUtils`(이메일·휴대폰/유선·카드 Luhn).
- **마스킹** — `MaskingUtils` 확장(이름/이메일/전화 + 주민번호·카드·계좌·주소).
- **날짜/영업일** — `DateUtils`(yyyyMMdd 변환·만나이·기간), `HolidayUtils`(주말+양력 고정공휴일 자동, 음력·대체공휴일은 주입식 영업일 계산).
- **금액/숫자** — `MoneyUtils`(천단위 콤마·한글 금액 "일금 …원정"·반올림/절사/올림).
- **한글** — `HangulUtils`(초성 추출·초성 검색·자모 분해/조합·조사 선택·한↔영 자판 변환).
- **해시/인코딩** — `HashUtils`(SHA-256/512 hex·Base64 표준/URL-safe·Hex).
- **JSON** — `JsonUtils`(Jackson 3 기반 null-safe `toJson`/`fromJson`/`TypeReference`, `JacksonConfig` 규칙 동일).
> 범용 `StringUtils`/`CollectionUtils` 류는 재발명하지 않고 Spring 표준을 사용. 회귀 테스트: `CoreUtilsTest`. ⚠️ 외국인등록번호는 2020-10 이후 체크섬 폐지 → 형식만 검증. ⚠️ 음력/대체공휴일은 양력 변환표가 필요해 자동계산하지 않고 주입(특일정보 API/사내 휴일표 권장).

### 소스 취약점 점검 (루트 build.gradle)
- **OWASP Dependency-Check**: `./gradlew dependencyCheckAggregate` (CVSS 7.0+ 발견 시 빌드 실패).
  리포트: `build/reports/dependency-check-report.html`, 오탐 억제: `config/dependency-check-suppressions.xml`.
- **SonarQube**: `./gradlew sonar -Dsonar.host.url=... -Dsonar.token=...` (정적분석/보안 핫스팟).
- CI 파이프라인(`deploy/cicd/ci-cd.yml`)에 두 단계가 빌드 게이트로 포함됨.

### 권한별 동작 확인(데모, local 프로필)
```bash
# admin 로그인 → 사용자 생성(POST) 가능 + 시스템관리 메뉴 노출
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"loginId":"admin"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
curl localhost:8080/api/v1/menus/me   -H "Authorization: Bearer $TOKEN"
curl -X POST localhost:8080/api/v1/users -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"loginId":"kim","name":"김철수","email":"kim@test.com"}'

# hong(USER) 로그인 → 조회는 되지만 생성(POST)은 403
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"loginId":"hong"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
curl -i -X POST localhost:8080/api/v1/users -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"loginId":"x","name":"테스트"}'   # -> 403
```

## 프로젝트별 기능 선택 (멀티 프로젝트 재사용 핵심)

프레임워크를 여러 SI 프로젝트에 들고 다닐 때 **2단계로 선택**한다.

**1단계 — 모듈 선택(큰 단위):** 필요한 framework 모듈만 의존성에 추가.
```groovy
dependencies {
    implementation project(':framework:framework-core')      // 거의 필수
    implementation project(':framework:framework-mybatis')   // DB 쓰면
    implementation project(':framework:framework-security')  // 인증/권한 필요하면
    implementation project(':framework:framework-openapi')   // API 문서 필요하면(빼면 통째로 제외)
    implementation project(':framework:framework-idempotency') // 정확히-한번/멱등키(금융)
    implementation project(':framework:framework-i18n')        // 메시지 외부화/다국어
    implementation project(':framework:framework-idgen')       // 채번(Snowflake/업무코드)
    implementation project(':framework:framework-client')      // 외부 API 표준 호출
    implementation project(':framework:framework-audit')       // 접속/감사 로그 적재·조회(ISMS-P)
    implementation project(':framework:framework-secure-web')  // 웹 보안 필터(헤더/경로조작/인젝션/CSRF)
    implementation project(':framework:framework-datasource')  // 읽기/쓰기 분리 라우팅 · 독립 다중 DB(금융)
    implementation project(':framework:framework-messaging')   // Kafka Outbox 발행 + 소비자측 멱등 소비(금융)
    implementation project(':framework:framework-excel')       // Excel 업/다운로드(POI 스트리밍 + 양식검증)
    implementation project(':framework:framework-batch')       // Spring Batch 실행/리스너 + Quartz cron
    implementation project(':framework:framework-notification') // 메일/SMS/알림톡 채널 추상화
}
```
> 신규 모듈 폴더를 추가하면 **루트 `settings.gradle` 에 `include 'framework:framework-<X>'` 등록**도 잊지 말 것(누락 시 `project not found`).

**2단계 — 프로퍼티 토글(세부):** 포함한 모듈 안에서 기능을 개별 on/off.
```yaml
framework:
  core:
    trace: true                 # traceId(MDC)
    http-logging: true          # 요청/응답 로깅(민감정보 마스킹)
    xss: true                   # XSS 입력 필터
    execution-time-aspect: true # 실행시간 측정
    audit-aspect: true          # @AuditLog 감사로그
  mybatis:
    audit-injection: true       # 감사필드 자동주입
  crypto:
    enabled: true
    aes-secret: "프로젝트별 키"
  security:
    enabled: true               # 보안 전체 on/off
    dynamic-authorization: true # false면 '인증만 되면 통과'(단순 모드)
    menu: true                  # 메뉴 API
    jwt:
      secret: "..."
  openapi:
    enabled: true
    title: "..."
```
모든 토글은 **미설정 시 기본 활성(matchIfMissing)**. 프로젝트 요건에 맞춰 끄기만 하면 된다.

## 공통기능 모듈 — 토대 4종 (2026-05 추가, 선택형)

> 공통 규약(3단 토글): ① 의존성 추가(`@ConditionalOnClass`) → ② `framework.<X>.enabled=true`(`@ConditionalOnProperty`) → ③ 같은 타입 빈 등록 시 `@ConditionalOnMissingBean` 으로 프로젝트가 override. 선택형이라 **기본 false** — 켜야 동작.
> 전체 모듈 카탈로그·구축순서·사업유형(공공/금융/일반) 프리셋은 `docs/FRAMEWORK_MODULES.md`.

### framework-idempotency (정확히-한번 / 금융 ★)
```yaml
framework:
  idempotency:
    enabled: true
    store: { type: jdbc }       # memory(기본·단일) | redis(다중인스턴스) | jdbc(영속·DB공유)
    replay: { enabled: true }   # 중복 시 409 대신 "저장된 응답 재생"(기본 false=409)
```
```java
@PostMapping("/api/v1/transfers")
@Idempotent                                  // Idempotency-Key 헤더로 멱등 처리(헤더 없으면 400)
public ApiResponse<Void> transfer(@RequestBody TransferRequest req) { ... }
```
- 기본(`replay.enabled=false`): 중복 키 → 409. 운영 replicas≥2 면 `store.type=redis|jdbc`.
- 재생 모드(`replay.enabled=true`): 완료된 동일 요청은 **저장된 응답 그대로 재생**, 처리중 409. 클라이언트 타임아웃 재시도에 동일 결과 보장. (실패/5xx 는 캐시 안 하고 재시도 허용. 작은 JSON 응답 권장 — 본문 버퍼링.)
- 영속/다중 인스턴스 공유는 `store.type=jdbc`(테이블 `db/idempotency-postgres.sql`) 또는 `redis`.

### framework-i18n (메시지 외부화 / 다국어)
```yaml
framework: { i18n: { enabled: true, default-locale: ko, error-localization: true } }
```
- 서비스 `messages[_en].properties` 에 `error.<코드>` 키만 추가하면 `BusinessException` 메시지가 `Accept-Language` 로 다국어화(키 없으면 기존 `ErrorCode.message()` 폴백).
- 일반 메시지: `MessageResolver.get("user.welcome", name)`.

### framework-idgen (공통 채번)
```yaml
framework: { idgen: { enabled: true, sequence: { table-name: id_sequence, initialize: true } } }
```
```java
long pk = idGenerator.nextLong();                          // Snowflake 분산 PK
String orderNo = codeGenerator.next("ORD", "yyyyMMdd", 6); // ORD20260531000123 (일자 바뀌면 자동 리셋)
```
- DataSource 없는 서비스는 `IdGenerator`(Snowflake)만 등록. 운영 다중 인스턴스는 Snowflake `node-id` 를 인스턴스별로 고정 권장.

### framework-client (외부 API 표준 호출)
```yaml
framework: { client: { enabled: true, connect-timeout: 2s, read-timeout: 5s } }
```
```java
RestClient client = frameworkRestClientBuilder.baseUrl("https://api.partner.com").build();
```
- 타임아웃 + 재시도(멱등 메서드, POST 제외) + 호스트별 서킷브레이커 + 연계로그(`framework.client.integration`) + `X-Trace-Id` 전파가 기본 적용.
- 기능별 개별 토글: `retry`/`circuit-breaker`/`logging`/`trace`. 새 외부 의존성 없음(서킷 자체 구현). 더 정교한 정책은 `frameworkRestClientBuilder` 빈 직접 등록으로 override.

## 보안 완성 모듈 (2026-05 추가, 선택형 · ISMS-P/보안성 심의)

> 동일 3단 토글 규약. 모두 **기본 false** — 켜야 동작.

### framework-security 확장 (비번 만료/이력 · 동시로그인)
```yaml
framework:
  security:
    password:
      expiry:  { enabled: true, max-age: 90d, warn-before: 14d }   # 변경주기 만료
      history: { enabled: true, count: 3, store: { type: jdbc } }  # 직전 N개 재사용 금지
    concurrent-session: { enabled: true, max-sessions: 1, strategy: EVICT_OLDEST, store: { type: jdbc } }
```
- 업무코드가 비번 변경 시점에 `PasswordLifecycleService` 를 호출(재사용 검사/이력 기록). 기능 off 면 no-op, 이력 없는 레거시 사용자는 강제 만료 안 함.
- 동시로그인: 초과 시 `EVICT_OLDEST`(기존 토큰 블랙리스트) 또는 `REJECT`(409). jdbc store 면 `security-extras-postgres.sql`(password_history·active_sessions) 적용.

### framework-audit (접속/감사 로그 적재·조회)
```yaml
framework: { audit: { enabled: true, store: { type: jdbc } } }   # logging(기본·인프라0) | jdbc(조회 API)
```
```java
@AuditLog(action = "TRANSFER")   // core 어노테이션 → audit 이 표준 적재(메서드 감사)
public void transfer(...) { ... }
```
- 로그인 성공/실패/로그아웃은 `LoginAuditEvent` 로 자동 적재(security→audit 단방향 이벤트). jdbc 면 `GET /api/v1/audit/logs?actor=&eventType=&from=&to=&page=&size=` + `audit-log-postgres.sql`. **kafka 싱크**(`store.type=kafka`)는 framework-messaging 의 Outbox 로 발행(messaging 도입 완료).

### framework-secure-web (웹 보안 필터)
```yaml
framework:
  secure-web:
    enabled: true
    headers: { enabled: true, frame-options: DENY, content-security-policy: "default-src 'self'" }
    path-traversal: { enabled: true }
    injection: { enabled: true, mode: log-only }                  # 오탐 관찰 후 block 전환
    csrf: { enabled: true, protect-paths: [/api/admin/**] }       # 쿠키인증/폼 보호 경로만
```
- 필터 순서: path → injection → csrf → headers → (core) xss. 거부는 표준 `ApiResponse` JSON. XSS **본문** 이스케이프는 core 담당(중복 아님).
- CSRF 는 Spring Security(csrf disable, stateless JWT)와 독립적인 더블서브밋. 인젝션 스크리닝은 보조 방어 — 본 방어는 파라미터화 쿼리(MyBatis `#{}`).

## 데이터·연계 / 업무 생산성 모듈 (2026-06 추가, 선택형)

전부 기본 off(`framework.<module>.enabled=false`). 상세 설계·카탈로그는 `docs/FRAMEWORK_MODULES.md`, 세션별 켜는 법/함정은 `HANDOFF_SUMMARY.md`.

### framework-datasource (읽기/쓰기 분리 라우팅 · 독립 다중 DB · 금융)
```yaml
framework:
  datasource:
    routing:                              # (1) 읽기/쓰기 분리 — 하나의 논리 DB 를 primary/replica 로
      enabled: true
      write: { url: jdbc:postgresql://primary:5432/sidb, username: ${DB_USER}, password: ${DB_PW} }
      read:  { url: jdbc:postgresql://replica:5432/sidb, username: ${DB_USER}, password: ${DB_PW} }  # 생략 시 WRITE 폴백
    # multi:                              # (2) 독립 다중 DB — 서로 다른 물리 DB (routing 과 배타)
    #   enabled: true
    #   primary: order                    # 소스 2개 이상이면 필수
    #   sources:
    #     order: { url: jdbc:postgresql://order-db:5432/orderdb, username: ${O_USER}, password: ${O_PW} }
    #     user:  { url: jdbc:postgresql://user-db:5432/userdb,  username: ${U_USER}, password: ${U_PW} }
```
- **routing**: `@Transactional(readOnly=true)` 트랜잭션은 READ(복제) 노드로, 그 외는 WRITE 로 라우팅. `LazyConnectionDataSourceProxy` 로 감싸 readOnly 확정 후 커넥션 획득. 복제 지연 유의(직후 정합성 중요한 읽기는 write 트랜잭션 안에).
- **multi**: DB 키 `<k>` 마다 `<k>DataSource`/`<k>SqlSessionFactory`/`<k>SqlSessionTemplate`/`<k>TransactionManager` 등록. 앱이 `@MapperScan(sqlSessionFactoryRef="<k>SqlSessionFactory")` + 보조 DB 는 `@Transactional("<k>TransactionManager")` 배선. **routing 과 동시 활성 불가**(둘 다 `@Primary` DataSource → 기동 시 fail-fast). 자세한 배선 예제는 `framework/framework-datasource/README.md`.

### framework-messaging (Kafka Outbox 발행 + 소비자측 멱등 소비 · 금융)
```yaml
framework:
  messaging:
    enabled: true                       # 발행자(Outbox DB 적재)
    kafka: { bootstrap-servers: kafka:9092 }
    outbox: { relay: { enabled: true } } # 릴레이(발행 워커) — PostgreSQL(SKIP LOCKED) 전제, 특정 인스턴스군만
    consumer: { enabled: true, ttl: PT24H, key-prefix: "evt:" }  # 소비자측 멱등(발행자와 독립)
  idempotency: { enabled: true, store: { type: redis } }         # 멱등 저장소(멀티 인스턴스=redis 필수)
```
- 발행: 비즈니스 트랜잭션과 같은 트랜잭션에서 `outbox_event` INSERT(원자성) → 릴레이가 Kafka 로 at-least-once 발행. `OutboxEventPublisher.publish(...)`.
- 소비: `IdempotentEventProcessor.process(record, env -> {...})` — 발행측이 실은 `x-event-id` 헤더로 중복 배달 1회 처리(실패 시 키 해제→재배달 재처리). **소비 서비스는 framework-idempotency 도 의존**해야 하며, 멀티 인스턴스면 `store.type=redis`.

### framework-saga (경량 오케스트레이션 Saga · 금융)
messaging Outbox 위에 **오케스트레이션만** 얹는다(전송·신뢰성·멱등 소비는 messaging 재사용). 단계 커맨드를 Outbox 로 발행(상태변경과 한 트랜잭션), 참여 서비스 리플라이로 전진/완료, 실패 시 **역순 보상**. 상태는 JDBC 영속(재기동 복구).
```yaml
framework:
  messaging:
    enabled: true
    outbox: { relay: { enabled: true } }   # Kafka 발행 워커(어느 인스턴스군이든 1곳 이상)
  saga:
    enabled: true
    reply-topic: saga-replies               # 참여 서비스가 회신할 토픽
    step-timeout: 60s
    recovery: { enabled: true }             # 스턱/재기동 복구 폴러(PostgreSQL SKIP LOCKED)
```
```groovy
// 의존: compileOnly 는 비전이 → messaging 도 명시
implementation project(':framework:framework-saga')
implementation project(':framework:framework-messaging')
```
- DDL `db/saga/saga-postgres.sql`(`saga_instance`/`saga_step`)을 서비스 마이그레이션으로 복사.
- 정의: `@Bean SagaDefinition.named("OrderSaga").step(name, cmdTopic, cmdType[, compTopic, compType])...build()`.
- 시작/리플라이: `sagaOrchestrator.start("OrderSaga", ctxJson)` · `@KafkaListener(topics="saga-replies")` 에서 `sagaReplyConsumer.handle(record)`.
- 참여 서비스: 커맨드 처리 후 `x-saga-reply-topic` 으로 `x-saga-id/step/phase` + `x-saga-outcome` 회신(자신의 Outbox 권장). **`(saga-id, step)` 기준 멱등 필수**(복구 재구동 시 재배달). 상세는 `framework/framework-saga/README.md`.

### framework-excel (POI 업/다운로드)
```yaml
framework: { excel: { enabled: true, export: { window-size: 100 }, import: { max-rows: 100000 } } }
```
- 다운로드 `ExcelExporter` — SXSSF 스트리밍(대용량 일정 메모리), 날짜 서식 자동, try-with-resources `close()`.
- 업로드 `ExcelImporter.readAndValidate(in, template)` — 양식검증(헤더/타입/필수/길이·패턴), 행별 오류 수집(`ExcelImportResult`).

### framework-batch (Spring Batch 실행/리스너 + Quartz cron)
```yaml
framework:
  batch: { enabled: true }
  scheduler:
    enabled: true                       # batch.enabled 도 필요
    jobs:
      - { name: settlementJob, cron: "0 0 2 * * ?" }   # Job '빈 이름' + Quartz cron
spring: { batch: { jdbc: { initialize-schema: always } } }   # 배치 메타테이블
```
- `JobLaunchSupport.launch(job)` — Batch 6 `JobOperator` 래핑 + run.id 재실행 보장. `LoggingJobExecutionListener` 부착 가능. yaml 로 배치 Job 을 cron 스케줄.

### framework-notification (메일/SMS/알림톡 채널 추상화)
```yaml
framework:
  notification:
    enabled: true
    channels:
      mail:     { enabled: true, from: noreply@company.com }   # spring.mail.* 필요
      sms:      { enabled: true, from: "0212345678" }          # 기본 로깅 → 벤더 SmsClient 빈 등록 시 교체
      alimtalk: { enabled: true, sender-key: ${ALIMTALK_SENDER_KEY:} }
```
- `NotificationService.send(NotificationRequest.mail(to,subject,content).html(true).build())` — `ChannelType` 라우팅. SMS/알림톡은 벤더 SPI(`SmsClient`/`AlimtalkClient`) + 기본 로깅 구현(서비스가 벤더 빈으로 교체).

## 운영/관측 모듈 (2026-06 추가, 선택형)

### framework-observability (메트릭 공통태그 · 구조화 로그 · OTel 익스포터)
core 의 분산추적 토대(`micrometer-tracing-bridge-otel`·`MdcTraceFilter`) 위에 **표준화 + 익스포트**를 얹는다. 전부 토글, 기본 off. **새 외부 의존성 0**(레지스트리/익스포터는 호스트가 runtimeOnly 로 opt-in, 모두 Boot BOM 관리).
```gradle
implementation project(':framework:framework-observability')
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'   // (선택) /actuator/prometheus
// runtimeOnly 'io.micrometer:micrometer-registry-otlp'        // (선택) 메트릭 OTLP push
// runtimeOnly 'io.opentelemetry:opentelemetry-exporter-otlp'  // (선택) 트레이스 OTLP export
```
```yaml
framework:
  observability:
    enabled: true
    env: prod
    version: "1.0.0"
    metrics: { common-tags-enabled: true, extra-tags: { region: kr } }
    logging: { structured: { enabled: true, format: ecs, target: console } }
    endpoints: { expose: [health, info, metrics, prometheus], probes-enabled: true }
```
- **공통 태그**: `MeterRegistryCustomizer` 가 모든 레지스트리에 `service`/`env`/`version`(+extra) 부여(k8s 라벨/OTel 컨벤션 정렬). `service`=app name, `env`=프로파일 자동.
- **구조화 로그**: Boot4 네이티브 `logging.structured.format`(ecs/logstash/gelf) — 인코더 라이브러리 불필요. traceId/spanId(MDC) 동봉.
- **OTLP 익스포터**(기본 off): 메트릭 `metrics.otlp.{enabled,url}`, 트레이스 `tracing.otlp.{enabled,endpoint}`(브리지 키 `management.otlp.tracing.endpoint`).
- 프로퍼티성 표준값은 `EnvironmentPostProcessor` 가 로깅/액추에이터 초기화 전에 주입(앱 값 우선). k8s: `deploy/k8s/observability.yaml`(ServiceMonitor + 스크레이프/프로브). 상세 `framework/framework-observability/README.md`.

## 추가된 공통 (이번 보강)
- **보안 예외 표준화**: 401/403 도 `ApiResponse` JSON 으로 통일(`RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`).
- **예외 처리 확장**: 잘못된 JSON, 타입 불일치, 필수 파라미터 누락, 404, 405, 업로드 초과, DB 무결성 위반, 인가 거부 등 일괄 표준화.
- **공통 검색조건**: `SearchCondition`(page/size/sort/keyword) + 정렬 컬럼 화이트리스트(SQL인젝션 방어).
- **감사필드 자동주입**: MyBatis 인터셉터가 INSERT/UPDATE 시 created_by/updated_at 등을 SecurityContext 사용자로 자동 채움.
- **암호화**: AES-256-GCM(`AesCryptoService`) + 개인정보 컬럼 암호화 `EncryptedStringTypeHandler` + BCrypt `PasswordEncoder`.
- **OpenAPI**: `framework-openapi` 모듈 추가만으로 Swagger UI(`/swagger-ui.html`) + JWT Authorize 버튼.

## admin-service (권한/메뉴 운영 API)
포트 8081. 모두 `@PreAuthorize("hasRole('ADMIN')")` + 감사로그. 변경 시 동적 인가 캐시 자동 갱신.
- `GET/POST/PUT/DELETE /api/v1/admin/resources` — URL-권한(리소스) CRUD, `POST|DELETE /role-map` 역할 매핑
- `GET/POST/PUT/DELETE /api/v1/admin/menus` — 메뉴 CRUD, 역할 매핑
- `GET /api/v1/admin/security/roles`, `POST /api/v1/admin/security/reload` — 역할 목록 / 권한 캐시 강제 갱신

토큰은 user-service(인증 서비스)가 발급하고 admin-service는 **동일 JWT 시크릿으로 검증**한다(MSA 표준).
```bash
# user-service(8080)에서 admin 로그인 → 토큰 획득 후 admin-service(8081) 호출
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"loginId":"admin"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
curl localhost:8081/api/v1/admin/resources -H "Authorization: Bearer $TOKEN"
curl -X POST localhost:8081/api/v1/admin/security/reload -H "Authorization: Bearer $TOKEN"
```

## 인증 추상화 + dev-auth + TokenStore (이번 추가)

### 인증은 "계약만 공통, 구현은 프로젝트"
프로젝트마다 인증 방식(DB/LDAP/SSO/GPKI)이 달라도 **`Authenticator` 인터페이스 하나만 구현**하면 된다.
토큰 발급/회전/폐기와 `/api/v1/auth/{login,refresh,logout}` 엔드포인트는 **공통**이 제공.
```java
@Component                                  // 이 빈만 등록하면 공통 로그인 자동 활성화
public class DbAuthenticationProvider implements Authenticator {
    public AuthenticatedUser authenticate(LoginCommand cmd) { ... }   // DB/LDAP/SSO 자유
}
```
- `Authenticator` 빈이 없으면 공통 로그인 자체가 비활성(외부 인증서버 위임 프로젝트 대응)
- access(jti 포함) + refresh 발급, refresh 회전(1회용), 로그아웃 시 jti 블랙리스트

### dev-auth — 개발 초기 인증/권한 우회
```yaml
framework:
  security:
    dev-auth:
      enabled: true              # 토큰 없이 통과 + 가짜 로그인 사용자 주입
      roles: [ROLE_ADMIN, ROLE_USER]
      allow-header-override: true  # 호출마다 X-Dev-Roles 로 권한 바꿔 테스트
```
- 인증을 우회하되 **가짜 사용자를 주입**하므로 `getCurrentUser()`/감사필드/`hasRole()`이 정상 동작 → 개발 코드가 안 깨짐
- 로그인 붙일 땐 `enabled: false` 한 줄로 실제 JWT 인증으로 전환(코드 변경 0)
- **안전장치**: prod 프로파일에서 켜져 있으면 **부팅 실패**, 활성 시 경고 배너 출력, 기본값 false
- 데모: `--spring.profiles.active=local,dev` 로 우회 / `local` 단독은 실제 로그인

### TokenStore — 상황별 선택
```yaml
framework:
  security:
    token-store:
      type: memory   # memory(기본,로컬) | jdbc(폐쇄망/공공SI) | redis(운영표준)
```
- **memory**: 인프라 0, 로컬 개발
- **jdbc**: 기존 DB 재사용(refresh_tokens/token_blacklist 테이블, `db/token-store-postgres.sql`)
- **redis**: `framework-redis` 모듈 추가 + type=redis. TTL 자동만료로 블랙리스트에 최적
- 인터페이스(`TokenStore`)만 공통, 구현 교체는 의존성+프로퍼티로. 기본 memory → jdbc fallback.

### DB 표준 = PostgreSQL
- 프레임워크 모듈은 **DB 드라이버를 품지 않는다**(재사용성). 각 서비스가 `org.postgresql:postgresql` 선언.
- 예제 기본 datasource = PostgreSQL(`jdbc:postgresql://localhost:5432/sidb`), 로컬은 H2 `MODE=PostgreSQL`.
- MyBatis는 벤더 중립이라 표준 SQL이면 다른 DB로도 이식 가능.

### 데모 (실제 로그인 → 토큰 → 호출)
```bash
# 실제 로그인(local 프로파일, 시드 계정 admin/{noop}admin123)
RESP=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"loginId":"admin","password":"admin123"}')
ACCESS=$(echo $RESP | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
REFRESH=$(echo $RESP | sed -E 's/.*"refreshToken":"([^"]+)".*/\1/')

curl localhost:8080/api/v1/menus/me -H "Authorization: Bearer $ACCESS"
curl -X POST localhost:8080/api/v1/auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}"
curl -X POST localhost:8080/api/v1/auth/logout  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}"

# 개발 우회 모드: 토큰 없이 호출 + 권한만 바꿔 테스트
# (bootRun --args='--spring.profiles.active=local,dev')
curl localhost:8080/api/v1/users -H "X-Dev-Roles: ROLE_USER"
```

## 공통코드 관리 + 3종 라이브러리 (이번 추가)

### framework-commoncode (선택형 모듈)
그룹/상세코드 관리. 의존성 추가만으로 API·캐시·변환이 자동 구성된다.
- 조회: `GET /api/v1/common-codes/{groupCode}`, `GET /api/v1/common-codes/groups`
- 관리(ADMIN): `POST/PUT /api/v1/common-codes`, `DELETE /api/v1/common-codes/{group}/{code}`
- 토글: `framework.commoncode.enabled`

### 녹여 넣은 3종
- **Caffeine 캐시** (framework-core): `@EnableCaching` + CaffeineCacheManager. 공통코드는 그룹 단위로 `@Cacheable`, 변경 시 `@CacheEvict`. 정책은 `framework.cache.spec`(예: `maximumSize=10000,expireAfterWrite=10m`)로 조정, `framework.cache.enabled`로 on/off.
- **MapStruct** (도메인→DTO 컴파일타임 변환): `CommonCodeStructMapper`. 런타임 비용 0. Lombok 병행 시 `lombok-mapstruct-binding` 적용.
- **Flyway** (DB 마이그레이션): `schema.sql`/`data.sql` 방식을 폐기하고 `src/main/resources/db/migration/V1__*.sql` 버전 관리로 전환.
  - 하나의 SQL 세트로 **H2(로컬)·PostgreSQL(운영) 공용** (H2는 flyway-core 내장, PostgreSQL은 `flyway-database-postgresql` 추가).
  - 운영 마이그레이션 = `db/migration`, **로컬 전용 시드**(테스트 계정/권한) = `db/seed-local` → local 프로파일에서만 `spring.flyway.locations` 에 포함.
  - 운영 DDL과 dev 시드가 깔끔히 분리됨.

### 캐시 동작 확인
```bash
# 같은 그룹 2회 조회 → 2번째는 캐시 히트(쿼리 로그 안 찍힘)
curl localhost:8080/api/v1/common-codes/GENDER -H "Authorization: Bearer $ACCESS"
curl localhost:8080/api/v1/common-codes/GENDER -H "Authorization: Bearer $ACCESS"
# ADMIN 이 코드 추가 → 해당 그룹 캐시 자동 무효화
curl -X POST localhost:8080/api/v1/common-codes -H "Authorization: Bearer $ADMIN_ACCESS" \
  -H 'Content-Type: application/json' \
  -d '{"groupCode":"GENDER","code":"X","codeName":"기타","sortOrder":3}'
```

> 참고: 로컬(H2)·운영(PostgreSQL) 모두 Flyway가 기동 시 마이그레이션을 자동 적용한다. 기존 운영 DB에 처음 도입할 땐 `spring.flyway.baseline-on-migrate=true`.

## 파일 업로드 공통 (이번 추가)

### framework-file (기본 local / nas) + framework-file-s3 (선택)
저장 백엔드를 추상화(`FileStorage`)하고 **환경별로 프로퍼티만 바꿔** 전환한다. 코드 변경 0.
```yaml
framework:
  file:
    enabled: true
    storage:
      type: local            # local(기본) | nas | s3
      base-path: ./uploads   # nas 는 마운트 경로(예: /mnt/nas/uploads)
      max-size: 10485760     # 10MB
      allowed-extensions: [jpg, png, pdf, docx, xlsx, hwp, zip, ...]
      s3:                    # type=s3 일 때만
        bucket: my-bucket
        region: ap-northeast-2
        endpoint:            # MinIO 등 S3 호환 스토리지(선택)
```
- **local**(기본)·**nas**: 파일시스템 저장소 공유(`FileSystemFileStorage`). NAS는 마운트 경로를 `base-path`로 지정만.
- **s3**: `framework-file-s3` 의존성 추가 + `type: s3`. AWS SDK v2 직접 사용(자격증명은 기본 체인: env/profile/IAM Role). `endpoint` 지정 시 MinIO 호환.
- 환경 분리 예: 로컬 `type: local`, 운영(폐쇄망) `type: nas`, 클라우드 `type: s3` — 같은 코드, 프로퍼티만 다르게.

### 보안 검증 (SI 필수)
- 저장 파일명은 **UUID로 생성**(원본명은 메타에만 보존) → 경로조작/덮어쓰기 차단
- 위험 확장자 **항상 차단**(exe/jsp/sh/php/bat 등) + 화이트리스트 확장자만 허용
- 크기 제한(프로퍼티) + Spring 멀티파트 한도 + 경로 정규화 후 base-path 이탈 검사
- 업로드/삭제는 `@AuditLog`로 감사로그 기록

### API & 데모
```bash
# 업로드 (multipart)
curl -X POST localhost:8080/api/v1/files \
  -H "Authorization: Bearer $ACCESS" -F "file=@./report.pdf"
# → { "data": { "id": 1, "originalName": "report.pdf", "storageType": "local", ... } }

# 다운로드 (한글 파일명은 RFC 5987 인코딩으로 처리)
curl -OJ localhost:8080/api/v1/files/1 -H "Authorization: Bearer $ACCESS"

# 메타 조회
curl localhost:8080/api/v1/files/1/meta -H "Authorization: Bearer $ACCESS"

# 삭제 (ADMIN)
curl -X DELETE localhost:8080/api/v1/files/1 -H "Authorization: Bearer $ADMIN_ACCESS"
```
파일 메타는 Flyway `V3__file_metadata.sql`로 관리. user-service에 연결되어 데모 가능.
