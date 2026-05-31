# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**보안 완성(ISMS-P)** 을 구현했다 — framework-security 에 **비번 만료/이력·동시(중복)로그인 제어** 를 추가하고, **선택형 신규 모듈 framework-audit**(접속/감사 로그 적재·조회) 를 만들었다. **새 외부 의존성 0**, 기존 3단 토글 규약 그대로.

## 최종 갱신
- 일자: 2026-05-31 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1

## 무엇을 했나 (Done)
- **framework-security 확장 — 비번 만료/이력** (ISMS-P): `framework.security.password.expiry.{enabled,maxAge=90d,warnBefore=14d}`, `…password.history.{enabled,count=3,store.type=memory|jdbc}`. `PasswordLifecycleService` 가 재사용금지 검사·변경기록·만료판정 담당(`assertNotReused`/`recordChange`/`isExpired`/`expiresAt`/`isExpiringSoon`). 기능 off 면 전부 no-op, 이력 없는 **레거시 사용자는 강제 만료 안 함**. 업무코드(user-service)는 비번 변경 시점에 호출.
- **framework-security 확장 — 동시(중복) 로그인 제어**: `framework.security.concurrent-session.{enabled=false,maxSessions=1,strategy=EVICT_OLDEST|REJECT,store.type=memory|jdbc}`. `ConcurrentSessionService` 가 로그인 시 세션 등록 → 한도 초과 시 **EVICT_OLDEST**(가장 오래된 세션 토큰 블랙리스트+refresh 제거) 또는 **REJECT**(CONFLICT). `LoginService.issue/refresh/logout` 에 연동.
- **framework-security 확장 — 로그인 감사 이벤트**: `LoginAuditEvent`(SUCCESS/FAILURE/LOGOUT) 를 `ApplicationEventPublisher` 로 발행만. **security 는 audit 에 의존하지 않음**(이벤트로 디커플).
- **framework-audit (신규, [선택])**: `framework.audit.enabled=false` 기본. `@AuditLog`(core 어노테이션) AOP 영속화 + 로그인 이벤트 적재. 싱크 `store.type=logging`(기본)|`jdbc`. jdbc 일 때만 조회 API(`GET /api/v1/audit/logs`, actor/eventType/result/from/to/page/size). kafka 는 messaging 모듈 도착 전까지 logging 으로 **우아하게 축소**.

## 현재 상태 (적용/검증)
- 변경/신규 파일 모두 repo 반영. `framework-audit` 는 `settings.gradle` 에 등록 완료(누락 시 `project not found`).
- 코드 정합성 점검 통과: core API 시그니처(`BusinessException(ErrorCode,String)`·`ApiResponse.ok`·`PageRequest.of`·`PageResponse.of`) 일치, 괄호/중괄호 균형 OK.
- ⚠️ **실제 gradle 컴파일 미검증**(작성 환경 Maven Central 차단). 받는 쪽에서 모듈별 확인:
  - `./gradlew :framework:framework-security:compileJava`
  - `./gradlew :framework:framework-audit:compileJava`
  - 권장: `./gradlew spotlessApply` (palantirJavaFormat 재정렬).
- DB: jdbc 싱크/스토어 쓸 때만 SQL 적용 — `framework-security/.../db/security-extras-postgres.sql`(password_history·active_sessions), `framework-audit/.../db/audit-log-postgres.sql`(audit_log).

## 바로 다음 할 일 (Next)
1. 받는 쪽에서 **security·audit 컴파일 최종 확인** + `spotlessApply`.
2. **framework-secure-web** — CSRF·시큐어코딩 필터(SQLi/경로조작 등, XSS 는 core).
3. **금융 핵심** — **framework-messaging**(Kafka + Outbox; audit 의 kafka 싱크는 여기 도착 후 연결), **framework-datasource**(읽기/쓰기 분리).
4. 이후: excel · batch · notification → 규제특화(pki/mfa/hsm/recon/egov) → observability → 게이트웨이/k8s/CI-CD 멀티서비스화. (상세 순서는 `docs/FRAMEWORK_MODULES.md` 4절)

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **security → audit 단방향 금지.** audit 가 security 에 의존(`api project(':framework:framework-security')`)하고, security 는 audit 을 **이벤트(`LoginAuditEvent`)로만** 알린다. 반대로 security 가 audit 을 import 하면 순환.
- **core 의 `@AuditLog` 로깅 어스펙트는 그대로 두고**, audit 의 `AuditTrailAspect` 가 같은 어노테이션에 `@Around` 를 **추가로** 건다(advice 2개 중첩, 대상 메서드는 1회 실행). 영속화는 audit 쪽만 담당.
- **audit 조회 from/to 는 `Instant.parse`(ISO-8601 instant) 로 명시 파싱** — `@DateTimeFormat` 의 포매터 모호성 회피. 형식 틀리면 `INVALID_INPUT`.
- **선택형 모듈 기본 off 유지**(`framework.audit.enabled=false`). 신규 모듈은 `settings.gradle` 등록 필수(기존 함정 재확인).
- **새 외부 의존성 0 유지.** audit 은 web/jdbc 를 `compileOnly`(호스트 제공). `libs.versions.toml`/`STACK.md` 변경 없음.
- (기존) 작성 환경 bash 중괄호 확장 `{a,b}` 미동작 → `for` 루프.

## 모듈 추가 레시피 (검증된 반복 절차)
1. `framework/framework-<X>/` 생성: `config`(Properties+AutoConfiguration) · 도메인 패키지 · `resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`(FQCN 등록).
2. `build.gradle`: `api project(':framework:framework-core')`(+필요 시 다른 framework 모듈) + starter 는 `compileOnly`. 새 버전 의존성 지양.
3. **`settings.gradle` 에 include 추가**(잊지 말 것).
4. 코드 작성 전 **Boot 4/Spring 7 변경 API 확인**(특히 `HttpHeaders`, `boot.http.client`, `RestClient`/`RestTemplate` 위치, starter-aop→starter-aspectj).
5. 오토컨피그: `@AutoConfiguration` + `@ConditionalOnClass(모듈마커)` + `@ConditionalOnProperty(framework.<x>.enabled=true)` + 빈은 `@ConditionalOnMissingBean`. 3단 impl 은 `store.type` 으로 분기(memory matchIfMissing).
6. 검증: `./gradlew :framework:framework-<X>:compileJava` (Configuration Cache 꼬이면 `--no-configuration-cache` 또는 `clean`).
7. 드롭인 배포: 모듈 폴더 + 변경 파일 + **완성 `settings.gradle`** 을 한 zip 에 담아 루트에서 `unzip -o`.

<!-- 갱신 끝 -->
