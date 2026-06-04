# NEXT_OIDC_ID_TOKEN.md — 다음 세션 착수: OIDC id_token 발급

> 상태: **착수 전(설계/조사 노트)**. 토큰 발급 라운드트립 e2e(2026-06-04, ✅ 4/4)에서 **의도적으로 미룬** 조각.
> 라운드트립 e2e 는 `authorization_code+PKCE` leg 에서 **`openid` scope 를 빼고 access_token 만** 검증했다(아래 §1 이유).
> 이번 세션 목표 = **id_token 까지 정상 발급되게 만들고 e2e 로 검증**.

---

## 1. 왜 미뤘나 (이번 세션에서 확정한 근본 원인)

라운드트립 e2e 에서 `scope=openid profile` 로 코드 교환을 하면 **코드 교환 자체가 실패**했다:

```
java.lang.IllegalArgumentException: authenticationTime cannot be null
  at org.springframework.security.oauth2.server.authorization.token.JwtGenerator.getAuthenticationTime(JwtGenerator.java:237)
  at JwtGenerator.generate(...)                 ← id_token 생성 중
  at OAuth2AuthorizationCodeAuthenticationProvider.authenticate(...:274)
```

확정 사실(SS7 7.0.0 `JwtGenerator` 정본 대조):
- id_token 의 **`auth_time`(+`sid`)** 클레임은 토큰 컨텍스트의 **`SessionInformation`** 에서 온다
  (`JwtGenerator` 141–144: `context.get(SessionInformation.class)` → `claim("sid", ...)` · `claim(AUTH_TIME, sessionInformation.getLastRequest())`).
- 받는 쪽 SS7 패치본은 `SessionInformation`/auth_time 이 **null 이면 Assert 로 막는다**(7.0.0 은 null 가드라 그냥 생략 — 버전차).
- e2e 의 MockMvc 폼 로그인은 **실 서블릿 세션 생성 이벤트를 일으키지 않아** `SessionRegistry` 에 세션이 등록되지 않고 → `SessionInformation` 이 null → 위 Assert.
- `openid` 가 있으면 id_token 생성이 코드 교환의 일부라, 실패 시 **access_token 도 함께 못 받는다.**

→ 결론: **MockMvc 아티팩트일 가능성이 높다**(실 브라우저 + 세션 레지스트리에선 채워짐). 다만 **그게 실제로 채워지는지는 본 세션에서 미확인** → 다음 세션의 첫 일.

---

## 2. 먼저 확인할 것 (조사 — 코드 짜기 전)

1. **현재 auth-server 가 OIDC 세션 관리를 와이어링하는가?**
   - `services/auth-server` 에 `SessionRegistry`(또는 SAS `OidcSessionRegistry`) 빈이 있는가?
   - 로그인 체인(`AuthorizationServerConfig` 의 order(2) `defaultSecurityFilterChain`)이 세션을 레지스트리에 등록하는가? (현재는 `formLogin(withDefaults())` 만 — **세션 레지스트리 등록 없음일 가능성 큼 = 갭**.)
   - 프레임워크의 동시로그인 제어(`framework-security` `ConcurrentSessionService`)는 **자체 JWT 기반**이라 Spring 의 `HttpSession` `SessionRegistry` 와 별개일 수 있다 → SAS 가 읽는 레지스트리에 세션이 들어가는지 별도 확인.
2. **실 환경(브라우저) `openid` 로그인 시 auth_time 이 실제로 채워지는가?**
   - 채워진다면 = MockMvc 한정 문제(테스트 전략만 바꾸면 됨).
   - 안 채워진다면 = **앱 갭**(아래 §3-A 와이어링이 진짜 필요 — 실 OIDC RP 로그인도 깨진다).

---

## 3. 구현 계획

### A. (필요 시) OIDC 세션 관리 와이어링 — auth_time/sid 채우기
- `SessionRegistry` 빈 추가 + `HttpSessionEventPublisher`(세션 라이프사이클 이벤트) 등록.
- 로그인 체인에 세션 관리 연동(예: `sessionManagement` + 인증 성공 시 레지스트리 등록 전략), 그리고 SAS `oidc()` 가 그 레지스트리에서 `SessionInformation` 을 가져오도록 연결.
- 참고: SAS OIDC 로그아웃/세션 관리 문서(`OidcSessionRegistry`/`InMemoryOidcSessionRegistry`). 다중 파드면 세션 레지스트리도 공유 백엔드 고려(후속).
- ⚠️ framework-security 무변경 원칙 유지 — auth-server 안에서만 와이어링.

### B. id_token 발급 e2e 테스트
`services/auth-server/src/test/.../e2e/` 에 별도 테스트(`OidcIdTokenIssuanceTest` 등):
- **전략 택1**:
  - (b1) **실 브라우저형 흐름**: `WebTestClient`(RANDOM_PORT, 실 쿠키/세션)로 login→authorize(`scope=openid profile`)→code→token. 실 세션이라 `SessionInformation` 채워짐.
  - (b2) **SessionRegistry 시드**: MockMvc 유지 + 테스트에서 레지스트리에 세션 등록(덜 충실).
  - → A 를 와이어링하면 (b1) 이 자연스러움. A 없이 MockMvc 면 (b2).
- 검증할 id_token 클레임: `iss`(= AS issuer) · `sub`(=demo) · `aud`(= client_id `demo-web`) · `auth_time`(null 아님) · `iat`/`exp` · (요청 시)`nonce` · (세션 관리 시)`sid` · `roles`(RoleClaimTokenCustomizer).
- id_token 서명/검증: AS JWKS(RS256)로 검증(라운드트립 e2e 의 `ResourceServerJwtVerifier` 재사용 가능 — 단 **aud 검증을 켜서** `expectedAudience=demo-web` 확인 권장).

### C. (선택) RP 측 id_token 검증 연계
- `framework-oauth-client` 의 `IdTokenVerifier`(OIDC 강화 때 구현)와 정합 확인 — AS 가 발급한 id_token 을 RP 가 그대로 검증하는 경로까지 e2e 로 닫으면 OIDC 전 구간.

---

## 4. 수용 기준 (Acceptance)
- [ ] `scope=openid profile` authorization_code 교환이 200 + `id_token` 포함.
- [ ] id_token 의 `auth_time` 이 null 이 아니고, `iss`/`sub`/`aud(=demo-web)`/`exp` 정상.
- [ ] (요청 시) `nonce` 왕복, (세션 관리 시) `sid` 존재.
- [ ] id_token 이 AS JWKS(RS256)로 검증되고 `aud` 검증 통과.
- [ ] 실 환경(또는 충실한 테스트)에서 OIDC 로그인 깨지지 않음 확인.

---

## 5. 관련 코드/문서
- 발급: `services/auth-server` — `AuthorizationServerConfig`(체인/oidc), `RoleClaimTokenCustomizer`(roles, id_token 에도 부여), `JdbcRotatingJwkSource`(RS256 키).
- 라운드트립 e2e: `services/auth-server/src/test/.../e2e/TokenIssuanceRoundTripTest`(access_token leg — 본 작업의 출발점).
- 검증기: `framework-security` `ResourceServerJwtVerifier`(aud 검증 옵션 보유) · `framework-oauth-client` `IdTokenVerifier`(RP 측).
- 경계/배경: `docs/modules/AUTH_SERVER.md` §4 · `docs/TOKEN_VERIFICATION_GUIDE.md` · `docs/modules/OIDC_HARDENING.md`.

---

## 6. 함정 메모(이번 세션 확정)
- **id_token auth_time ← SessionInformation**(SS7 `JwtGenerator`). 세션 레지스트리에 세션이 없으면 null → (패치본) Assert 실패. MockMvc 폼 로그인은 세션 이벤트 미발생 → null.
- **`openid` 있으면 id_token 실패가 access_token 발급까지 막는다** — access-token 만 보려면 `openid` 제외(라운드트립 e2e 가 그렇게 함).
- 테스트 전략: id_token 은 **실 세션이 필요** → MockMvc 보다 WebTestClient 실 흐름 또는 레지스트리 시드.
