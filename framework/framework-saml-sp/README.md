# framework-saml-sp

SAML 2.0 SP — 외부 SAML IdP 신원확인 → 앱의 `SamlUserResolver` 매핑 → `framework-security` 로 **자체 JWT 발급**. 전용 SecurityFilterChain(`/saml2/**`)으로 security 무수정. 공공/대기업 SSO.

> ⚠️ **OpenSAML 전이** — 프레임워크 최초의 "외부 의존성 0" 예외. 루트 빌드에 Shibboleth 저장소(그룹 한정)가 필요하다.

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-saml-sp') }   // spring-security-saml2-service-provider 전이
```
```yaml
framework:
  saml-sp:
    enabled: true                       # 기본 false
    request-repository: SESSION          # SESSION(기본) | REDIS(멀티 파드)
    default-continue-url: /
    registrations:
      myidp:
        metadata-uri: https://idp.example.com/metadata
        email-attribute: email
        name-attribute: displayName
    redis:                               # request-repository=REDIS 시
      key-prefix: "saml:authnreq:"
      cookie-same-site: None
      cookie-secure: true
    slo: { enabled: false }              # IdP-initiated SLO 수신
```

## 쓰는 법
1. 앱이 `SamlUserResolver` 빈 구현(SAML assertion → 내부 사용자).
2. `/saml2/**` 전용 체인이 인증 처리 → `SamlAuthenticationSuccessHandler` → `SamlTokenIssuer` 로 자체 JWT 발급.
3. SLO 사용 시 `SamlLogoutUserResolver` 빈 등록 → 중앙 로그아웃 시 우리 JWT 무효화(SAML 본체는 SS `saml2Logout` 위임).

멀티 파드는 `request-repository: REDIS` + HTTPS(상관 쿠키 `SameSite=None;Secure`). 상세는 [`../../docs/modules/SAML_SP.md`](../../docs/modules/SAML_SP.md).

## 끄는 법
`framework.saml-sp.enabled: false` 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`SamlUserResolver`(필수)·`SamlLogoutUserResolver`(SLO 시) 빈으로 매핑 제어.

## 버전 관리
spring-security-saml2-service-provider 는 `implementation`(OpenSAML 전이). Shibboleth repo 설정은 루트 `build.gradle`/`settings.gradle` 참고. 변경 시 `STACK.md` 갱신.
