# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**SSO §6.3 C) Authorization Server 골격 착수.** 우리가 OP(OAuth2/OIDC Provider)가 되어 외부/그룹사에 토큰을 발급하는 **별도 배포 서비스 `services/auth-server`** 신설. 결정 4건 확정 + 최소 골격 + 리소스서버 정합 가이드. **버전 정합 핵심 발견: Spring Authorization Server 는 SS7 에 흡수돼 버전=Boot/Security BOM 관리(핀 불필요), Jackson 3 기본(`tools.jackson.*`)이라 우리 규칙과 충돌 0.**

핵심 설계: ① OP **범위 한정** — 내부 1차 인증/세션은 기존 자체 JWT 유지, **외부/그룹사 위임 발급만** AS. ② 경계 = 독립 포트(9000)/도메인, 키·동의·인가코드·클라이언트 등록 **전부 JDBC**(다중 파드 공유). ③ **이중 발급기** — 리소스 서버가 `iss` 로 분기(AS 토큰=RS256/JWKS 검증, `framework-oauth-client.JwksKeyResolver` 재사용). ④ JDBC `RegisteredClientRepository`. framework-security 는 **사용자 소스(`Authenticator`/RBAC)만 재사용** — 기본 체인이 `@ConditionalOnMissingBean(SecurityFilterChain)` 라 우리 AS 체인 2개 정의 시 자동 백오프(충돌 0), `menu=false`.

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: SSO §6.3 Authorization Server 골격 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done) — 신규 서비스 `services/auth-server`
- **`config/AuthorizationServerConfig`**: AS 체인 2개(`@Order(1)` 프로토콜 엔드포인트 + OIDC + resourceServer.jwt / `@Order(2)` 폼로그인) · `JdbcRegisteredClientRepository` · `JdbcOAuth2AuthorizationService` · `JdbcOAuth2AuthorizationConsentService` · `JWKSource=JdbcRotatingJwkSource` · `JwtDecoder` · `AuthorizationServerSettings(issuer)` · `OAuth2TokenCustomizer`(roles 클레임) · `PasswordEncoder`(@ConditionalOnMissingBean).
- **`config/AuthServerProperties`**: `issuer`(blank 면 기동 차단) · `jwkCacheTtl`(기본 5m).
- **`jose/JdbcRotatingJwkSource`**: DB 공유 + 회전 오버랩 JWKS. 서명=최신 ACTIVE, 검증=ACTIVE+RETIRED 전체 노출. TTL 캐시 + 부트스트랩(키 없으면 RSA2048 1개 생성). 회전 스케줄러는 `framework-lock` 리더선출로 확장(미구현 확장점).
- **`jose/SigningKey`·`SigningKeyMapper`(MyBatis)·`SigningKeyMapper.xml`**: 서명키 저장(ON CONFLICT DO NOTHING).
- **`user/FrameworkAuthenticationProvider`**: 폼로그인 → `Authenticator.authenticate`. principal=표준 `User`(Jackson3 PolymorphicTypeValidator 안전), roles→`ROLE_*`.
- **`user/RoleClaimTokenCustomizer`**: access/id 토큰에 `roles` 클레임.
- **`config/LocalDemo`(@Profile local)**: demo 인증기(demo/demo) + demo 클라이언트 2종(demo-web PKCE, demo-service client_credentials) 코드 등록.
- **resources**: `application.yml`(3프로파일·issuer·framework.security menu=false) · `application-local.yml`(H2 MODE=PostgreSQL) · `application-local-postgres.yml` · Flyway `V1__authorization_server_schema.sql`(SAS 정본 스키마 PG 적응본) · `V2__auth_signing_key.sql`.
- **`build.gradle`**: SAS 좌표(버전 미기재=BOM) + web/jdbc starter + framework-core/security/mybatis + flyway/postgres/h2 (user-service 패턴).
- **`settings.gradle`**: `include 'services:auth-server'`.
- **문서**: `docs/modules/AUTH_SERVER.md`(서비스 가이드 + 리소스서버 이중 issuer 정합) · `docs/NEXT_SSO.md` §6.3 결정/진행 기록.

## 검증 (이 환경)
- 작성 환경 Maven Central 차단 → **SAS 본체 컴파일 불가**(SAML 과 동일 제약). 순수 JDK 단독 테스트 대상 없음(전부 SAS/Spring/Nimbus 바인딩). **받는 쪽 gradle 컴파일 필수**.

## 새로 밟은/확정한 함정 (HANDOFF §6 등록 대상)
1. **SAS = SS7 흡수** — 좌표 `org.springframework.security:spring-security-oauth2-authorization-server`, **버전 오버라이드 불가**(Boot/Security BOM). `libs.versions.toml` 미등록·build.gradle 버전 미기재. (SAML OpenSAML 처럼 "핀 예외"가 아니라 BOM 으로 해결.)
2. **Jackson 3 정합** — SAS 가 기본 Jackson 3. `JdbcOAuth2AuthorizationService` 매퍼도 Jackson 3 → `com.fasterxml` 누수 0. 커스텀 직렬화 시 `SecurityJacksonModules.getModules(loader)`+`JsonMapper.builder()`+`BasicPolymorphicTypeValidator.allowIfSubType(...)`. **`SecurityJackson2Modules`(ObjectMapper)·`com.fasterxml` 금지**.
3. **PolymorphicTypeValidator** — SS7 은 SS 타입만 기본 허용 → 골격은 principal 을 표준 `User` 로 두어 회피.
4. **SS7 패키지 재배치(확정, 7.0.5 소스로 검증·수정 완료)** — `OAuth2AuthorizationServerConfiguration`→`org.springframework.security.config.annotation.web.configuration`, `OAuth2AuthorizationServerConfigurer`→`...config.annotation.web.configurers.oauth2.server.authorization`, `OAuth2TokenType`→`...oauth2.server.authorization`(`.token`아님). `applyDefaultSecurity`/static `authorizationServer()` **제거** → `new OAuth2AuthorizationServerConfigurer()`+`http.securityMatcher(getEndpointsMatcher()).with(...)` DSL.
5. **이중 발급기 혼란** — 검증/로그아웃/블랙리스트 경로가 자체JWT vs AS 로 갈림 → `iss` 분기, 경계 문서화(`AUTH_SERVER.md` §4).
6. **서명키 개인키 평문저장 금지** — `auth_signing_key.jwk_json` 운영 암호화(KMS/Vault). 회전은 `framework-lock` 리더선출.
7. **SAS POM 의 commons-logging 제외(spring-security#18372)** — SAS 7.0.x POM 이 spring-core 에서 `commons-logging` 을 (구 spring-jcl 시절) 낡은 제외. SF7 은 실제 Apache Commons Logging 사용(SF#32459) → `LogFactory` 누락 → 기동 시 `NoClassDefFoundError`(SpringApplication.<clinit>). **컴파일은 통과, 런타임만 실패**. SAS 쓰는 서비스만 해당(user-service 등 무영향). 해결: build.gradle 에 `implementation 'commons-logging:commons-logging:1.3.5'` 재추가.
8. **로컬 H2 SQL 이식성** — `TIMESTAMPTZ`(PG 약어)·`ON CONFLICT`(PG 전용)는 H2(MODE=PostgreSQL)가 파싱/인식 못 함(에러 50004). Flyway V1 기동 실패. → `TIMESTAMP`(+`CURRENT_TIMESTAMP`, SAS 정본도 timestamp 사용)·평문 INSERT 로 통일(H2·PG 양립).
9. **MyBatis mapper-locations** — 매퍼 XML 을 `mapper/` 폴더(인터페이스와 다른 위치)에 두는 프로젝트 규약상, 새 서비스는 `mybatis.mapper-locations: classpath*:mapper/**/*.xml` 를 yml 에 줘야 함(미설정 시 `Invalid bound statement (not found)`). user-service 와 동일.
10. **framework-security RBAC eager 로딩** — `framework.security.enabled=true` 면 `SecurityMetadataService` 빈이 무조건 생성되고 생성자에서 `findAllResources()` 를 즉시 호출(eager). RBAC 테이블(resources/roles/role_resources) 없으면 기동 시 WARN(비치명적). `menu=false` 로도 안 꺼짐(menu 토글과 별개). enabled=false 는 LocalDemo Authenticator→AuthAutoConfiguration→LoginService 의존 누락으로 기동 깨짐 → **대신 빈 RBAC 테이블(V3) 마련**으로 해결(user-service DDL 동일).

## 실행/검증 (받는 쪽)
```bash
./gradlew :services:auth-server:compileJava   # ★ SS7 import/시그니처 확정(최우선)
./gradlew :services:auth-server:bootRun        # H2, demo 자동등록
# http://localhost:9000/.well-known/openid-configuration  · 로그인 demo/demo
```

## 다음 (Next) 후보
- **리소스 서버 이중 issuer 정합**(프레임워크 무변경, 게이트웨이/리소스서버 측 어댑터·설정 — `iss` 분기 + `JwksKeyResolver` 로 AS jwks_uri 검증).
- **서명키 회전 스케줄러**(`framework-lock @SchedulerLock` 리더선출 + 개인키 암호화 저장).
- (보류) **6.2-B** SP-initiated SLO · **6.4** Passwordless(WebAuthn).

## 받는 쪽 적용 (이번 zip)
```bash
unzip -o si-msa-auth-server.zip   # services/auth-server/** + settings.gradle + docs/** 덮어쓰기(신규 위주)
# 신규 파일이라 삭제 대상 없음. settings.gradle/NEXT_SSO.md/HANDOFF_SUMMARY.md 는 덮어쓰기.
```
> 코드+문서 드롭. SAS 본체는 작성 환경 컴파일 불가 → **받는 쪽 compileJava 로 SS7 import/API 확정**이 첫 작업.
<!-- 갱신 끝 -->
