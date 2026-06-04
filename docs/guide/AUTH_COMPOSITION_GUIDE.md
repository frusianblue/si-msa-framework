# 인증·아이덴티티 구성 가이드 (Composition Decisions)

> **누가 보나**: 프레임워크를 조립하는 **오너**. [`FRAMEWORK_MODULES.md`](../FRAMEWORK_MODULES.md) 가 "무엇이 있나"(카탈로그)라면, 이 문서는 **"무엇을 골라 어떻게 조합하나"**(결정·레시피)다.
> 사업유형 일괄 프리셋은 [`USAGE_BY_PROJECT_TYPE.md`](USAGE_BY_PROJECT_TYPE.md), 모듈 의존/토글은 [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md).

**구현 상태 표기** — ✅ 구현됨(바로 켬) · 🟡 기존 모듈 설정으로 가능 · ⬜ 미구현(필요 시 추가). 같은 방식으로 추후 다른 영역(데이터/연계 등)도 확장 가능.

---

## 결정 순서

1. [인증 방식](#1-인증-방식) — 신원을 어떻게 확인하나 (가장 먼저)
2. [상태 관리 / 토큰 저장](#2-상태-관리--토큰-저장) — 발급한 토큰을 어떻게 다루나
3. [소셜 연동](#3-소셜-연동) — 외부 간편 로그인
4. [보안 강화 (2차 인증)](#4-보안-강화-2차-인증) — 고가치/규제 대비
5. [엔터프라이즈 / SSO](#5-엔터프라이즈--sso) — 사내 통합·대규모 MSA
6. [★ 추천 조합 (레시피)](#6--추천-조합-레시피)
7. [아직 없는 것 (추가 후보)](#7-아직-없는-것-추가-후보)

---

## 1. 인증 방식

> "사용자 신원을 누가 확인하고, 토큰은 누가 발급하나?"

| 방식 | 설명 | 켜는 것 | 언제 | 상태 |
|---|---|---|---|---|
| **A. 자체 회원 + 토큰** | 우리 DB 회원이 로그인 → 우리가 JWT 발급 | `framework-security`(코어) | 자체 회원제 서비스 | ✅ |
| **B. 외부 위임** | 외부 IdP(소셜/SAML)가 신원확인 → **우리가 자체 JWT 발급** | `framework-oauth-client`(소셜/OIDC) / `framework-saml-sp`(SAML) + security | B2C 간편가입, 공공/대기업 SSO | ✅ |
| **C. 중앙 토큰 발급(OP)** | 중앙 인가서버가 그룹사/MSA 전체에 토큰 발급, 각 서비스는 검증만 | `services/auth-server`(OP) + 서비스 `framework.security.resource-server` | 그룹사 통합, 대규모 MSA | ✅ |
| **D. 서버 세션 기반** | 서버 HttpSession 으로 상태 유지(쿠키 세션ID) | — | 레거시 호환·SSR | ⬜ 미구현(현 프레임워크는 토큰 우선). 필요 시 `framework-session` 추가 |

> A/B/C 는 모두 **최종적으로 우리 JWT** 로 수렴한다(외부는 신원확인까지, 발급은 우리). 그래서 B/C 를 골라도 다운스트림 인가(RBAC)·MFA 는 동일하게 얹힌다. 상세: [`framework-security/README.md`](../../framework/framework-security/README.md), 토큰 검증 자세 [`../reference/TOKEN_VERIFICATION_GUIDE.md`](../reference/TOKEN_VERIFICATION_GUIDE.md).

---

## 2. 상태 관리 / 토큰 저장

> 1장에서 발급한 JWT 를 "어디에 두고, 무효화할 수 있나". 인증 방식과 직교 — A/B/C 위에 공통으로 얹는다.

| 전략 | 무효화/로그아웃 | 멀티 인스턴스 | 켜는 것 | 상태 |
|---|---|---|---|---|
| **무상태 JWT**(저장 안 함) | ✕ (만료까지 유효) | 자연히 공유 | 기본 | ✅ |
| **JWT + TokenStore(JDBC)** | ○ (서버측 무효화) | DB 공유 | `token-store.type=jdbc` | ✅ |
| **JWT + TokenStore(Redis)** | ○ | ○ (권장) | `framework-redis` + `token-store.type=redis` | ✅ |
| **로그인 잠금 공유** | — | ○ | `login-attempt.type=redis\|jdbc` | ✅ |
| **쿠키 전달** | (저장 전략과 별개) | — | JWT 를 `Set-Cookie`(HttpOnly)로 내리는 전달 선택. 기본은 `Authorization` 헤더 | 🟡 |

> **판단**: 단일 인스턴스 데모 = `memory`. 운영(replicas≥2) = **redis**(토큰·로그인잠금 공유). 강제 로그아웃/블랙리스트가 필요하면 `memory` 불가 → `jdbc`/`redis`.

---

## 3. 소셜 연동

| 기능 | 설명 | 켜는 것 | 상태 |
|---|---|---|---|
| OAuth2 소셜 로그인 | 인가코드 → userinfo → 자체 JWT | `framework-oauth-client` (google/kakao/naver 프리셋) | ✅ |
| OIDC RP | id_token 검증(JWKS·iss·aud·exp·nonce) + discovery | `providers.<id>.oidc.enabled=true` | ✅ |
| 임의 프로바이더 추가 | 표준 OAuth2/OIDC 면 설정만으로(authorization/token/userinfo URI 또는 issuer) | `providers.<id>.{...}` | 🟡 |

상세: [`../modules/OAUTH_CLIENT.md`](../modules/OAUTH_CLIENT.md) · [`../modules/OIDC_HARDENING.md`](../modules/OIDC_HARDENING.md). 앱은 `OAuthUserResolver` 빈으로 외부 사용자↔내부 사용자 매핑만 구현.

---

## 4. 보안 강화 (2차 인증)

> 금융·어드민·고가치 거래에서 1차 인증 위에 얹는다. `framework-mfa` 가 `framework-security` 로그인에 `MfaGate` 로 분기.

| 수단 | 켜는 것 | 상태 |
|---|---|---|
| TOTP (Authenticator 앱, RFC 6238) | `framework-mfa` `totp.enabled` | ✅ |
| SMS / Email / 알림톡 OTP | `framework-mfa` `otp.enabled` + `framework-notification`(OtpSender) | ✅ |
| 일회용 복구코드(ISMS-P) | mfa 기본 | ✅ |
| 로그인 잠금 / 동시세션 / 비번 만료·이력 | `framework-security` 토글 | ✅ |
| Passkey / WebAuthn(FIDO2) | — | ⬜ 미구현(백로그). 필요 시 `framework-webauthn` 추가 |

상세: [`framework-mfa/README.md`](../../framework/framework-mfa/README.md). TOTP 등록 QR 은 `framework-qr` 로 PNG 변환.

---

## 5. 엔터프라이즈 / SSO

| 기능 | 설명 | 켜는 것 | 상태 |
|---|---|---|---|
| SAML 2.0 SP | 외부 SAML IdP 신원확인 → 자체 JWT | `framework-saml-sp` (⚠️ OpenSAML 전이) | ✅ |
| OIDC SSO (**Keycloak** 등 IdP) | Keycloak/사내 IdP 를 OIDC 프로바이더로 연동 | `framework-oauth-client` OIDC (issuer=Keycloak) | 🟡 (전용 모듈 불요) |
| 중앙 OP(자체 토큰 발급) | 그룹사 위임 토큰 | `services/auth-server` | ✅ |
| 중앙 로그아웃(SLO) | IdP 로그아웃 시 우리 세션 무효화 | `saml-sp` `slo.enabled` | ✅ |
| API Gateway 엣지 인증 | 게이트웨이에서 JWT 1차 검증 후 헤더 전파 | `services/gateway` | ✅ |
| Keycloak 전용 어댑터 | — | ⬜ (보통 OIDC/SAML 로 충분) |

상세: [`../modules/SAML_SP.md`](../modules/SAML_SP.md) · [`../modules/AUTH_SERVER.md`](../modules/AUTH_SERVER.md) · [`../modules/GATEWAY_EDGE_AUTH.md`](../modules/GATEWAY_EDGE_AUTH.md) · [`../modules/SSO_CENTRAL_LOGOUT.md`](../modules/SSO_CENTRAL_LOGOUT.md).

> **Keycloak**: 별도 모듈 없이 — OIDC 면 `oauth-client`(issuer 를 Keycloak realm 으로), SAML 이면 `saml-sp`(IdP 메타데이터를 Keycloak 으로). 즉 "Keycloak 연동"은 3·5장의 기존 모듈로 해결된다.

---

## 6. ★ 추천 조합 (레시피)

> 가장 많이 쓰는 조합을 그대로 복사. "왜"와 "켜는 것"을 함께.

### R1. B2C 소셜 서비스 — *가장 표준* ✅
**OAuth2 소셜 로그인 + 자체 JWT + Redis 토큰관리**
```
모듈: framework-oauth-client + framework-security + framework-redis
yml : framework.oauth-client.enabled=true, providers.{google,kakao}.*
      framework.security.token-store.type=redis, oauth-client.state.store.type=redis
```
왜: 간편가입/소셜로 진입장벽↓, 발급은 우리 JWT 라 인가·정책은 우리가 통제, Redis 로 다중 파드 토큰 공유·강제 로그아웃.

### R2. 자체 회원 단일 서비스(MVP) ✅
**자체 로그인 + 무상태 JWT**
```
모듈: framework-security (코어만)
yml : framework.security.token-store.type=memory   # 단일 인스턴스면 충분
```
왜: 최소 구성. 인스턴스 늘면 R 계열로 token-store=redis 전환.

### R3. 금융 백오피스 / 어드민 ✅
**자체 로그인 + JWT + Redis + MFA(TOTP) + 로그인잠금/동시세션/비번이력**
```
모듈: framework-security + framework-redis + framework-mfa (+ framework-qr)
yml : security.password.{expiry,history}.enabled=true, security.concurrent-session.enabled=true
      security.login-attempt.type=redis, mfa.enabled=true mfa.totp.enabled=true
```
왜: ISMS-P/보안성 심의 대비 2차 인증·계정 정책. 어드민·고가치 화면 보호.

### R4. 공공 / 대기업 SSO ✅
**SAML 2.0 SP (또는 OIDC) + 자체 JWT + 중앙 로그아웃**
```
모듈: framework-saml-sp + framework-security (+ framework-redis: 멀티파드 AuthnRequest)
yml : saml-sp.enabled=true, registrations.<idp>.metadata-uri=..., saml-sp.slo.enabled=true
대안: Keycloak/OIDC IdP 면 framework-oauth-client OIDC 로 동일 효과
```
왜: 기관/그룹 표준 IdP 와 통합, 중앙 로그아웃으로 세션 일관성.

### R5. 그룹사 통합 / 대규모 MSA ✅
**중앙 OP(auth-server) 토큰 발급 + 게이트웨이 엣지 인증 + 서비스 resource-server 재검증**
```
구성: services/auth-server(OP) 발급 → services/gateway 엣지 JWT 검증 → 각 서비스 framework.security.resource-server.enabled=true (issuer=AS)
```
왜: 한 곳에서 발급/회전, 게이트웨이가 1차 차단, 서비스는 필요 시 zero-trust 재검증. 망 격리 강하면 `edge-trust.mode=gateway-headers` 로 비용↓.

### R6. 소셜 + 2차 인증(고가치 거래) ✅
**R1 + MFA** — OAuth2 소셜 + 자체 JWT + Redis + MFA(TOTP/SMS). 간편가입은 소셜, 결제 등 민감 동작에서만 2차 인증 요구.

---

## 7. 아직 없는 것 (추가 후보)

필요해지면 **표준 3단 토글 규약**(클래스패스→`enabled`→구현선택)을 따라 새 모듈을 만들어 끼운다 — 기존 코드 수정 없이.

| 후보 | 무엇 | 신설 모듈(예) |
|---|---|---|
| Passkey / WebAuthn | FIDO2 비밀번호 없는 인증 | `framework-webauthn` (security `MfaGate`/인증기 SPI 에 연결) |
| 서버 세션 기반 인증 | HttpSession + 세션 클러스터 | `framework-session` (Spring Session 위임) |
| Keycloak 전용 어댑터 | (현재는 OIDC/SAML 로 대체) | 보통 불필요 |
| OP 확장 | Device Flow, 토큰 교환 등 | `auth-server` 확장 |

**추가 절차(요약)**: ① `framework-<x>` 디렉터리 + `build.gradle`(선택 의존은 `compileOnly`) ② `@ConditionalOnClass`→`@ConditionalOnProperty`→`@ConditionalOnMissingBean` 3단 오토컨피그 ③ `settings.gradle` include ④ `framework-archtest/build.gradle` 에 `testImplementation project(...)` 한 줄(검사 포함) ⑤ 모듈 README + 이 표 갱신. 상세 규약은 [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md) §0.
