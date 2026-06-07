# AUTH_TYPES_REFERENCE.md — 인증·인가 유형 참고서 (개념)

> 실행/진행 추적은 짝 문서 **[`AUTH_SUMMARY.md`](./AUTH_SUMMARY.md)** 를 본다.
> 이 문서는 "어떤 인증·인가 방식이 있고 무엇이 다른가"의 **개념 카탈로그**다(Spring Security 7 / Boot 4 기준).

---

## 1. 인증 (Authentication) — "누구인가"

| 유형 | 메커니즘 | 상태 | 대표 용도 | 핵심 체감 |
|------|----------|------|-----------|-----------|
| 세션(Form/REST 로그인) | 로그인 후 `HttpSession` + 쿠키(`SESSION`/`JSESSIONID`) | stateful | SSR·관리콘솔·BFF | 멀티팟이면 세션 외부화(Redis) 필요, 로그아웃=서버 무효화 |
| HTTP Basic | `Authorization: Basic` | - | 내부 보호·액추에이터 | 단독 프로덕션 사용자 인증엔 부적합 |
| 무상태 JWT(self-issued) | 앱이 발급·검증, 클레임 자체에 신원 | stateless | MSA 내부 기본 | 세션 없음→수평확장 자유, 즉시 로그아웃 어려움(만료/블랙리스트) |
| OAuth2 Resource Server | 토큰 **검증만**(JWKS 공개키) | stateless | 토큰 수신 서비스 | 키 로테이션 자동 반영, 멀티팟에 최적 |
| OAuth2 Login / OIDC RP | 외부 IdP 위임, code+PKCE → id_token | 세션/토큰 | 위임 로그인 | redirect_uri https 복원 필요, confidential 은 `CLIENT_SECRET_POST`+`requireProofKey(false)` |
| SAML SP | 사내 IdP 위임, XML assertion | 세션 | 레거시 엔터프라이즈(금융/공공) | 별도 체인 `securityMatcher` 경로 한정으로 메인 체인과 공존 |
| MFA(TOTP/WebAuthn) | 1차 위에 2차 요소 | (상속) | 규제(ISMS-P) | 요소별 발급시각/`auth_time` 추적, WebAuthn 은 origin/rpId·조건부 클래스로딩 주의 |
| mTLS | 양방향 TLS, 워크로드 인증 | - | 서비스 간 | 사용자 부재, 메시 사이드카 자동, 앱코드 무관 |
| API Key / client_credentials | 헤더 키 / 머신 토큰 | stateless | 머신·파트너 | scope 인가, 키 로테이션 운영 비용 |

### 이 프레임워크에서의 매핑
- **세션 ↔ 무상태**는 `framework.security.session.mode = session | stateless` (서비스 단위 하나).
- **단일 인증 계약**: 프로젝트는 `Authenticator` 하나만 구현(DB/LDAP/SSO 무관) → 공통 로그인 흐름 자동 활성.
- **위임**: `framework-oauth-client`(OIDC RP), `framework-saml-sp`(SAML SP).
- **MFA**: `framework-mfa`(`MfaGate` SPI), `framework-webauthn`.
- **이중 발급기**: 내부 self-issued JWT + 외부 AS(OP) RS256/JWKS 를 `iss` 로 분기(`resource-server.enabled`).

---

## 2. 인가 (Authorization) — "무엇을 할 수 있나"

| 유형 | 단위 | 예 |
|------|------|----|
| URL(요청) 기반 | 경로 | `requestMatchers("/admin/**").hasRole("ADMIN")` |
| 메서드 보안 | 서비스 메서드 | `@PreAuthorize("hasRole('ADMIN')")` |
| Role/Authority(RBAC) | 역할/권한 | `ROLE_ADMIN`, `user:read` |
| Scope(OAuth2) | 토큰 스코프 | `hasAuthority("SCOPE_read")` |
| 표현식/ABAC | 동적 속성 | `@PreAuthorize("@orgPolicy.isMember(#deptId, authentication)")` |
| 도메인 객체 ACL | 인스턴스 | 문서별 읽기/편집 권한(무겁다, 꼭 필요한 도메인만) |

### 이 프레임워크에서의 매핑
- **동적 인가(RBAC)**: `framework.security.dynamic-authorization=true` → `DynamicAuthorizationManager` 가 DB(rbac)에서
  URL-역할 매핑 조회. `false` 면 "인증만 되면 통과"(데모/단순 서비스).
- 권한은 세션·무상태 양쪽에서 동일한 `ROLE_*` 형태 → `@PreAuthorize`/RBAC 동일 동작.

---

## 3. K8s/MSA 실전 조합

| 위치 | 인증 | 인가 |
|------|------|------|
| 게이트웨이/엣지 | 외부 IdP 토큰 OIDC/JWT 검증 | 코스 그레인(경로/스코프) |
| 내부 서비스(엣지 신뢰) | 게이트웨이 주입 헤더 신뢰(`X-User-Id`/`X-User-Roles`) | RBAC + 메서드 보안 |
| 내부 서비스(zero-trust) | Bearer 재검증(`iss` 분기) | RBAC + 메서드 보안 |
| 서비스 간 | mTLS(메시) | — |
| 위임/외부 | OIDC RP, SAML SP | scope + role |

### 신뢰 자세(`framework.security.edge-trust.mode`) — 환경변수 직교
- `gateway-headers`: **K8s + NetworkPolicy**(게이트웨이 우회 불가 보장)에서만 안전한 **의도적 옵트인**. Bearer 재검증 생략(저렴).
- `zero-trust`(기본): VM 등 네트워크 격리 약한 환경. Bearer 로컬 재검증.
- `local|dev|prod` 프로파일과 직교 → `EDGE_TRUST_MODE` env 로 주입.

### HTTP→HTTPS
- K8s: 엣지(Ingress)에서 TLS 종료 + `ssl-redirect`. 앱은 `X-Forwarded-Proto` 신뢰(`server.forward-headers-strategy`)만.
- VM/앱 종료: `requiresChannel().requiresSecure()` + HSTS. forwarded-header 신뢰와 결합해야 무한 리다이렉트 방지.

---

## 4. 핵심 원칙

- **인증은 엣지에서 한 번 무겁게, 인가는 각 서비스가 메서드 레벨까지 자기 책임으로.**
- 위임/외부 인증만 AS(OP)로 분리, 내부 인증은 self-issued JWT(이중 발급기 경계).
- 새 보안 체인은 `@AutoConfiguration(after = SecurityAutoConfiguration.class)` + `securityMatcher` 경로 한정으로 메인 체인과 공존(SAML SP 선례).
