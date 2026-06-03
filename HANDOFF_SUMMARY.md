# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**로컬 개발/운영 환경정비 + 보안·검증 보강 + spotless 확장** 묶음 완료. (1) 프로파일을 **local/dev/prod** 로 통일하고 메모리(H2)↔로컬 PostgreSQL↔Redis 를 `local-xx` 오버레이로 전환, **감사 로그 DB 적재**가 안 되던 원인(=`audit_log` 마이그레이션 부재)을 잡아 `V4/V2__audit_log.sql` 추가. (2) **JWT 시크릿 prod 가드**(`JwtSecretSafetyGuard`, DevAuth 가드 패턴) 신설 + **요청 검증 빈틈**(Spring7 `HandlerMethodValidationException` 미처리, 로그인 `LoginCommand` 무제약) 보강. (3) **spotless 를 Java 전용 → gradle/yaml/sql/md 까지 확장**하고 **설정 캐시 충돌**(`lineEndingsPolicy`)을 `lineEndings=UNIX` 로 해결. 포맷터는 google 아님 = **Palantir**. **다음 세션 최우선 = YAML(설정값) 패스워드 암호화** → 설계서 `docs/NEXT_YAML_PASSWORD_ENCRYPTION.md`.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: 환경정비+보안검증+spotless 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### A. 환경(프로파일) 정비 — local/dev/prod 통일 + 로컬 인프라
1. **프로파일 재편**(user/admin 서비스): `application.yml` 은 인프라 비종속 공통만(기본 활성 `local`). **local**=H2 메모리(설치0)·**dev**=개발 서버(env 주입)·**prod**=운영(시크릿 주입). 특수는 `local-xx` 오버레이 — **local-postgres**(DB→로컬 PG)·**local-redis**(Redis on)·**local-noauth**(로그인 우회, 과거 `dev` 역할 이전).
2. **감사 로그 DB 적재 활성화**: `framework.audit.store.type=jdbc` 가 동작하려면 `audit_log` 테이블 필요한데 **마이그레이션이 없어 INSERT 가 조용히 실패**(WARN만)하던 문제 해결 → `db/migration/V4__audit_log.sql`(user)·`V2__audit_log.sql`(admin), H2/PG 양립 DDL(IDENTITY). local 에서 H2 콘솔로 검증하도록 `store.type=jdbc`+`h2.console` on.
3. **로컬 설치/검증 문서**: `docs/LOCAL_SETUP.md` — 설치 목록(JDK21·PostgreSQL17/18·Redis8|Valkey·선택 ClamAV/MinIO/Kafka)·OS별 설치(Win/WSL·mac·Ubuntu)·DB/계정(sidb/siuser)·실행 매트릭스·**로그 DB 적재 검증 절차**·docker-compose 부록.
4. **변경/Deprecated 통합문서**: `docs/CHANGES_AND_DEPRECATIONS.md` — Jackson2→3·413 명칭·Batch6 `JobOperator`·POI close·OpenPDF 패키지·Boot4 EPP 이동·게이트웨이 server.webflux/trusted-proxies·datasource-proxy 2.0·Redis Jackson2 금지·외국인등록번호 체크섬 폐지·프로파일 재편.

### B. 보안·검증 보강 (framework-security / -core, 신규 의존성 0)
5. **JWT 시크릿 prod 가드**: `JwtSecretSafetyGuard`(`InitializingBean`, DevAuth/Password 가드와 동일 패턴) — prod 에서 비었거나·`change-this`/`change-me` 흔적·32바이트 미만이면 **부팅 실패**, local/dev 는 경고. `SecurityAutoConfiguration` 에 빈 등록. 테스트 6.
6. **요청 검증 빈틈 보강**: (a) Spring7 **`HandlerMethodValidationException`**(메서드 파라미터 검증) 미처리 → `GlobalExceptionHandler` 에 핸들러 추가(ApiResponse 400 통일; 미처리 시 기본 ProblemDetail 로 형식 깨짐). (b) 미인증 진입점 **로그인** `LoginCommand` 에 `@NotBlank` + `AuthController.login` 에 `@Valid`(빈 자격증명 400 차단). validation 은 이미 framework-core 가 전이 노출 → **신규 의존성 0**.

### C. spotless 확장 + 설정 캐시 해결 (루트 build.gradle)
7. **검사 범위 확장**: 기존 **Java(Palantir)만** + `subprojects` 안이라 루트 build.gradle 미검사 → **루트에도 적용**해 gradle/yaml/sql/md 까지. YAML/SQL/Gradle 은 의미 보존 위해 **공격적 재포맷터(jackson/dbeaver/greclipse) 대신 화이트스페이스 위생만**(옵션 주석 제공). md 는 줄끝 공백 보존 위해 `endWithNewline` 만.
8. **컨벤션 추가**: `encoding 'UTF-8'`(한글 주석 깨짐 방지·Windows 중요)·`toggleOffOn()`·`formatAnnotations()`(8.x 기본 목록에 `@Valid` 포함).
9. **설정 캐시 충돌 해결**: `org.gradle.configuration-cache=true` + spotless 기본 `GIT_ATTRIBUTES` 줄바꿈정책이 `lineEndingsPolicy`(DefaultProvider) 직렬화 실패 → `:spotlessGroovyGradle` BUILD FAILED. **해결 = `lineEndings = com.diffplug.spotless.LineEnding.UNIX`** 두 블록 모두(Windows 개발+Linux CI 일관성=LF). 문서 `docs/SPOTLESS_NOTES.md`.

## 현재 상태 (적용/검증)
- ✅ **사용자 환경에서 Java 컴파일 성공 + `spotlessApply` 통과 확인(2026-06-03)**.
- ⚠️ **build.gradle 의존성은 직접 1줄 추가 필요**(zip 미포함 — buildscript/flyway 블록 보존 목적): 감사 DB 적재=`implementation project(':framework:framework-audit')`, Redis 기능=`framework-redis`+`framework-cache-redis`+`spring-boot-starter-data-redis`. 안 넣으면 `@ConditionalOnClass` 로 해당 기능만 조용히 비활성(빌드는 정상).
- 작성 환경(Maven Central 차단)으로 **gradle 풀빌드/테스트는 미실행** — 받는 쪽 검증 경로 제시함.
- 신규/수정 파일: 프로파일 yml(user/admin 각 7)·audit 마이그레이션 2·`JwtSecretSafetyGuard`(+test)·`SecurityAutoConfiguration`/`AuthController`/`LoginCommand`/`GlobalExceptionHandler` 패치·루트 `build.gradle`·문서 5(LOCAL_SETUP/CHANGES_AND_DEPRECATIONS/SECURITY_VALIDATION_ADDITIONS/SPOTLESS_NOTES/NEXT_YAML_PASSWORD_ENCRYPTION).

## 켜는 법
```bash
# DB: 메모리 → 로컬 PostgreSQL → +Redis
./gradlew :services:user-service:bootRun
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres'
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres,local-redis'
# 로그인 우회 개발(과거 local,dev) → 이제:
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-noauth'
```
- 감사 로그 DB 적재 검증: 위 기동 후 로그인 호출 → H2 콘솔(`/h2-console`, jdbc:h2:mem:sidb) 또는 psql 에서 `SELECT * FROM audit_log;` (상세 `docs/LOCAL_SETUP.md §5`).
- spotless: 최초 1회 `rm -rf .gradle/configuration-cache && ./gradlew spotlessApply` 후 안정.

## 바로 다음 할 일 (Next)
1. **★ YAML(설정값) 패스워드 암호화** — **설계서 `docs/NEXT_YAML_PASSWORD_ENCRYPTION.md` 그대로 진행**. 핵심: Jasypt 말고 **커스텀 `EnvironmentPostProcessor`(Boot4 패키지)** 가 `ENC(...)` 값을 **기존 `AesCryptoService`**(AES-GCM, 마스터키=`framework.crypto.aes-secret`/`AES_SECRET`)로 복호화. 신규 의존성 0. 곁들임: AES 마스터키 prod 가드(JWT 가드 패턴).
2. (devops) **CI 게이트**(`:framework-archtest:test`+전모듈 `:test` PR 차단) + 멀티모듈 jacoco 집계.
3. (선택) 카탈로그 §6 대기열: 아카이빙/압축·일괄 처리 / 서버측 S3 멀티파트 병렬 업로드(TransferManager).
4. (선택) 그릇 정비 — 게이트웨이 런타임 점검·k8s 멀티서비스(redis/secret/configmap·observability ServiceMonitor)·CI-CD 멀티서비스화.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **spotless 포맷터 = Palantir Java Format**(google 아님). 줄바꿈은 `lineEndings = LineEnding.UNIX` 로 고정 — 기본 `GIT_ATTRIBUTES` 는 **설정 캐시와 충돌**(gradle#19113/spotless#987). spotless 를 **루트에도 적용**해야 루트 build.gradle/yaml/sql/md 가 검사됨. 에러 후엔 `.gradle/configuration-cache` 비우고 재실행.
- **감사 로그 DB 적재 3요건**: ① `framework-audit` 의존 ② `store.type=jdbc` ③ `audit_log` 테이블(마이그레이션). 하나라도 빠지면 `JdbcAuditEventSink` 가 **실패를 삼키고 WARN만** → "로그 안 쌓임"의 주범.
- **프로파일 의미 변경**: 로그인 우회 = `local,local-noauth`(옛 `local,dev` 아님). `dev` 는 이제 개발 서버. `application.yml` 에 DB 하드코딩 금지(프로파일이 결정), 기본 활성=`local`.
- **prod 시크릿 가드**: `JwtSecretSafetyGuard` 가 prod 기본/약한 JWT 시크릿을 부팅 실패시킴. 운영은 `JWT_SECRET`(32B+) 주입 필수. (AES 마스터키도 같은 가드를 다음에 추가 권장.)
- **요청 검증**: `@Valid @RequestBody` 는 `MethodArgumentNotValidException`(처리됨), **메서드 파라미터 검증은 Spring7 `HandlerMethodValidationException`**(이번에 핸들러 추가) — 둘은 다른 예외. 패키지 `org.springframework.web.method.annotation`.
- **EPP(다음 작업 예고)**: Boot4 `EnvironmentPostProcessor`는 `org.springframework.boot.EnvironmentPostProcessor`(구 `...boot.env.*` 아님) + `spring.factories` 키 정확히. EPP 는 빈 사용 불가(컨텍스트 전) → `AesCryptoService` 직접 생성. 마스터 키는 `ENC()` 불가(평문 주입).
- (지난·유효) 토글3단 기본 off / Jackson3(`tools.jackson.*`, annotation만 예외) / compileOnly 타입 test 재선언 / `.imports` 가드 / 슬라이스 단방향 / 필드주입 금지 / 413=`CONTENT_TOO_LARGE` / 필터는 GlobalExceptionHandler 밖 / Boot4 패키지 리네임 추측 금지 / JUnit launcher·starter-test 모듈마다 / Redis Jackson2 직렬화기 금지.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증 가능하게.
2. 기존 인터페이스는 건드리지 말고 capability 인터페이스로 확장(ISP). 생성자 변경 시 기존 오버로드 유지.
3. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+test 재선언), BOM 밖=`implementation`. 의존성 추가는 곧 1단(모듈) 토글 — 미추가 시 기능만 조용히 비활성.
4. 새 오토컨피그면 `.imports`+가드 테스트. 기존 오토컨피그에 빈만 추가면 `.imports` 무변경. **EPP 는 `spring.factories`**(키 정확히, Boot4 패키지).
5. 오토컨피그 토글 + `@ConditionalOnMissingBean`. 선택 백엔드는 설정 분기.
6. 테스트: 핵심 알고리즘 단위(JDK) + 오토컨피그 빈 선택 + 동작 게이트(Mockito). EPP 는 `StandardEnvironment`+`MapPropertySource` 로 단위 검증.
7. 드롭인: 변경 파일 전부 한 zip, 루트 `unzip -o`. 문서 동기화. 사용자 환경 `./gradlew :...:test :framework-archtest:test spotlessApply` 검증. (spotless 는 UTF-8/LF·설정캐시 주의.)

<!-- 갱신 끝 -->
