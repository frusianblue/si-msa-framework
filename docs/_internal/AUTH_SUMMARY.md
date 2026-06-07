# AUTH_SUMMARY.md — 인증 유형별 실험 트랙 (독립 써머리)

> **독립성 선언**: 이 문서는 `HANDOFF_SUMMARY.md` / `HANDOFF.md` 와 **완전히 독립적인 앵커 문서**다.
> 인증(AuthN) 유형을 분리 구현해 **차이를 직접 체감**하기 위한 실험 트랙 전용 써머리이며,
> 메인 핸드오프 체계와 교차 참조하지 않는다. (검증된 교훈만 `docs/guide/PITFALLS.md` 로 승격)
>
> 개념 참고서: **[`AUTH_TYPES_REFERENCE.md`](./AUTH_TYPES_REFERENCE.md)** — 인증/인가 유형 카탈로그(이론).
> 이 써머리(실행/진행) ↔ 참고서(개념)를 짝으로 본다.

---

## 0. 목적

- 인증을 **유형별로 분리 구현**해, 각 방식이 실제로 어떻게 다르게 동작하는지 손으로 만져본다.
- **한 번에 한 축(axis)만 바꿔** A/B 비교한다 → 차이의 원인을 한 변수로 격리.
- 운영 관점(K8s/CICD/DevOps)에서 각 방식이 멀티팟·확장·로테이션에 어떻게 반응하는지 체감.

---

## 1. 두 갈래 산출물 (역할 분리)

| 위치 | 역할 | 빌드 형태 | 패키지 |
|------|------|-----------|--------|
| `examples/auth-types` | **카탈로그**(학습·체감) — 한 앱에서 프로파일 전환으로 T1~T10 A/B 비교 | 루트 멀티프로젝트 편입(`include 'examples:auth-types'`), `project(':framework:..')` 직접 의존 | `com.example.authtypes` |
| `services/auth-*-service` | **실서비스**(실전 적용) — 방식별로 쪼갠 배포 가능한 서비스 | 루트 `settings.gradle` include, `project(':framework:..')` 의존 | `com.company.<방식>` |

`examples` = 비교 실험, `services` = 실전 적용. 카탈로그에서 체감 → 실서비스에 반영하는 순서.

### 실서비스 분할 (입도 = 인증 방식 4종, 네이밍 `auth-<방식>-service`)

| 서비스 | 대응 트랙 | 인증 방식 |
|--------|-----------|-----------|
| `services/auth-session-service` | T1 | 서버 세션(HttpSession + 쿠키) |
| `services/auth-jwt-service` | T2 | 무상태 JWT |
| `services/auth-oidc-service` | T3 | OIDC RP 위임 |
| `services/auth-saml-service` | T4 | SAML SP 위임 |

> trust(T5/T6) · factor(T7/T8) · subject(T9/T10) 축은 위 4서비스의 **프로파일/환경변수 변형**으로 흡수한다
> (예: `auth-jwt-service` 에 `EDGE_TRUST_MODE=gateway-headers` 로 T5, `zero-trust` 로 T6).

---

## 2. 비교 원칙 (실험 설계)

1. **공통 보호 리소스 고정**: 모든 트랙이 동일한 `GET /api/resource`(인증된 사용자만 200)를 보호.
2. **한 축만 변경**: 베이스라인(T1)에서 출발해 한 트랙당 정확히 하나의 변수만 바꾼다.
3. **체감 포인트 명시**: 각 트랙은 "무엇이 달라지는가"를 한 줄로 선언하고 그것만 확인.
4. **같은 코드, 다른 토글**: 카탈로그는 프로파일로 전환(코드 분기 최소화) → 운영 현실 반영.

---

## 3. 변이 축 & 빌드 순서

| 축 | 양 끝 | 비교 트랙 |
|----|-------|-----------|
| 상태(State) | 세션 ↔ 무상태 JWT | T1 ↔ T2 |
| 발급자(Issuer) | self ↔ 위임(OIDC/SAML) | T2 ↔ T3 ↔ T4 |
| 신뢰(Trust, **K8s 핵심**) | 엣지 헤더 ↔ zero-trust 재검증 | T5 ↔ T6 |
| 요소(Factor) | 1FA ↔ MFA(TOTP/WebAuthn) | T7 ↔ T8 |
| 주체(Subject) | 사용자 ↔ 워크로드 | T9 ↔ T10 |

빌드 순서(축-페어): ① 상태 → ② 발급자 → ③ 신뢰 → ④ 요소 → ⑤ 주체. 베이스라인은 **T1**.

---

## 4. 진행 상태

| 트랙 | 방식 | 카탈로그(`examples/auth-types`) | 실서비스(`services/`) |
|------|------|-------------------------------|----------------------|
| T1 | 세션 | ☑ `t1-form-session` curl 4~5단계 검증 통과 + 루트 편입(#1) | ☑ `auth-session-service` 8081 curl 1·5=401·2·3·4=200 검증 통과 |
| T2 | 무상태 JWT | ☐ | ☐ `auth-jwt-service` |
| T3 | OIDC RP | ☐ | ☐ `auth-oidc-service` |
| T4 | SAML SP | ☐ | ☐ `auth-saml-service` |
| T5 | 엣지 신뢰 헤더 | ☐ (T2 변형) | ☐ (jwt-service 프로파일) |
| T6 | zero-trust | ☐ (T2 변형) | ☐ (jwt-service 프로파일) |
| T7 | +TOTP | ☐ | ☐ |
| T8 | +WebAuthn | ☐ | ☐ |
| T9 | mTLS | ☐ | ☐ (메시) |
| T10 | client_credentials/API Key | ☐ | ☐ |

> 범례: ☐ 미착수 / ◐ 진행 / ☑ 완료(테스트 통과) / ⚠ 보류

---

## 5. T1 실행 메모 (세션 기반)

프레임워크 매핑: `framework.security.session.mode=session` → `SessionAuthController` 등록
(`POST /api/v1/auth/session/login | /logout`, 단일 파드는 `Set-Cookie: JSESSIONID=...`(톰캣 기본). framework-session 추가 시 `SESSION`).

- **데모 인증기**: `DemoAuthenticator implements Authenticator` (인메모리 사용자, BCrypt 검증).
- **부팅 전제(2026-06-07 보안-영속 결합 분리 이후 갱신)**: framework-security 는 더 이상 `framework-mybatis`/`spring-jdbc` 를
  전이로 끌어오지 않는다(RBAC 영속=어댑터 분리, JDBC 저장소=`compileOnly`+`@ConditionalOnClass` 가드). 데모는 `dynamic-authorization=false`
  + `menu=false` 라 RBAC provider 가 없어도 fail-fast 에 안 걸리고, SqlSessionFactory/DataSourceAutoConfiguration 자체가 비활성 →
  **DataSource/H2 전혀 불필요**(인메모리 인증기만으로 기동). 과거의 빈 rbac 스키마(H2 `INIT=RUNSCRIPT`)·`rbac-empty-schema.sql` 은
  제거됐다 — 그 WARN 의 원인이던 무조건 `SecurityMetadataService` 생성이 `@ConditionalOnBean(ResourceMetadataProvider)` 로 바뀌어 사라졌다(§6 참고).
- **체감 포인트**:
  - 단일 파드: 톰캣 `HttpSession` 으로 충분.
  - replicas≥2: 세션 공유 필요 → `framework-session`(Spring Session Redis) 의존 추가. **안 하면 파드 전환 시 로그아웃** ← T1의 핵심 체감.
  - 로그아웃 = 서버 세션 무효화(`session.invalidate()`). T2(JWT)와의 대비점.
  - CSRF: 세션 모드 기본 on(쿠키 더블서브밋). `GET /api/resource` 와 `/api/*/auth/**` 는 영향 없음.

데모 계정: `alice/Password1!`(USER), `admin/Admin1234!`(ADMIN,USER).

검증 흐름(curl):
```bash
# 1) 보호 리소스 — 미인증 401
curl -i localhost:8080/api/resource

# 2) 세션 로그인 → 쿠키 저장
curl -i -c cookies.txt -X POST localhost:8080/api/v1/auth/session/login \
  -H 'Content-Type: application/json' -d '{"loginId":"alice","password":"Password1!"}'

# 3) 쿠키로 보호 리소스 — 200
curl -i -b cookies.txt localhost:8080/api/resource

# 4) 로그아웃 → 세션 무효화
curl -i -b cookies.txt -X POST localhost:8080/api/v1/auth/session/logout
```

---

## 6. 트랙 전용 Pitfalls 레저 (독립)

> 이 트랙에서 직접 겪은 함정만 append-only. 일반화 가치가 검증되면 `docs/guide/PITFALLS.md` 로 승격.

- **[일반 known] framework-security 는 framework-mybatis 전이 의존** — Authenticator 만 쓰는 데모라도
  SqlSessionFactory 가 필요해 **DataSource(H2 등)가 없으면 부팅 실패**. 해결: H2 runtimeOnly + datasource 설정,
  rbac 미사용 시 `dynamic-authorization=false`/`menu=false` 로 DB 조회 회피(스키마는 빈 채로 OK).
  - ✅ **무효화됨(2026-06-07, #2 보안-영속 결합 분리)**: 코어는 더 이상 `framework-mybatis`/`spring-jdbc` 를 전이로 끌어오지 않는다.
    RBAC 영속은 `framework-security-rbac-mybatis` 어댑터로, JDBC 저장소는 `compileOnly`+`@ConditionalOnClass(JdbcTemplate)` 로 분리.
    → 인증만 쓰는 서비스는 **H2/DataSource 없이 부팅**(이 항목의 "DataSource 없으면 부팅 실패"는 더 이상 성립하지 않음).
- **[일반 known] 세션 모드에서도 JWT `AuthController` 가 동시 등록**(`AuthAutoConfiguration` 은 `@ConditionalOnBean(Authenticator)` 만 검사) —
  경로가 `/api/v1/auth/login`(JWT) vs `/api/v1/auth/session/login`(세션)으로 분리돼 **충돌 아님**. T1은 세션 경로만 사용.
- **[겪음 encountered] standalone 예제를 루트 멀티프로젝트로 편입할 때(#1)** — standalone `build.gradle` 의
  `id 'org.springframework.boot' version '4.0.6'` / `id 'io.spring.dependency-management' version '1.1.7'` 를
  그대로 두면, 루트가 이미 클래스패스에 올린(`apply false`) 플러그인과 **버전 중복 선언 충돌**("plugin already on the classpath
  must not include a version"). 해결: 버전 제거하고 적용만(`id 'org.springframework.boot'`), 의존은 `'com.company:..:1.0.0'`
  → `project(':framework:..')`, group/version/toolchain/repositories/dependency-management/JUnit·인코딩은 루트 상속에 맡김
  (형제 `services/auth-session-service` 와 동형). 또한 편입한 디렉터리의 **standalone `settings.gradle` 는 삭제**해야 한다 —
  남겨두면 그 디렉터리에서 `gradle` 직접 실행 시 그 settings 가 잡혀 `project(':framework:..')` 미해소로 실패하는 함정.
- **[겪음 encountered] `dynamic-authorization=false` 인데도 부팅 시 rbac 테이블 조회 WARN** — `SecurityAutoConfiguration`
  의 `securityMetadataService` 빈은 토글과 **무관하게 항상 생성**되고(`SecurityAutoConfiguration` 161–163줄),
  생성자가 `reload()` → `SecurityMapper.findAllResources()` 를 1회 호출한다. `dynamic-authorization` 토글은
  "조회 결과를 인가 체인에 **연결할지**"만 끄지(306·374줄 `if`), "조회 **자체**"는 막지 못한다. 빈 H2엔 `resources`/`roles`/
  `role_resources` 테이블이 없어 MyBatis 가 `JdbcSQLSyntaxErrorException: Table "RESOURCES" not found` 를 시끄럽게
  찍는다. **단, `reload()` 는 try/catch 로 삼켜 빈 캐시로 시작 → 기동·로그인 정상(무해, 로그 노이즈일 뿐).**
  해결(데모): 빈 rbac 스키마(행 0) 3테이블을 H2 `INIT=RUNSCRIPT FROM 'classpath:db/rbac-empty-schema.sql'` 로 선생성
  → 0행 조회로 조용히 부팅. (schema.sql 은 빈 생성 순서에 따라 reload 후 실행될 수 있어 INIT 절을 택함.)
  근본 해결은 #2(보안-영속 결합 분리) — SecurityMetadataService 의 무조건 DB 결합 제거가 이 WARN 의 진짜 원인.
  - ✅ **해결됨(2026-06-07, #2)**: `securityMetadataService` 가 `@ConditionalOnBean(ResourceMetadataProvider)` 로 바뀌어
    RBAC provider(어댑터)가 없는 데모에선 **아예 생성되지 않는다** → `findAllResources()` 호출 자체가 사라짐 = WARN 소멸.
    이에 따라 H2 `INIT=RUNSCRIPT` 와 `db/rbac-empty-schema.sql`(양 데모)·datasource 설정을 **전부 제거**했다(§6-7). 인증 전용 데모는 이제 DataSource 없이 기동.
- **[겪음 encountered] `framework.security.session.cookie-name` 은 현재 어디서도 소비되지 않는 "죽은" 설정** —
  T1 검증에서 쿠키가 설정값 `SESSION` 이 아니라 톰캣 기본 `JSESSIONID` 로 나왔다. `FrameworkSecurityProperties.Session.cookieName`
  에 필드/게터/세터·기본값("SESSION")은 있으나 `getCookieName()` 호출부가 framework-security 에 없다(소비처는 전부 secure-web XSRF·
  saml-sp 의 별개 프로퍼티). framework-session(Spring Session) 도 쿠키 이름을 따로 안 잡고 Boot 표준 `SessionAutoConfiguration`
  에 위임 → 그때 Spring Session `DefaultCookieSerializer` **기본값**이 우연히 `SESSION` 일 뿐(이 프로퍼티가 만든 게 아님).
  ∴ 단일 파드=`JSESSIONID`, framework-session 추가 시=`SESSION`, **둘 다 이 프로퍼티와 무관**. 쿠키 이름을 실제로 바꾸려면
  표준 `server.servlet.session.cookie.name`(톰캣·Spring Session 양쪽을 Boot 가 함께 적용)을 써야 한다.
  → **결정됨: (a)** — `framework.security.session.cookie-name` 을 표준 `server.servlet.session.cookie.name` 으로 배선해
    실제로 쿠키 이름을 바꾼다(톰캣·Spring Session 양쪽을 Boot 가 함께 적용). framework-security 변경이라 문서 캐스케이드 동반
    (모듈 README 켜는법/쓰는법, FRAMEWORK_MODULES, AUTH_COMPOSITION_GUIDE 등). 다음다음 세션 이후 처리(우선순위는 8081 검증 → #2 뒤).
    T1 검증엔 무영향(curl 쿠키 jar 는 이름 무관 캡처).

---

## 7. 다음 (Next)

> **다음 세션 시작점**: **T1 멀티팟 세션 외부화**(아래 트랙진행 #5). `auth-session-service` replicas≥2 로 띄워
> 세션 외부화 on/off 차이를 체감한다(`framework-session`/Spring Session Redis). 단일 파드=톰캣 `HttpSession`,
> 외부화 안 하면 파드 전환 시 로그아웃 ← T1 핵심 체감. 외부화하면 `framework-session` 추가로 파드 무관 세션 유지.
>
> **이월(받는 쪽 1회)**: #2 보안-영속 결합 분리 코드/문서 ☑(이번 세션 완료) — 적용 후 로컬 빌드/테스트 그린만 확인하면 종료
> (`apply.sh` 또는 패치, 기준 HEAD `e6b16cb`). 그린 시 [`planning/NEXT_SECURITY_PERSISTENCE_DECOUPLING.md`](./planning/NEXT_SECURITY_PERSISTENCE_DECOUPLING.md) → `docs/_internal/archive/`.
> (T1 양쪽 8080·8081 curl 검증은 ☑ 완료 — 트랙진행 #4.)

### 먼저 처리
1. ✅ **카탈로그 빌드 통일(완료)** — `examples/auth-types` 를 standalone → **루트 멀티프로젝트로 편입**.
   루트 `settings.gradle` 에 `include 'examples:auth-types'`, `build.gradle` 의존을 `'com.company:..:1.0.0'`
   → `project(':framework:..')` 로, 플러그인 버전 선언 제거(루트 상속). standalone `settings.gradle` 삭제.
   이제 `publishToMavenLocal` 불필요, repo 루트에서 `:examples:auth-types:bootRun` 으로 바로 실행.
   ⚠️ 예제는 jacocoAggregation/archtest 대상이 아님 — 루트 `build.gradle` 의 해당 목록엔 넣지 않았다.
2. **보안-영속 결합 분리(리팩터)** — ✅ **코드 작성 완료(2026-06-07, 미빌드 검증)**. `planning/NEXT_SECURITY_PERSISTENCE_DECOUPLING.md` §6-1~§6-7 ☑.
   `framework-security` 의 MyBatis 결합(→어댑터 분리) + spring-jdbc 결합(→compileOnly+`@ConditionalOnClass` 가드)까지 분리 완료.
   §6-7 데모 H2/DataSource 제거까지 반영(인증 전용 서비스는 DataSource 불필요). 잔여: 받는 쪽(Chae) 로컬 빌드/테스트 그린 확인만.
3. **[결정됨 (a)] `session.cookie-name` 실배선** — 죽은 프로퍼티(§6)를 `server.servlet.session.cookie.name` 으로 연결해
   실제 쿠키 이름을 바꾼다(톰캣·Spring Session 동시 적용). framework-security 변경 → 문서 캐스케이드 동반. 우선순위: 8081 검증 → #2 뒤.

### 트랙 진행
4. ✅ **T1 검증(완료)** — ☑ 카탈로그(8080) curl 4~5단계 통과. ☑ 실서비스(8081) 동일 검증 통과(1·5=401, 2·3·4=200; `principal=alice`/`authorities=[ROLE_USER]`, 쿠키 `JSESSIONID`).
5. **T1 멀티팟 체감(➡ 다음 세션)** — `auth-session-service` replicas=2 로 띄워 세션 외부화 on/off 차이 확인.
   - off(기본): 톰캣 `HttpSession` 은 파드 로컬 → 로그인한 파드와 다른 파드로 라우팅되면 세션 미인식 = 로그아웃처럼 보임.
   - on: `framework-session`(Spring Session Redis) 의존 추가 + Redis → 세션이 외부 저장소에 → 파드 무관 유지.
   - 체감 방법(예): K8s Service 라운드로빈/`kubectl scale`, 또는 두 포트로 띄워 쿠키 jar 로 교차 요청. 로그인→파드A 쿠키→파드B 요청 결과 비교.
   - 결과·전후 차이를 `AUTH_T1_VERIFY.md`(또는 신규 메모)에 기록. 쿠키 이름도 함께 관찰(단일=`JSESSIONID`, Spring Session=`SESSION` — §6 cookie-name 죽은설정 메모 참조).
6. **T2(무상태 JWT)로 상태 축 비교** — `auth-jwt-service` + 카탈로그 `t2-jwt` 프로파일. 로그아웃·확장 차이 기록.

### 세션 닫음 메모
- 직전 세션: #1 카탈로그 루트 편입 / rbac WARN 억제(H2 `INIT=RUNSCRIPT` 빈 스키마) / T1 카탈로그(8080) curl 검증 통과 /
  cookie-name 죽은 설정 발견·(a) 결정 / [`AUTH_T1_VERIFY.md`](./AUTH_T1_VERIFY.md) 작성. → 커밋/푸시 완료(HEAD `0e7f332`).
- T1 실서비스(8081): `auth-session-service` 정적 점검(8080 동형 확인) → 로컬 curl 1·5=401 / 2·3·4=200 통과 → T1 ☑.
- 이번 세션: **#2 보안-영속 결합 분리 — 코드 작성 완료(미빌드 검증).** 신설 어댑터 `framework-security-rbac-mybatis`
  (SecurityMapper+XML FQN 유지 이전 / `MyBatisResourceMetadataProvider`·`MyBatisMenuProvider` / 감사 브리지 `SecurityContextCurrentUserProvider` 이전·`@Primary` / `@AutoConfiguration(before=SecurityAutoConfiguration)` + nested `@ConditionalOnClass(SqlSessionFactory)` + `@MapperScan(annotationClass=Mapper.class)` + `.imports`).
  코어: `rbac.spi` 포트(`ResourceMetadataProvider`/`MenuProvider`/`RbacProviderSafetyGuard`) 신설, `SecurityMetadataService`/`MenuService` 포트화,
  `SecurityAutoConfiguration` 에서 `@MapperScan`·`SecurityMapper`·`CurrentUserProvider` 제거 + RBAC 빈 `@ConditionalOnBean(포트)` + dynamic-authorization=true 시 fail-fast, 두 필터체인 `ObjectProvider<DynamicAuthorizationManager>` 화, build.gradle mybatis 전이 제거.
  등록: settings/archtest(의존+규칙2+가드테스트)/jacoco. 마이그레이션: user/admin/auth-server 어댑터 한 줄(DbAuthenticationProvider 무변경). 문서 캐스케이드 완료(security/adapter README·FRAMEWORK_MODULES·MODULE_COMPOSITION·AUTH_COMPOSITION_GUIDE·PITFALLS·root/framework README).
  착수 시 발견: spring-jdbc 가 mybatis 전이로만 코어에 들어왔음(JdbcTokenStore 등) → **spring-jdbc 도 compileOnly + `JdbcTemplate` @Bean nested `@ConditionalOnClass` 가드화**(framework-lock 패턴)로 함께 분리.
  이로써 §6-7 완수: `auth-session-service`·`examples/auth-types` 의 **H2/DataSource/rbac-empty-schema.sql 제거**(인증 전용 → DataSource 불필요). §5 부팅 전제·§6 WARN 항목 해소 기록.
  **다음 = 받는 쪽 빌드/테스트 그린 확인.** (작성 환경 Maven Central 차단 → Gradle 미실행, 정적 작성.)
- 세션 마무리(2026-06-07): #2 코드/문서 전부 정리 + **새 클론(현재 원격 HEAD `e6b16cb`)에 재적용·재검증** — 원격이 17커밋(전부 K8s/GitOps 문서) 전진했으나 코드 충돌 0,
  겹친 문서 3건(FRAMEWORK_MODULES·AUTH_SUMMARY·PITFALLS)은 3-way 머지로 양쪽 보존. **놓쳤던 stale 문서 2건(`auth-session-service`/`examples:auth-types` README 부팅메모) 수정**,
  §6 옛 known-issue 항목에 "무효화됨" 표시, 계획서 중복 체크 한 줄 정리. 빈 클론+`apply.sh`→검증 트리 바이트 일치·`git apply --3way` 클린 확인. 한방 적용 zip(overlay+patch+apply.sh+BASE_COMMIT) 산출.
  **→ 이번 세션 종료. 다음 세션 = 트랙진행 #5 T1 멀티팟 세션 외부화.** (#2 는 받는 쪽 빌드 그린 1회만 남음 — 그린 시 계획서 archive.)

