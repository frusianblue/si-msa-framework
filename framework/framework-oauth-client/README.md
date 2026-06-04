# framework-oauth-client

소셜 로그인(OAuth2) + OIDC RP 강화. 외부 IdP 인가코드 흐름 → userinfo → 앱의 `OAuthUserResolver` 매핑 → `framework-security` 로 **자체 JWT 발급**. google/kakao/naver 프리셋. 외부 의존성 0(RestClient+Jackson3, jjwt 재사용).

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-oauth-client') }   // framework-security 전제
```
```yaml
framework:
  oauth-client:
    enabled: true                       # 기본 false
    base-redirect-uri: https://app.corp.com
    state: { store: { type: redis } }   # memory | redis (멀티 인스턴스는 redis)
    providers:
      google:
        client-id: ${GOOGLE_ID}
        client-secret: ${GOOGLE_SECRET}
        oidc: { enabled: true }         # id_token 검증(JWKS·iss·aud·exp·nonce) + discovery 자동
```

## 쓰는 법
1. 앱이 `OAuthUserResolver` 빈 구현(외부 사용자 → 내부 사용자 매핑).
2. 인가 시작/콜백은 `OAuthController` 가 처리 → `OAuthLoginService` 가 토큰 교환·userinfo·매핑 후 `DirectOAuthTokenIssuer` 로 자체 JWT 발급.
3. OIDC 사용 시 `IdTokenVerifier`+`JwksKeyResolver` 가 id_token 을 검증(discovery 는 `OidcDiscoveryClient`).

상세 흐름·프로바이더 설정은 [`../../docs/modules/OAUTH_CLIENT.md`](../../docs/modules/OAUTH_CLIENT.md), OIDC 강화는 [`../../docs/modules/OIDC_HARDENING.md`](../../docs/modules/OIDC_HARDENING.md), 통합 예시는 [`INTEGRATION.md`](INTEGRATION.md).

## 끄는 법
`framework.oauth-client.enabled: false` 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`OAuthUserResolver`(필수 구현)·`OAuthTokenIssuer`·`OAuthStateStore` 빈으로 매핑/발급/state 저장을 커스터마이즈.

## 버전 관리
**신규 외부 의존성 0**. web/data-redis 는 `compileOnly`(테스트 재선언 필요).
