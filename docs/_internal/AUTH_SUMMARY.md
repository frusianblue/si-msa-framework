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
- **부팅 전제**: framework-security 가 `framework-mybatis` 를 전이로 끌어옴 → SqlSessionFactory 용 **DataSource(H2) 필수**.
  데모는 `dynamic-authorization=false` + `menu=false` 로 rbac **인가 연결**을 끈다. ⚠️ 단 `SecurityMetadataService` 는
  토글과 무관하게 부팅 시 `findAllResources()` 를 1회 호출하므로(§6 참고), 빈 rbac 스키마(행 0)를 H2 `INIT=RUNSCRIPT`
  로 선생성해 조용히 부팅한다(없으면 무해하지만 시끄러운 WARN).
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

> **다음 세션 시작점**: T1 양쪽(8080 카탈로그 · 8081 실서비스) curl 검증 완료 ☑. 다음은 아래 중 택1 —
> **#2 보안-영속 결합 분리**(authoring · framework 리팩터) / **T1 멀티팟 체감**(replicas=2 세션 외부화) / **T2 무상태 JWT**(상태 축 비교).

### 먼저 처리
1. ✅ **카탈로그 빌드 통일(완료)** — `examples/auth-types` 를 standalone → **루트 멀티프로젝트로 편입**.
   루트 `settings.gradle` 에 `include 'examples:auth-types'`, `build.gradle` 의존을 `'com.company:..:1.0.0'`
   → `project(':framework:..')` 로, 플러그인 버전 선언 제거(루트 상속). standalone `settings.gradle` 삭제.
   이제 `publishToMavenLocal` 불필요, repo 루트에서 `:examples:auth-types:bootRun` 으로 바로 실행.
   ⚠️ 예제는 jacocoAggregation/archtest 대상이 아님 — 루트 `build.gradle` 의 해당 목록엔 넣지 않았다.
2. **보안-영속 결합 분리(리팩터)** — `docs/_internal/planning/NEXT_SECURITY_PERSISTENCE_DECOUPLING.md` 착수.
   `framework-security` 의 MyBatis/DataSource 강제 결합을 RBAC 포트/어댑터(`framework-security-rbac-mybatis`)로 분리.
   완료 후 T1 데모에서 H2/DataSource 제거(인증만 → DataSource 불필요).
3. **[결정됨 (a)] `session.cookie-name` 실배선** — 죽은 프로퍼티(§6)를 `server.servlet.session.cookie.name` 으로 연결해
   실제 쿠키 이름을 바꾼다(톰캣·Spring Session 동시 적용). framework-security 변경 → 문서 캐스케이드 동반. 우선순위: 8081 검증 → #2 뒤.

### 트랙 진행
4. ✅ **T1 검증(완료)** — ☑ 카탈로그(8080) curl 4~5단계 통과. ☑ 실서비스(8081) 동일 검증 통과(1·5=401, 2·3·4=200; `principal=alice`/`authorities=[ROLE_USER]`, 쿠키 `JSESSIONID` = 정적 점검 예고대로).
5. **T1 멀티팟 체감** — `auth-session-service` replicas=2 로 띄워 세션 외부화 on/off 차이 확인(`framework-session`).
6. **T2(무상태 JWT)로 상태 축 비교** — `auth-jwt-service` + 카탈로그 `t2-jwt` 프로파일. 로그아웃·확장 차이 기록.

### 세션 닫음 메모
- 직전 세션: #1 카탈로그 루트 편입 / rbac WARN 억제(H2 `INIT=RUNSCRIPT` 빈 스키마, 양쪽) / T1 카탈로그(8080) curl 검증 통과 /
  cookie-name 죽은 설정 발견·문서 정정·(a) 결정 / 재실행용 [`AUTH_T1_VERIFY.md`](./AUTH_T1_VERIFY.md) 작성.
  → 커밋/푸시 완료(HEAD `0e7f332 "추가"`, master). 이전의 "미커밋" 메모는 해소됨.
- 이번 세션: `auth-session-service` 8081 정적 점검(골격 ↔ 8080 카탈로그 동형 확인) → 로컬 curl 실행 → **T1 실서비스 ☑ 검증 통과**.
  새 함정 없음(§6 추가 없음). 본 문서 갱신분만 커밋/푸시 필요.

