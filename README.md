# SI MSA Common Framework (Spring Boot 4.0.6 / Java 21 / MyBatis)

SI 프로젝트용 MSA 공통 프레임워크 스켈레톤. 멀티모듈 + "프로젝트별 선택"(옵션 모듈 + `framework.*.enabled` 토글) 설계.

> 함께 보기: 인계 요약은 `HANDOFF.md`, 라이브러리/플러그인/버전 관리는 `STACK.md`.

## 스택
| 항목 | 버전 |
|------|------|
| Spring Boot | 4.0.6 (Spring Framework 7.0.7) |
| Java | 21 (가상 스레드) |
| Gradle | 8.14 (멀티모듈, Kotlin/Groovy DSL) |
| MyBatis | mybatis-spring-boot-starter 4.0.1 |
| Spring Cloud | 2025.1.1 (Oakwood) — Gateway |
| 버전 관리 | `gradle/libs.versions.toml` 단일 카탈로그 |

## 모듈 구조
```
framework/
  framework-core       공통 응답/예외/페이징/트레이스/로깅/AOP/XSS/암호화(AES)/캐시(Caffeine)/가상스레드
  framework-mybatis    MyBatis 공통설정 + BaseEntity(감사컬럼) + 감사필드 자동주입 + 암호화 TypeHandler
  framework-security   JWT 인증 + DB기반 동적 RBAC + 메뉴 + dev-auth + 인증추상화(Authenticator) + TokenStore
  framework-openapi    (선택) springdoc + JWT 스킴
  framework-redis      (선택) RedisTokenStore
  framework-commoncode (선택) 공통코드 + Caffeine 캐시 + MapStruct
  framework-file       (선택) 파일 업로드(로컬/NAS) + 보안검증
  framework-file-s3    (선택) S3 저장소(AWS SDK v2)
services/
  gateway       Spring Cloud Gateway (라우팅 + CircuitBreaker)
  user-service  샘플 업무 서비스 (8080) — 인증/공통코드/파일 연결
  admin-service 권한/메뉴 운영 API (8081)
deploy/         docker / k8s(프로브·HPA) / cicd(GitHub Actions)
Jenkinsfile     빌드·품질게이트·Flyway검증·도커·K8s 배포
```

## 핵심 설계
- 각 서비스는 `framework-*` 의존성만 추가하면 표준 응답/예외/보안/MyBatis가 **자동 적용**(auto-configuration `.imports`).
- 모든 응답은 `ApiResponse<T>` 표준 포맷. 예외는 `GlobalExceptionHandler`가 통일 변환(401/403/4xx/DB무결성 포함).
- 요청마다 `traceId`(MDC) 부여 → 로그 패턴/응답 헤더(`X-Trace-Id`)에 노출.
- Java 21 가상 스레드: `spring.threads.virtual.enabled=true` + `@Async` 가상스레드 executor.
- 보안: `Authorization: Bearer <JWT>` 무상태 인증. `/actuator/**`, `/api/*/auth/**` 는 permitAll.
- 프레임워크는 DB 드라이버를 품지 않음 — 각 서비스가 `org.postgresql:postgresql` 선언.

## 처음 실행하기
```bash
# (최초 1회) wrapper — 이미 생성되어 있으면 생략
gradle wrapper --gradle-version 8.14

# 전체 빌드(컴파일 + 테스트 + 커버리지 + 포맷 검사)
./gradlew build

# 로컬(H2) 실행 + 실제 로그인
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local'

# 호출 예시
curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"loginId":"admin","password":"admin123"}'
```

## 빌드 / 품질 / CI
### 버전 카탈로그
- 모든 버전은 `gradle/libs.versions.toml` 단일 소스. 루트 `build.gradle` 의 `ext { }` 브리지로 기존 모듈의 `${...Version}` 참조 호환 → 모듈은 `libs.*` 로 점진 이관.

### 품질 게이트
```bash
./gradlew spotlessApply          # 코드 포맷 자동정렬(Palantir). 최초 1회는 단독 커밋 권장
./gradlew spotlessCheck          # 포맷 검사(CI 게이트)
./gradlew build                  # JaCoCo 커버리지 리포트 생성 → Sonar 가 수집
./gradlew dependencyCheckAggregate   # OWASP CVE 스캔(CVSS 7.0+ 빌드 실패)
./gradlew sonar -Dsonar.host.url=... -Dsonar.token=...
```

### Flyway (CI 마이그레이션)
앱 기동 시 자동 마이그레이션과 별개로, 배포 전 정합성 검증에 사용한다.
```bash
./gradlew :services:user-service:flywayValidate \
  -Pflyway.url=jdbc:postgresql://... -Pflyway.user=... -Pflyway.password=...
```

### Jenkins
루트 `Jenkinsfile`: 빌드·테스트(Testcontainers+JaCoCo) → 품질게이트(Spotless·OWASP·Sonar) → **Flyway 검증 게이트** → 도커 빌드/푸시 → K8s 롤아웃. credentials(`db-prod`/`registry`/`kubeconfig`) 등록 필요, 빌드 노드에 Docker 데몬 필요.

### 개발 편의
- **SQL 디버깅**: datasource-proxy(2.0.0)로 실제 바인딩 값 + 슬로우쿼리 로깅. `application-local.yml` 의 `decorator.datasource.*` 로 제어(운영 프로파일엔 끄기).
- **핫 리로드**: DevTools(`developmentOnly`). 운영 jar 미포함.
- **통합테스트**: Testcontainers + `@ServiceConnection` 으로 실 PostgreSQL 기동, Flyway 가 컨테이너에 마이그레이션 적용.

## 기능 가이드

### 보안 (framework-security)
- **JWT 무상태 인증** + 토큰 roles → 권한 매핑(jti 포함 → 로그아웃 블랙리스트).
- **DB 기반 동적 인가**: `resources(URL패턴-메서드)` ↔ `roles` 매핑을 DB에서 읽어 `DynamicAuthorizationManager`가 요청마다 판단. 권한 변경은 테이블 수정 후 `SecurityMetadataService.reload()`(또는 admin API)로 무중단 반영.
- **메뉴 관리**: 역할별 메뉴 → `GET /api/v1/menus/me` 로 권한 맞춤 메뉴 트리.
- **메서드 보안**: `@PreAuthorize("hasRole('ADMIN')")` (`@EnableMethodSecurity`).
- **시큐어 헤더**: HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, CSP.
- **보안 예외 표준화**: 401/403 도 `ApiResponse` JSON(`RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`).
- 테이블: `users / roles / user_roles / resources / role_resources / menus / role_menus`.

### 인증 추상화 + dev-auth + TokenStore
- 인증은 **계약만 공통, 구현은 프로젝트**: `Authenticator` 인터페이스 하나만 구현(DB/LDAP/SSO/GPKI 자유). 로그인/refresh/logout·토큰발급은 공통 제공(`/api/v1/auth/{login,refresh,logout}`).
- **dev-auth**: `framework.security.dev-auth.enabled=true` 로 토큰 없이 통과 + 가짜 사용자 주입(`getCurrentUser()`/감사필드/`hasRole()` 정상). prod 프로파일에서 켜지면 부팅 실패(안전장치). `X-Dev-Roles` 헤더로 권한 변경 테스트.
- **TokenStore**: `framework.security.token-store.type` = `memory`(로컬) | `jdbc`(폐쇄망/공공) | `redis`(운영표준). 인터페이스만 공통, 구현은 의존성+프로퍼티로 교체.

### 코어 (framework-core)
- **공통 로깅**: `logback-common.xml`(traceId 패턴, 일자별 롤링, AUDIT 전용 파일). 요청/응답 본문 로깅 + 민감정보(비밀번호/토큰/주민번호/카드) 자동 마스킹.
- **AOP**: `ExecutionTimeAspect`(슬로우 탐지), `@AuditLog`+`AuditLogAspect`(감사추적). ※ AuditLogAspect 는 spring-security 를 compileOnly 로 참조(보안 모듈 있으면 적용).
- **XSS 방어**: `XssRequestFilter`(파라미터/헤더 이스케이프).
- **암호화**: AES-256-GCM(`AesCryptoService`) + 개인정보 컬럼 `EncryptedStringTypeHandler` + BCrypt `PasswordEncoder`.
- **캐시**: Caffeine 기반 `@Cacheable`(정책 `framework.cache.spec`).
- **공통 검색조건**: `SearchCondition`(page/size/sort/keyword) + 정렬 컬럼 화이트리스트(SQL인젝션 방어).

### 공통코드 (framework-commoncode)
- 그룹/상세코드 관리. 의존성 추가만으로 API·캐시·MapStruct 변환 자동 구성.
- 조회 `GET /api/v1/common-codes/{groupCode}` / 관리(ADMIN) `POST/PUT/DELETE`. 변경 시 해당 그룹 캐시 자동 무효화.

### 파일 업로드 (framework-file / framework-file-s3)
- 저장 백엔드 추상화(`FileStorage`) — `framework.file.storage.type` = `local`(기본)/`nas`/`s3`. 코드 변경 0, 프로퍼티만 교체.
- 보안: 저장명 UUID, 위험 확장자 항상 차단 + 화이트리스트, 경로조작 방어, 크기 제한, 업로드/삭제 감사로그.
- API: `POST /api/v1/files`(업로드), `GET /api/v1/files/{id}`(다운로드, RFC5987), `GET .../meta`, `DELETE`(ADMIN).

### admin-service (권한/메뉴 운영 API, 8081)
- `GET/POST/PUT/DELETE /api/v1/admin/resources` — URL-권한 CRUD + `role-map`
- `GET/POST/PUT/DELETE /api/v1/admin/menus` — 메뉴 CRUD + role-map
- `GET /api/v1/admin/security/roles`, `POST /api/v1/admin/security/reload` — 역할 목록 / 권한 캐시 갱신
- 토큰은 user-service 가 발급, admin-service 는 동일 JWT 시크릿으로 검증(MSA 표준).

## 프로젝트별 기능 선택
**1단계 — 모듈 선택**: 필요한 `framework-*` 모듈만 의존성에 추가.
**2단계 — 프로퍼티 토글**: `framework.core.*` / `framework.security.*` / `framework.commoncode.enabled` / `framework.file.*` / `framework.cache.*` / `framework.crypto.*` 등. 미설정 시 기본 활성(matchIfMissing).

## 컨테이너 / 배포
```bash
# jar 는 (Jenkins에서) ./gradlew :services:user-service:bootJar 로 먼저 빌드
docker build -f deploy/docker/Dockerfile \
  --build-arg JAR_FILE=services/user-service/build/libs/user-service-1.0.0.jar \
  -t user-service .
kubectl apply -f deploy/k8s/
```
- Dockerfile: 런타임 전용(레이어 추출), 비루트 실행, 엔트리포인트 `org.springframework.boot.loader.launch.JarLauncher`.
- K8s: Actuator 프로브(startup/liveness/readiness) + HPA(CPU 70%, 2~10).

## 확장 가이드
- 새 업무 서비스: `services/` 아래 모듈 추가 → `settings.gradle` include → `framework-*` 의존.
- 업무별 에러코드: `ErrorCode` 인터페이스를 각 서비스 enum으로 구현.
- 의존성 추가/변경: `gradle/libs.versions.toml` 수정 후 `STACK.md` 갱신.
