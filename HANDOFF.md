# HANDOFF.md — 인계 전반 (SI MSA 공통 프레임워크)

> 목적: 처음 인계받는 사람이 **구조·실행·설계 원칙·함정**을 한 번에 파악하도록 한다.
> 문서 지도: **이 문서**(인계 전반) · `README.md`(사용 가이드/데모) · `STACK.md`(라이브러리/버전) · `HANDOFF_SUMMARY.md`(세션 단위 한 장 — 매 세션 갱신) · `docs/FRAMEWORK_MODULES.md`(전 기능 on/off 모듈 설계서·카탈로그).
> 최종 갱신: 2026-05-31 · 갱신자: <!-- 채우기 -->

---

## 1. 한눈에 보는 구조
Spring Boot 4.0.6 / Java 21 / Spring Cloud 2025.1.1(Oakwood) / MyBatis 기반 멀티모듈.

**공통 프레임워크(라이브러리)** — `framework/`
- `framework-core` : 응답표준(`ApiResponse`)·에러(`ErrorCode`/`BusinessException`)·페이징·AOP(`@AuditLog`)·로깅/트레이스·XSS·캐시
- `framework-mybatis` : MyBatis 연동, `CurrentUserProvider`(감사필드/현재 사용자)
- `framework-security` : 인증추상화·JWT·TokenStore·동적 RBAC·비밀번호 정책·로그인 시도 제한
- `framework-openapi` / `framework-commoncode` / `framework-file` / `framework-file-s3` : 선택형
- `framework-redis` : 선택형. Redis 기반 `TokenStore` + `LoginAttemptService`(다중 인스턴스 공유)
- **공통기능 모듈(2026-05 추가, 선택형)** — 토대 4종:
  - `framework-idempotency` : 정확히-한번/멱등키(`@Idempotent`+`Idempotency-Key`, store=memory|redis). 금융 ★
  - `framework-i18n` : 메시지 외부화/다국어(`MessageSource`+`MessageResolver`, `ErrorCode` 로케일 해석)
  - `framework-idgen` : 채번 — Snowflake `IdGenerator`(분산 PK) + 업무코드 `CodeGenerator`(테이블 기반, DataSource 있을 때만)
  - `framework-client` : 외부 API 표준 호출(`RestClient` + 타임아웃·재시도·서킷·연계로그·트레이스전파). 새 외부 의존성 0
  - 전체 카탈로그/구축순서/사업유형 프리셋은 `docs/FRAMEWORK_MODULES.md`.

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
- (작업 환경) bash 중괄호 확장 `{a,b}` 미동작 → `for` 루프.

## 7. 현재 상태 / 다음 작업
- 보안 골격(비번 정책·BCrypt·로그인 잠금 memory/redis·키 전략·인가 분리)까지 일단락.
- **공통기능 토대 4종 추가**(idempotency·i18n·idgen·client) — 선택형, 3단 토글. i18n·idgen 컴파일 확인, client 는 Spring 7 API 수정 후 재컴파일 단계. 최신 세션 상세는 `HANDOFF_SUMMARY.md`.
- **다음 우선순위**: (1) client 컴파일 최종 확인 → 토대 완료. (2) **보안 완성(ISMS-P/심의)**: 비번 만료·이력, 동시(중복)로그인 제어, `framework-audit`(접속/감사 로그 적재·조회). (3) **금융 핵심**: `framework-messaging`(Kafka+Outbox), `framework-datasource`(읽기/쓰기 분리). (4) 이후 excel·batch·notification → 규제특화(pki/mfa/hsm/recon/egov) → observability → 게이트웨이/k8s/CI-CD 멀티서비스화.
- **기존 미해결(유효)**: user-service 통합 테스트 0개 보강(Testcontainers-PostgreSQL), 관리자 초기화 인가 단일 소스 통일, 운영 프로파일 redis/`key-strategy` 명시.

## 8. 문서 갱신 규칙
- **세션을 넘길 때마다** `HANDOFF_SUMMARY.md` 의 `<!-- 갱신 -->` 구간을 새로 쓴다.
- 라이브러리/플러그인 **버전을 바꾸면** `gradle/libs.versions.toml`(단일 소스) → `STACK.md` 표 갱신.
- 구조/원칙/함정이 바뀌면 **이 문서**를, 사용법/데모가 바뀌면 `README.md` 를 갱신.
