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
> 위는 핵심 모듈만 표기한 단순도. 선택 모듈(`framework-openapi/redis/commoncode/file/file-s3`)과 `services/admin-service`(8081)는 아래 상세 섹션 참고.

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
- **OWASP Dependency-Check**: CVSS 7.0+ 발견 시 빌드 실패. `./gradlew dependencyCheckAggregate`.
- **SonarQube**: 정적분석/보안 핫스팟/커버리지.
- **CI = Jenkins**(`deploy/cicd/Jenkinsfile`): Build&Test(Testcontainers+JaCoCo) → 품질 게이트(Spotless/OWASP/Sonar 병렬) → Flyway Validate(운영DB) → 이미지 빌드/푸시 → K8s 롤아웃. (구 `ci-cd.yml`(GitHub Actions)은 레거시 — 사용 시 정리 권장)

## 처음 실행하기
```bash
# 1) 로컬(H2) 프로필로 user-service 기동 (gradlew 동봉 — 별도 gradle 설치 불필요)
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local'

# 2) 호출 예시 (실 로그인: loginId + password)
curl -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"loginId":"admin","password":"admin123"}'
curl -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' \
     -d '{"loginId":"hong","name":"홍길동","email":"hong@test.com","phone":"010-1234-5678"}'
```

## 컨테이너 / 배포
```bash
# 런타임 전용 Dockerfile: jar 는 CI(Jenkins)가 먼저 빌드 → JAR_FILE 로 주입(레이어 추출, JarLauncher 기동)
./gradlew :services:user-service:bootJar
docker build -f deploy/docker/Dockerfile \
  --build-arg JAR_FILE=services/user-service/build/libs/*.jar -t user-service .
kubectl apply -f deploy/k8s/
```
> 실제 운영 흐름은 `deploy/cicd/Jenkinsfile` 한 곳에서 빌드·테스트·게이트·이미지·롤아웃을 수행한다.

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
}
```

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
