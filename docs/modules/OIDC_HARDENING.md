# OIDC 강화 (framework-oauth-client / OIDC RP)

`framework-oauth-client` 의 **선택형(per-provider) OIDC 강화**. 기존 OAuth2(userinfo) 흐름은 그대로 두고,
공급자별로 `oidc.enabled=true` 를 켜면 표준 OpenID Connect RP 로 동작한다 — **id_token 을 직접 검증**해 IdP 의
신원 주장을 신뢰할 수 있게 만든다.

> 기본값 off. kakao/naver 처럼 비(非)OIDC 공급자는 영향 없음.

---

## 1. 무엇이 강화되나

OIDC 를 켜면 callback 단계에서 다음을 수행한다.

1. **id_token 수신** — 토큰 교환 응답의 `id_token` 을 받는다(기존엔 `access_token` 만 사용).
2. **서명 검증**
   - `RS*/ES*/PS*` : `jwks_uri` 의 JWKS 에서 헤더 `kid` 로 공개키를 해석(키 회전 시 자동 재조회).
   - `HS*` : `client-secret` 을 HMAC 키로 검증.
3. **클레임 검증** — `iss` 일치, `aud ⊇ client-id`, `exp/nbf`(clock-skew 허용), `nonce` 일치, `sub` 존재.
4. **nonce 바인딩** — authorize 때 발급한 nonce 를 state 와 함께 저장하고, id_token 의 nonce 와 대조(재생/주입 차단).
5. **신원 구성** — 검증된 id_token 클레임으로 사용자 식별(`sub`/`email`/`name`). `userInfoUri` 가 있으면 빈 필드만
   userinfo 로 보충(id_token 우선).

추가로 **Discovery 자동적용**: `oidc.issuer`(또는 `oidc.discovery-uri`)만 주면 첫 요청 시
`/.well-known/openid-configuration` 을 조회해 authorization/token/userinfo/jwks 엔드포인트를 자동 채운다(지연·1회·캐시).

---

## 2. 설정

```yaml
framework:
  oauth-client:
    enabled: true
    base-redirect-uri: "https://app.example.com"
    state:
      store: { type: redis }          # 다중 파드면 redis(authorize/callback 가 다른 파드일 수 있음)
    providers:
      # 예: Keycloak/Azure AD/사내 SSO 등 표준 OIDC IdP
      corp:
        client-id: "${OIDC_CLIENT_ID}"
        client-secret: "${OIDC_CLIENT_SECRET}"
        oidc:
          enabled: true
          issuer: "https://sso.example.com/realms/corp"   # discovery 출처 + iss 기대값
          # jwks-uri / authorization-uri / token-uri 는 discovery 로 자동 보충(직접 지정도 가능)
          # discovery-uri: "https://sso.example.com/realms/corp/.well-known/openid-configuration"
          # clock-skew: PT60S
          # nonce: true
      google:                          # google 은 OIDC 프리셋(issuer 내장) 보유
        client-id: "${GOOGLE_CLIENT_ID}"
        client-secret: "${GOOGLE_CLIENT_SECRET}"
        oidc:
          enabled: true                # 켜면 google 도 id_token 검증 경로로
```

### OIDC 공급자 설정 규칙(`ProviderRegistry.require`)

- `client-id` 필수.
- 엔드포인트 출처는 둘 중 하나:
  - **discovery**: `oidc.issuer` 또는 `oidc.discovery-uri` 지정(엔드포인트는 자동 보충), 또는
  - **직접 지정**: `authorization-uri` + `token-uri` + `oidc.jwks-uri` 명시.
- `user-name-attribute` 는 OIDC 기본 `sub`(미지정 시 자동) — 신원 attribute 는 `sub/email/name` 기본.
- `userInfoUri` 는 **선택**(id_token 만으로 신원 구성 가능).

---

## 3. 동작 흐름

```
authorize:  require → (discovery 보충) → state+nonce 발급/저장 → 인가 URL(scope 에 openid 강제, &nonce=)
callback:   state 1회 소비(nonce 회수) → 토큰 교환(id_token 포함)
            → id_token 검증(JWKS/HS 서명 · iss · aud · exp/nbf · nonce · sub)
            → (userInfoUri 있으면 빈 필드 보충) → OAuthUserResolver → 자체 JWT 발급
```

---

## 4. 키 회전 / 캐시

- JWKS 는 `jwks_uri` 별로 `{kid→Key}` 스냅샷을 캐시(기본 TTL 1h).
- 들어온 `kid` 가 캐시에 없으면 **1회 강제 재조회**(IdP 가 키를 회전한 경우 무중단 대응) 후에도 없으면 거부.
- `kid` 가 없고 키가 1개뿐이면 그 키를 사용.

---

## 5. 보안 노트

- **서명 검증 실패/위변조/만료/iss·aud·nonce 불일치는 모두 401**(`BusinessException`, `SecureWebResponder` 대상 아님 —
  컨트롤러 경로이므로 `GlobalExceptionHandler` 가 처리).
- nonce 는 기본 on. 끄려면 `oidc.nonce=false`(권장하지 않음).
- HS 서명 id_token 은 `client-secret` 이 HMAC 키이므로 **충분히 긴 비밀(>=256bit)** 이어야 한다(HS256).
- discovery/JWKS 조회는 IdP 로의 아웃바운드가 필요 — K8s NetworkPolicy/egress 허용 대상에 포함할 것.

---

## 6. 점검 체크리스트

- [ ] 공급자에 `oidc.enabled=true` + (`oidc.issuer` 또는 endpoints+`jwks-uri`)
- [ ] scope 에 `openid` (자동 포함되나 IdP 콘솔 동의항목 확인)
- [ ] IdP egress(Discovery/JWKS/Token) 네트워크 허용
- [ ] 다중 파드면 `state.store.type=redis` (nonce 도 redis 에 함께 바인딩됨)
- [ ] HS 서명 IdP 면 `client-secret` 길이 확인(HS256 ≥ 32 bytes)


## 7. 사내 AS 와의 연계 검증 (✅ 완료, 2026-06-04)

이 모듈의 `IdTokenVerifier` 는 외부 IdP 뿐 아니라 **우리 Authorization Server(`services/auth-server`)가 발급한
id_token** 도 그대로 검증한다(JWKS RS256 · iss · aud⊇client-id · nonce · sub). **발급(AS)↔검증(RP) 양끝을 우리
코드로 닫는 e2e 완료(A안)**: `services/auth-server` 의 `e2e/OidcRpLinkageTest` 가 실 AS 발급 id_token 을 이 모듈의
`IdTokenVerifier`(+ 실 `JwksKeyResolver` → 라이브 `/oauth2/jwks`)로 검증한다 — 양성(클레임 왕복 sub=demo·iss·
roles(ROLE_USER)·nonce·auth_time + JWKS 캐시 히트) 2 + 음성(issuer/aud(clientId)/nonce 불일치) 3 = **5테스트**,
받는 쪽 통과. 연계는 `testImplementation project(':framework:framework-oauth-client')` 라이브러리 의존만으로 성립
(서비스 간 의존 0). 설계/경위: [`../NEXT_RP_IDTOKEN_LINK.md`](../NEXT_RP_IDTOKEN_LINK.md)(✅ 완료 배너).

> ⚠️ 예외 타입: 이 모듈의 `IdTokenVerifier`/`JwksKeyResolver` 는 검증 실패를 `BusinessException(ErrorCode.Common.UNAUTHORIZED)`
> 로 던진다 — AS 측 `ResourceServerJwtVerifier` 의 `io.jsonwebtoken.JwtException` 과 다르므로 음성 테스트 단언에서 혼동 금지.

> 주의(전체 흐름 연계 시 = B안, 백로그): RP 의 코드 교환은 `client_secret`(client_secret_post) 방식이라, AS 의 public+PKCE
> 클라이언트(`demo-web`)가 아니라 **confidential authorization_code 클라이언트**(`demo-rp`)가 필요하다. 검증기 수준
> 연계(A안, 위 완료분)는 id_token 이 클라이언트 인증방식과 무관하므로 이 제약과 무관하다.
