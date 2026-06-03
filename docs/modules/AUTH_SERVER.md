# Authorization Server (`services/auth-server`)

우리가 **OP(OAuth2/OIDC Provider)** 가 되어 외부/그룹사에 표준 토큰을 발급하는 **별도 배포 서비스**. Spring Authorization Server(SS7 흡수) 기반.

> 라이브러리 모듈이 아니다. 독립 수명주기(서명키 회전 · 동의 · 클라이언트 등록 DB · 가용성 · CI/CD · k8s 매니페스트 · 서명키 시크릿)를 가진다.

---

## 1. 결정 기록 (2026-06-04 확정)

| # | 항목 | 결정 |
|---|------|------|
| ① | 정말 OP 가 필요한가 | **필요(범위 한정)**. 내부 1차 인증/세션은 기존 자체 JWT 유지, **외부/그룹사 위임 발급만** AS 경로. |
| ② | 서비스 경계 | 독립 포트(9000)/도메인. 키 회전·동의·인가코드·클라이언트 등록 **전부 JDBC**(다중 파드 공유). |
| ③ | 자체 JWT vs AS 토큰 | **이중 발급기**. 내부=자체 JWT, 외부=AS 토큰. 리소스 서버가 **두 issuer 모두 신뢰**(§4). |
| ④ | 클라이언트 저장소 | **JDBC** `RegisteredClientRepository`(시크릿 해시 · redirect-uri 화이트리스트). |

## 2. 버전·의존성

- SAS 는 **Spring Security 7.0 에 흡수**됨(1.5.x 가 마지막 독립 세대). 좌표 `org.springframework.security:spring-security-oauth2-authorization-server`, **버전=Boot/Security BOM 관리**(오버라이드 불가) → `libs.versions.toml` 미등록, build.gradle 에 버전 미기재.
- 그랜트: `authorization_code` + **PKCE**(SS7 기본 활성) · `client_credentials`. implicit/password 미채택.
- **Jackson 3**: SAS 가 기본으로 `tools.jackson.*` 사용 → `com.fasterxml` 누수 없음. `JdbcOAuth2AuthorizationService` 기본 매퍼가 이미 Jackson 3. 커스텀 principal 직렬화 시 `SecurityJacksonModules.getModules(loader)` + `JsonMapper.builder()` + `BasicPolymorphicTypeValidator.allowIfSubType(...)`. 구버전 `SecurityJackson2Modules`(ObjectMapper, com.fasterxml) **금지**.
- **SS7 패키지 재배치(확정, 7.0.5)**: SAS config 가 메인 config 모듈로 이동. `OAuth2AuthorizationServerConfiguration` → `org.springframework.security.config.annotation.web.configuration`, `OAuth2AuthorizationServerConfigurer` → `org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization`, `OAuth2TokenType` → `org.springframework.security.oauth2.server.authorization`(`.token` 아님). `applyDefaultSecurity`/static `authorizationServer()` 제거 → `new OAuth2AuthorizationServerConfigurer()` + `http.securityMatcher(getEndpointsMatcher()).with(...)` DSL.

## 3. 구조

```
services/auth-server
├─ config/AuthorizationServerConfig  # AS 체인 2개 + Jdbc 저장소 3종 + JWKSource + issuer + tokenCustomizer + PasswordEncoder
├─ config/AuthServerProperties       # issuer · jwkCacheTtl
├─ config/LocalDemo (@Profile local) # demo 인증기 + demo 클라이언트 2종(데모 전용)
├─ jose/JdbcRotatingJwkSource        # DB 공유 + 회전 오버랩 JWKS (서명=최신 ACTIVE, 검증=ACTIVE+RETIRED)
├─ jose/SigningKey(Mapper)           # 서명키 저장(MyBatis)
├─ user/FrameworkAuthenticationProvider  # 폼 로그인 → framework-security Authenticator
└─ user/RoleClaimTokenCustomizer     # 발급 토큰에 roles 클레임
db/migration: V1(SAS 스키마) · V2(서명키)
```

- **사용자 소스 재사용**: `framework.security.enabled=true` 로 `Authenticator`/`LoginService` 빈 재사용. framework-security 기본 체인은 `@ConditionalOnMissingBean(SecurityFilterChain)` 이라 우리 AS 체인 정의 시 자동 백오프(충돌 없음). RBAC 메뉴는 `framework.security.menu=false`.
- **서명키 회전**: 읽기 측 + 부트스트랩만 구현. 주기적 회전(새 ACTIVE 발급 + 오래된 키 RETIRE)은 **`framework-lock` 의 `@SchedulerLock`(리더 선출)**로 단일 파드만 수행하는 스케줄러로 확장(다중 파드 중복 회전 방지). [확장점]
- ⚠️ **서명키 개인키**: `auth_signing_key.jwk_json` 은 RSA 개인키 원문 포함 → 운영은 **반드시 암호화 저장**(컬럼 암호화/KMS/Vault). 골격은 평문(데모 한정). [TODO]

## 4. 리소스 서버 정합 (이중 발급기 — 결정 ③)

기존 RP/리소스 서버(게이트웨이·user-service 등)는 **자체 JWT** 를 검증한다. AS 발급 토큰도 받으려면:

- AS 토큰은 **RS256 + JWKS(`{issuer}/oauth2/jwks`)** 로 검증. `framework-oauth-client` 의 **`JwksKeyResolver`**(kid 캐시 + 회전 재조회 + 쿨다운, OIDC 강화 때 만든 패턴)를 그대로 재사용.
- **issuer 로 분기**: 토큰의 `iss` 가 AS issuer 면 JWKS 경로, 우리 내부 issuer 면 기존 자체 JWT 경로. 검증·로그아웃·블랙리스트 경로가 갈리므로 **경계를 코드/문서로 못 박을 것**(이중 발급기 혼란 = 핵심 함정).
- **로그아웃 경계**: 자체 JWT 의 jti 블랙리스트/`logoutAllByUserId` 는 내부 토큰에만 적용. AS 토큰 폐기는 `/oauth2/revoke`(AS) 경로. 혼용 금지.

> 본 드롭은 **프레임워크 라이브러리 무변경**. 위 정합은 리소스 서버 측 설정/어댑터로 수행(다음 작업 후보).

## 5. 엔드포인트 (SAS 기본)

`/oauth2/authorize` · `/oauth2/token` · `/oauth2/jwks` · `/.well-known/openid-configuration` · `/userinfo` · `/oauth2/revoke` · `/oauth2/introspect`

## 6. 로컬 실행

```bash
./gradlew :services:auth-server:bootRun         # H2 인메모리, demo 클라이언트 자동 등록
# discovery: http://localhost:9000/.well-known/openid-configuration
# demo 로그인: demo / demo
# 데모 클라이언트: demo-web(PKCE) · demo-service(client_credentials, secret=demo-secret)
```

## 7. 검증 메모

작성 환경은 Maven Central 차단 → SAS 본체 컴파일 불가(SAML 과 동일 제약). **받는 쪽 gradle 로 컴파일/기동 확인** 필요:
`./gradlew :services:auth-server:compileJava` → import 경로(SS7 재배치)·SAS API 시그니처 확정 → bootRun 으로 discovery/토큰 라운드트립.
