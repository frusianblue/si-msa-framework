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
- ⚠️ **SAS POM 버그 — commons-logging 제외(spring-security#18372)**: `spring-security-oauth2-authorization-server:7.0.x` POM 이 spring-core 에서 `commons-logging` 을 낡은(구 spring-jcl 시절) 제외로 빼버린다. SF7 은 spring-jcl 을 폐지하고 실제 Apache Commons Logging 을 쓰므로(SF#32459) `org.apache.commons.logging.LogFactory` 가 사라져 기동 시 `NoClassDefFoundError`(SpringApplication 정적 초기화). → build.gradle 에 `implementation 'commons-logging:commons-logging:1.3.5'` 명시 추가로 되돌림(SAS 가 제외 수정하면 제거 가능). SAS 미사용 서비스는 영향 없음.
- **로컬 H2 SQL 이식성**: Flyway 마이그레이션은 `TIMESTAMP`(+`CURRENT_TIMESTAMP`)·평문 INSERT 로 작성(SAS 정본도 `timestamp`). `TIMESTAMPTZ`·`ON CONFLICT` 는 PG 전용이라 로컬 H2(MODE=PostgreSQL)에서 50004 로 기동 실패.
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

- **사용자 소스 재사용**: `framework.security.enabled=true` 로 `Authenticator`/`LoginService` 빈 재사용. framework-security 기본 체인은 `@ConditionalOnMissingBean(SecurityFilterChain)` 이라 우리 AS 체인 정의 시 자동 백오프(충돌 없음). RBAC 메뉴는 `framework.security.menu=false`. 단 `SecurityMetadataService` 는 enabled=true 면 무조건 생성+생성자 eager 로딩이라, RBAC 테이블(resources/roles/role_resources)을 빈 채로 마련(Flyway V3)해 기동 WARN 을 없앤다(enabled=false 는 AuthAutoConfiguration→LoginService 의존 누락으로 기동 실패).
- **서명키 회전**: 읽기 측 + 부트스트랩만 구현. 주기적 회전(새 ACTIVE 발급 + 오래된 키 RETIRE)은 **`framework-lock` 의 `@SchedulerLock`(리더 선출)**로 단일 파드만 수행하는 스케줄러로 확장(다중 파드 중복 회전 방지). [확장점]
- ⚠️ **서명키 개인키**: `auth_signing_key.jwk_json` 은 RSA 개인키 원문 포함 → 운영은 **반드시 암호화 저장**(컬럼 암호화/KMS/Vault). 골격은 평문(데모 한정). [TODO]

## 4. 리소스 서버 정합 (이중 발급기 — 결정 ③)

기존 RP/리소스 서버(게이트웨이·user-service 등)는 **자체 JWT** 를 검증한다. AS 발급 토큰도 받으려면:

- AS 토큰은 **RS256 + JWKS(`{issuer}/oauth2/jwks`)** 로 검증. `framework-oauth-client` 의 **`JwksKeyResolver`**(kid 캐시 + 회전 재조회 + 쿨다운, OIDC 강화 때 만든 패턴)를 그대로 재사용.
- **issuer 로 분기**: 토큰의 `iss` 가 AS issuer 면 JWKS 경로, 우리 내부 issuer 면 기존 자체 JWT 경로. 검증·로그아웃·블랙리스트 경로가 갈리므로 **경계를 코드/문서로 못 박을 것**(이중 발급기 혼란 = 핵심 함정).
- **로그아웃 경계**: 자체 JWT 의 jti 블랙리스트/`logoutAllByUserId` 는 내부 토큰에만 적용. AS 토큰 폐기는 `/oauth2/revoke`(AS) 경로. 혼용 금지.

> 본 정합의 **정본 검증 지점 = 게이트웨이**(1차 canonical 검증자). 게이트웨이가 AS 토큰을 검증해
> `X-User-Id`/`X-User-Roles` 를 주입하면, 다운스트림(`framework-context`)은 **무변경으로 AS 발급자를 투명 수용**한다.
>
> **✅ 게이트웨이 이중 발급기 구현 완료(2026-06-04)** — `services/gateway` 의 `GatewayJwksTokenVerifier`(JWKS/RS256,
> `JwksKeyResolver` 패턴 자립 재현) + `GatewayTokenAuthenticator`(iss 분기). **프레임워크 라이브러리 무변경.**
> 켜는 법은 [`GATEWAY_EDGE_AUTH.md`](./GATEWAY_EDGE_AUTH.md) "이중 발급기" 절. 다운스트림 servlet 자체 재검증
> (zero-trust)은 선택적 심층방어이며 별도 후속(§8).

## 5. 엔드포인트 (SAS 기본)

`/oauth2/authorize` · `/oauth2/token` · `/oauth2/jwks` · `/.well-known/openid-configuration` · `/userinfo` · `/oauth2/revoke` · `/oauth2/introspect`

## 6. 로컬 실행

> 새 서비스 설정 주의: 매퍼 XML 을 `mapper/` 폴더에 두므로 `application.yml` 에 `mybatis.mapper-locations: classpath*:mapper/**/*.xml` 필수(user-service 규약). 미설정 시 `Invalid bound statement (not found)`.

```bash
./gradlew :services:auth-server:bootRun         # H2 인메모리, demo 클라이언트 자동 등록
# discovery: http://localhost:9000/.well-known/openid-configuration
# demo 로그인: demo / demo
# 데모 클라이언트: demo-web(PKCE) · demo-service(client_credentials, secret=demo-secret)
```

## 7. 검증 상태

**✅ 받는 쪽 실기동 검증 완료(2026-06-04)**: `./gradlew :services:auth-server:compileJava` → `bootRun` → `http://localhost:9000/.well-known/openid-configuration` 200(issuer·authorize·token·jwks·userinfo·revoke·introspect·PKCE S256·id_token RS256) · 서명키 부트스트랩(`auth_signing_key` 1건) · demo 클라이언트(demo-web/demo-service) 등록 확인.

컴파일 통과 후 기동 경로에서 순차로 해소한 6관문(전부 §2/HANDOFF §6 SAS 묶음 등록):
1. SS7 패키지 재배치(config 클래스 메인 모듈 이동, `applyDefaultSecurity` 제거→DSL).
2. `JdbcRotatingJwkSource.get()` 의 `current().jwkSet()` 이중 호출(자체 버그) → `current()` 가 이미 JWKSet.
3. SAS POM 의 commons-logging 제외(#18372) → `commons-logging:1.3.5` 재추가.
4. 로컬 H2 SQL 이식성(`TIMESTAMPTZ`/`ON CONFLICT` → `TIMESTAMP`/평문 INSERT).
5. `mybatis.mapper-locations` 누락 → yml 추가.
6. framework-security RBAC `SecurityMetadataService` eager 로딩 → 빈 RBAC 테이블(V3).

> 작성 환경은 Maven Central 차단으로 SAS 본체 컴파일/기동 불가 → 받는 쪽 `bootRun` 로그가 최종 검증(상기 완료).

## 8. 다음 (후속)

- ~~**리소스 서버 이중 issuer 정합(게이트웨이)**~~ **✅ 완료(2026-06-04)**: `services/gateway` 가 토큰 `iss` 로 분기 —
  AS issuer 면 `{issuer}/oauth2/jwks` 로 RS256 검증(`GatewayJwksTokenVerifier`), 내부면 자체 JWT(HMAC). 프레임워크 무변경.
  상세 [`GATEWAY_EDGE_AUTH.md`](./GATEWAY_EDGE_AUTH.md) "이중 발급기".
- ~~**(선택) 다운스트림 servlet zero-trust 재검증**~~ **✅ 완료(2026-06-04)**: `framework-security` 가 `edge-trust.mode=zero-trust` + `resource-server.enabled=true` 에서 `Authorization` Bearer 를 자체 JWT(HMAC) + AS 토큰(RS256/JWKS)으로 **직접 재검증**. 신규 `ResourceServerJwtVerifier`/`DownstreamTokenAuthenticator`/`TokenIssuerKind`, `JwtAuthenticationFilter` 가 모드 분기. 배치 환경별(K8s=gateway-headers / VM=zero-trust) 분기는 env(`EDGE_TRUST_MODE`). 상세 [`../TOKEN_VERIFICATION_GUIDE.md`](../TOKEN_VERIFICATION_GUIDE.md) §6.4/§7.3.
- **서명키 회전 스케줄러**: `framework-lock @SchedulerLock`(리더 선출)로 단일 파드만 새 ACTIVE 발급 + 오래된 키 RETIRE. 개인키 저장 암호화(KMS/Vault).
- **토큰 발급 라운드트립 통합테스트**: demo-web authorization_code+PKCE, demo-service client_credentials.
