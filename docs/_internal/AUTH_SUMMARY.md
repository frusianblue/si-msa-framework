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
| `examples/auth-types` | **카탈로그**(학습·체감) — 한 앱에서 프로파일 전환으로 T1~T10 A/B 비교 | 독립 빌드(standalone `settings.gradle`), `com.company:framework-*:1.0.0` 소비 | `com.example.authtypes` |
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
| T1 | 세션 | ◐ `t1-form-session` 프로파일 생성 | ◐ `auth-session-service` 골격 생성 |
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
(`POST /api/v1/auth/session/login | /logout`, `Set-Cookie: SESSION=...`).

- **데모 인증기**: `DemoAuthenticator implements Authenticator` (인메모리 사용자, BCrypt 검증).
- **부팅 전제**: framework-security 가 `framework-mybatis` 를 전이로 끌어옴 → SqlSessionFactory 용 **DataSource(H2) 필수**.
  데모는 `dynamic-authorization=false` + `menu=false` 로 rbac DB 조회를 끄고, H2 빈 스키마로 부팅.
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

---

## 7. 다음 (Next)

### 먼저 처리(이번 섹션에서 결정, 미적용)
1. **카탈로그 빌드 통일** — `examples/auth-types` 를 독립 빌드 → **루트 멀티프로젝트로 편입**
   (루트 `settings.gradle` 에 `include 'examples:auth-types'`, `build.gradle` 의존을 `'com.company:..:1.0.0'` →
   `project(':framework:..')` 로 전환). 그러면 `publishToMavenLocal` 불필요, `:examples:auth-types:bootRun` 으로 바로 실행.
2. **보안-영속 결합 분리(리팩터)** — `docs/_internal/planning/NEXT_SECURITY_PERSISTENCE_DECOUPLING.md` 착수.
   `framework-security` 의 MyBatis/DataSource 강제 결합을 RBAC 포트/어댑터(`framework-security-rbac-mybatis`)로 분리.
   완료 후 T1 데모에서 H2/DataSource 제거(인증만 → DataSource 불필요).

### 트랙 진행
3. **T1 로컬 부팅·curl 검증** (Chae 로컬) — 카탈로그 `t1-form-session` + `auth-session-service` 양쪽.
4. **T1 멀티팟 체감** — `auth-session-service` replicas=2 로 띄워 세션 외부화 on/off 차이 확인(`framework-session`).
5. **T2(무상태 JWT)로 상태 축 비교** — `auth-jwt-service` + 카탈로그 `t2-jwt` 프로파일. 로그아웃·확장 차이 기록.
