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
├─ config/LocalDemo (@Profile local) # demo 인증기 + demo 클라이언트 3종(demo-web/demo-service/demo-rp, 데모 전용)
├─ jose/JdbcRotatingJwkSource        # DB 공유 + 회전 오버랩 JWKS (서명=최신 ACTIVE, 검증=ACTIVE+RETIRED) · cipher.reveal 로 개인키 복호
├─ jose/SigningKey(Mapper)           # 서명키 저장(MyBatis) · retireAllActive/deleteRetiredOlderThan 추가
├─ jose/SigningKeyCipher / AesSigningKeyCipher  # 개인키 컬럼 보호 추상 + AES-GCM 구현(마커 enc:) — framework-core 재사용
├─ jose/SigningKeyGenerator / RsaSigningKeyGenerator  # 새 ACTIVE 생성(generateRsaKey + cipher.protect)
├─ jose/SigningKeyRotationService    # 회전 오케스트레이션(@Transactional · RETIRE→INSERT→grace정리 · 멱등가드)
├─ jose/SigningKeyRotationScheduler  # @Scheduled + @SchedulerLock(리더선출) thin wrapper → Service 위임
├─ config/SigningKeyProperties       # auth-server.signing-key.{rotation,encryption}.*
├─ user/FrameworkAuthenticationProvider  # 폼 로그인 → framework-security Authenticator
└─ user/RoleClaimTokenCustomizer     # 발급 토큰에 roles 클레임
db/migration: V1(SAS 스키마) · V2(서명키) · V3(빈 RBAC) · V4(framework_lock) · V5(auth_signing_key.retired_at) · **V6(SS7 정합 — `oauth2_authorization` device_code/user_code 8컬럼; SAS 가 그랜트 무관 고정 컬럼 목록을 INSERT 하므로 디바이스 그랜트 미채택이어도 필수. 토큰 발급 라운드트립 e2e 가 잡은 버그.)**
```

- **사용자 소스 재사용**: `framework.security.enabled=true` 로 `Authenticator`/`LoginService` 빈 재사용. framework-security 기본 체인은 `@ConditionalOnMissingBean(SecurityFilterChain)` 이라 우리 AS 체인 정의 시 자동 백오프(충돌 없음). RBAC 메뉴는 `framework.security.menu=false`. 단 `SecurityMetadataService` 는 enabled=true 면 무조건 생성+생성자 eager 로딩이라, RBAC 테이블(resources/roles/role_resources)을 빈 채로 마련(Flyway V3)해 기동 WARN 을 없앤다(enabled=false 는 AuthAutoConfiguration→LoginService 의존 누락으로 기동 실패).
- **actuator 미인증 허용(K8s 프로브/헬스체크 전제)**: AS 체인을 자체 정의하면 framework-security 기본 체인(이 `/actuator/**` 를 permitAll 하던)이 백오프되므로, `@Order(2) defaultSecurityFilterChain` 에서 `requestMatchers("/actuator/**").permitAll()` 을 **직접** 넣어야 한다. 빠뜨리면 `/actuator/health` 가 `anyRequest().authenticated()`+formLogin 에 걸려 302(→`/login`)/401 → K8s startup/liveness/readiness 프로브·로컬 compose healthcheck·Prometheus 스크레이프가 전부 실패한다(앱은 정상 기동인데 컨테이너가 unhealthy 로 죽음). 노출 엔드포인트 범위는 `application.yml` `management.endpoints.web.exposure.include`(health/info/prometheus/metrics)로 이미 한정. [PITFALLS §9]
- **로그인 사용자 인증기 — 프로파일별 분리**: 폼 로그인 ID/PW 는 `FrameworkAuthenticationProvider` 가 `Authenticator` SPI 로 위임한다. 그 빈을 프로파일이 가른다.
  - `local`: `LocalDemo` 의 **demo/demo** 데모 인증기(`@Profile("local")`, 운영 미사용).
  - `!local`(dev/prod, **K8s 포함**): `user/ProdAuthenticatorConfig` 가 `DbAuthenticator` 등록 — authdb `app_user` 테이블 비번 검증(user-service `DbAuthenticationProvider` 와 동일 패턴). `@ConditionalOnMissingBean(Authenticator.class)` 라 **실 프로젝트가 자체 Authenticator(LDAP/AD/GPKI 등)를 넣으면 그쪽이 우선**(레퍼런스 기본값). 비번은 `{bcrypt}$2a$…` 포맷 필수(`BcryptEnforcingPasswordEncoder` 가 `{noop}`/평문 거부).
  - 사용자 저장소: `app_user`(Flyway **V7**, authdb 자체 테이블 — 서비스별 DB 규약), MyBatis `AppUserMapper`(`@MapperScan` 에 `com.company.authserver.user` 추가, `login_id AS loginId` alias=map-underscore 미설정 규약). seed 데모 계정 **`tester` / `Test1234!` / `USER`**(운영은 제거하고 실 사용자 적재). 빈 `@Configuration`+`@Bean` 으로 등록(컴포넌트 스캔 빈의 `@ConditionalOnMissingBean` 순서 불안정 회피, [PITFALLS §9]).
- **서명키 회전**: ✅ **구현 완료(2026-06-04)**. `SigningKeyRotationScheduler`(`@Scheduled` cron + `@SchedulerLock` 리더 선출)가 단일 파드만 회전을 수행 → `SigningKeyRotationService.rotateOnce()`(한 트랜잭션: 직전 ACTIVE 전부 RETIRE → 새 ACTIVE INSERT → grace 지난 RETIRED 정리). 기본 **off**(`auth-server.signing-key.rotation.enabled=false`), `SIGNING_KEY_ROTATION_ENABLED=true` 로 활성. 멱등 가드(`min-interval` 내 ACTIVE 면 스킵)는 락 실패/수동 중복 트리거 2차 안전망.
- **서명키 개인키**: ✅ **암호화 저장 구현(2026-06-04)**. `SigningKeyCipher`/`AesSigningKeyCipher`(framework-core `AesCryptoService` AES-GCM 재사용) 가 `jwk_json` 을 `enc:` 마커 + Base64(IV||ct+tag) 로 보호. 쓰기(부트스트랩/회전)·읽기(JWKS 로드) 양쪽 경유. 기본 **on**(`encryption.enabled=true`). 읽기는 토글 무관 항상 마커 인지 → 평문(레거시/데모)↔암호문 혼재·롤백 안전. 운영 마스터키=`AES_SECRET`(framework-core `AesMasterKeySafetyGuard` 가 prod 약한키 부팅 차단). KMS/Vault 는 `SigningKeyCipher` 교체로 후속.

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
>
> **✅ 토큰 발급 라운드트립 e2e 통과(2026-06-04)** — `services/auth-server` `e2e/TokenIssuanceRoundTripTest`: 실 기동 AS 가
> 두 그랜트(client_credentials·authorization_code+PKCE)로 발급한 RS256 access token 을 실 `/oauth2/jwks` + 논리 issuer 로
> 다운스트림 zero-trust 검증기(`ResourceServerJwtVerifier`/`DownstreamTokenAuthenticator`)가 재검증(음성 2종 포함). 4/4 통과.
> **단 그 leg2 는 `openid` 제외(access_token 만)** — 당시 id_token 코드 교환이 `"authenticationTime cannot be null"`
> 로 실패했기 때문. (당초 `SessionInformation` 의존으로 진단했으나 **오진**이었다 — 아래 ✅ 참고.)
>
> **✅ OIDC id_token 발급 완료(2026-06-04)** — `e2e/OidcIdTokenIssuanceTest`: `authorization_code+PKCE` 에 `openid`
> 포함 → id_token 발급 + 실 JWKS(RS256)/클레임 검증(iss·sub·aud=demo-web·auth_time·exp·nonce·sid·roles, aud 불일치 음성).
> **진짜 원인 = 커스텀 `FrameworkAuthenticationProvider` 가 인증 팩터를 안 붙인 것**(SS7 7.0 `JwtGenerator` 는 auth_time
> 을 `SessionInformation` 이 아니라 principal 의 `FactorGrantedAuthority#issuedAt` 에서 산출). 수정 = provider 가
> `FactorGrantedAuthority.fromAuthority(PASSWORD_AUTHORITY)` 부착 + `RoleClaimTokenCustomizer` 가 roles 클레임에서 팩터 제외
> (framework-security 무변경). 상세·정정 경위 = [`../NEXT_OIDC_ID_TOKEN.md`](../_internal/planning/NEXT_OIDC_ID_TOKEN.md) · HANDOFF §6.
>
> **✅ RP 연계(OIDC 풀루프 마감) 완료(2026-06-04, A안)** — 발급한 id_token 을 **우리 RP 검증기**(`framework-oauth-client`
> `IdTokenVerifier` + 실 `JwksKeyResolver` → 라이브 `/oauth2/jwks`)가 그대로 검증해 발급(AS)↔검증(RP) 양끝이 모두
> 우리 코드임을 입증. `e2e/OidcRpLinkageTest`(양성 2 + 음성 3): `testImplementation project(':framework:framework-oauth-client')`
> 만 추가(서비스 간 의존 0). ⚠️ RP 검증기는 실패를 `BusinessException(UNAUTHORIZED)` 로 던진다(AS 측 `ResourceServerJwtVerifier`
> 의 `JwtException` 과 예외 타입 다름 — 음성 단언 주의). 상세 = [`../NEXT_RP_IDTOKEN_LINK.md`](../_internal/planning/NEXT_RP_IDTOKEN_LINK.md) ·
> [`OIDC_HARDENING.md`](OIDC_HARDENING.md) §7 · HANDOFF §6.
>
> **✅ RP 전체 콜백 흐름(B안) 완료(2026-06-06)** — confidential `demo-rp`(`client_secret_post`, PKCE 불요)를 `LocalDemo`·
> `SmokeClientSeeder` 양쪽에 등록하고, 우리 RP `OAuthLoginService.callback()` 이 **authorize→code→토큰교환(`client_secret_post`)→
> id_token 검증→자체 토큰** 전 구간을 실제로 돈다. `e2e/OidcRpFullCallbackTest`(양성 1 + 음성 3: unknown-state·
> provider-mismatch·wrong-secret). 함정: id_token `iss`=설정 issuer(`http://localhost:9000`)인데 테스트는 RANDOM_PORT →
> discovery·userinfo 가 죽은 9000 을 친다 → authorization/token/jwks 를 **라이브 명시 + discovery off(no-op)**, `userInfoUri`
> 미설정으로 검증된 id_token 클레임만으로 신원 구성(PITFALLS §5).
>
> 암호화 값(서명키 개인키 `enc:` · 설정 `ENC(...)`) 다루는 법은 [`../ENCRYPTION_GUIDE.md`](../reference/ENCRYPTION_GUIDE.md).

## 5. 엔드포인트 (SAS 기본)

`/oauth2/authorize` · `/oauth2/token` · `/oauth2/jwks` · `/.well-known/openid-configuration` · `/userinfo` · `/oauth2/revoke` · `/oauth2/introspect`

## 6. 로컬 실행

> 새 서비스 설정 주의: 매퍼 XML 을 `mapper/` 폴더에 두므로 `application.yml` 에 `mybatis.mapper-locations: classpath*:mapper/**/*.xml` 필수(user-service 규약). 미설정 시 `Invalid bound statement (not found)`.

```bash
./gradlew :services:auth-server:bootRun         # H2 인메모리, demo 클라이언트 자동 등록
# discovery: http://localhost:9000/.well-known/openid-configuration
# demo 로그인: demo / demo
# 데모 클라이언트 3종: demo-web(PKCE) · demo-service(client_credentials, secret=demo-secret) · demo-rp(confidential, client_secret_post, secret=demo-rp-secret)
```

## 6.5 운영(prod/kind) 클라이언트 등록 — 스모크 시더(옵트인)

`LocalDemo` 의 데모 클라이언트 시드는 `@Profile("local")` 이라 **prod 프로파일(kind/운영)에선 비활성**이다. 즉 prod 의 `oauth2_registered_client` 는 **0건이 정상(설계)** — 운영 상시 클라이언트 등록은 프로젝트 책임이다. 다만 `authorization_code+PKCE` 토큰 플로우(= `DbAuthenticator` 운영 인증 경로)를 **스모크 검증**하려면 클라이언트가 1건은 있어야 `/oauth2/authorize` 가 로그인 폼까지 간다.

이를 위한 **프로파일 비의존 옵트인 경로**가 `SmokeClientSeeder` 다.

- 가드: `@ConditionalOnProperty(framework.auth.seed-smoke-client)` — **기본 false**(삼단 토글 정신). 프로파일/`DbAuthenticator` 배선은 건드리지 않는다.
- 켜면(=`FRAMEWORK_AUTH_SEED_SMOKE_CLIENT=true`): `demo-web`(public+PKCE)·`demo-service`(client_credentials)·`demo-rp`(confidential, client_secret_post)를 **`RegisteredClientRepository.save()`** 로 멱등 등록한다(LocalDemo 와 동일 식별자·설정).
- ⚠️ **`oauth2_registered_client` 에 SQL INSERT 수동 등록 금지**: `client_settings`/`token_settings` 가 SAS 전용 Jackson(`@class` 타입 메타) JSON 이라 손으로 만들면 row mapper 가 역직렬화에서 깨진다. 반드시 앱 `save()` 경유. (PITFALLS §9)

kind 검증 절차(overlays/local 은 이 플래그를 이미 on — `auth-server-config` ConfigMap 패치):

```bash
# 0) 플래그 ConfigMap 반영 (⚠️ rollout restart 만으론 기존 ConfigMap 으로 떠서 플래그가 파드 env 에 안 들어간다)
kubectl apply -k deploy/k8s/overlays/local
kubectl -n si-msa exec deploy/auth-server -- printenv FRAMEWORK_AUTH_SEED_SMOKE_CLIENT   # true 떠야 함

# 1) 시더가 포함된 auth-server 이미지 재빌드(compose 빌드 정의: Dockerfile.build, SERVICE 인자, context=repo root)
#    ⚠️ 호스트 빌드 산출물이 이미지로 새지 않도록 .dockerignore 가 있어야 함(PITFALLS §1). 최초 1회 clean 권장.
./gradlew :services:auth-server:clean
docker compose -f deploy/compose/docker-compose.yml build --no-cache auth-server
# 빌드 검증(ENTRYPOINT 무시하고 jar 안 클래스 확인 — 1 이상이면 OK):
docker run --rm --entrypoint sh si-msa/auth-server:local -c 'grep -ac SmokeClientSeeder /application/app.jar'

# 2) 새 이미지로 파드 재생성
#    ⚠️ 같은 :local 태그를 재빌드하면 노드 containerd 가 옛 digest 를 캐시한 채 IfNotPresent 로 새 이미지를 무시한다(PITFALLS §9).
#       delete pod 후에도 파드 digest 가 안 바뀌면(= kubectl get pod -o jsonpath='{..imageID}' 가 호스트 docker images digest 와 다르면)
#       유니크 태그로 우회: docker tag si-msa/auth-server:local si-msa/auth-server:local-N → kustomization newTag 를 local-N 으로 → apply -k.
kubectl -n si-msa delete pod -l app.kubernetes.io/name=auth-server

# 3) 등록 확인 (demo-web/demo-service 2 rows 면 성공)
kubectl -n si-msa exec deploy/postgres -- psql -U auth_app -d authdb \
  -c "SELECT client_id, authorization_grant_types FROM oauth2_registered_client;"

# 4) authorization_code+PKCE: 브라우저로 /oauth2/authorize?client_id=demo-web... → tester/Test1234! 로그인(DbAuthenticator)
#    → code 수령 → POST /oauth2/token(code+code_verifier) → access/id_token.
#    ⚠️ discovery issuer 가 in-cluster 명(http://auth-server:9000)이라 브라우저 redirect 는 port-forward 기준 보정 필요
#       (가장 단순: redirect_uri 를 등록값으로 두고 code 만 받아 토큰교환을 curl 로).
# 5) (선택) client_credentials 1줄:
kubectl -n si-msa port-forward svc/auth-server 9000:9000 &
curl -s -u demo-service:demo-secret -d grant_type=client_credentials -d scope=api.read localhost:9000/oauth2/token
```

> ✅ **검증 완료(2026-06-06)**: 위 절차로 kind 실배포에서 `demo-web`(authorization_code,refresh_token)·`demo-service`(client_credentials) 2 rows 등록 확인. 자동 테스트 `SmokeClientDbAuthFlowTest` 도 통과(sub=`tester`).

> ⚠️ **client_credentials *토큰 발급* 은 standalone kind 에서 깨끗한 검증 잔여(2026-06-06)**: 위 "2 rows 등록 확인"·자동 테스트는 **등록 + authorization_code+PKCE(demo-web)** 경로다. client_credentials access_token 1줄은 standalone kind 리허설에서 stale port-forward 로 302(→/login) 아티팩트가 나왔고(wrong-secret→401 로 client_secret_basic 인증 메커니즘 자체는 정상 확인), 단일 port-forward 로 깨끗이 재요청해 `access_token` 을 확인하는 1줄만 남았다(NEXT_K8S_REAL_DEPLOY §S3' 잔여).

> **자동 테스트 등가물**: `SmokeClientDbAuthFlowTest`(`@ActiveProfiles("smoketest")`)가 위 수동 절차를 그대로 — 스모크 시더 등록 + `tester` 폼 로그인(DbAuthenticator) + `authorization_code+PKCE` → `access/id_token`(sub=`tester`) — 단언한다. 운영 상시 클라이언트는 이 시더를 끄고(기본값) 프로젝트가 자체 등록 절차(예: 부팅 시 `RegisteredClientRepository.save()` 또는 관리 콘솔)로 둘 것.

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

**서명키 회전(2026-06-04 추가)**: 작성 환경(JRE only·Central 차단)에서 Gradle 빌드 불가라, cipher 보호/해제 + 회전 오케스트레이션(RETIRE→INSERT 순서·멱등 가드·grace=retired_at 정리·0-ACTIVE 불변식)을 **순수 JDK standalone 으로 재현 검증 = 23/23 통과**. JUnit 테스트 2종(`SigningKeyRotationServiceTest`·`AesSigningKeyCipherTest`) 작성 완료. **받는 쪽 검증(필수)**:
```bash
export SIGNING_KEY_ROTATION_ENABLED=true LOCK_TYPE=jdbc AES_SECRET="<32+자 강한키>"
./gradlew :services:auth-server:compileJava :services:auth-server:test :services:auth-server:bootRun
# 회전 트리거 후 /.well-known/openid-configuration 의 jwks 에 새 kid 등장 + 직전 kid 가 grace 동안 잔존(오버랩)
```

## 8. 다음 (후속)

- ~~**리소스 서버 이중 issuer 정합(게이트웨이)**~~ **✅ 완료(2026-06-04)**: `services/gateway` 가 토큰 `iss` 로 분기 —
  AS issuer 면 `{issuer}/oauth2/jwks` 로 RS256 검증(`GatewayJwksTokenVerifier`), 내부면 자체 JWT(HMAC). 프레임워크 무변경.
  상세 [`GATEWAY_EDGE_AUTH.md`](./GATEWAY_EDGE_AUTH.md) "이중 발급기".
- ~~**(선택) 다운스트림 servlet zero-trust 재검증**~~ **✅ 완료(2026-06-04)**: `framework-security` 가 `edge-trust.mode=zero-trust` + `resource-server.enabled=true` 에서 `Authorization` Bearer 를 자체 JWT(HMAC) + AS 토큰(RS256/JWKS)으로 **직접 재검증**. 신규 `ResourceServerJwtVerifier`/`DownstreamTokenAuthenticator`/`TokenIssuerKind`, `JwtAuthenticationFilter` 가 모드 분기. 배치 환경별(K8s=gateway-headers / VM=zero-trust) 분기는 env(`EDGE_TRUST_MODE`). 상세 [`../TOKEN_VERIFICATION_GUIDE.md`](../reference/TOKEN_VERIFICATION_GUIDE.md) §6.4/§7.3.
- ~~**▶ 서명키 회전 스케줄러**~~ **✅ 완료(2026-06-04)**: `framework-lock @SchedulerLock`(리더 선출)로 **단일 파드만** 새 ACTIVE 발급 + 직전 키 RETIRE + grace 지난 키 정리(한 트랜잭션, RETIRE→INSERT 순서로 0-ACTIVE 불변식 보장). DB 개인키는 `framework-core` `AesCryptoService`(AES-GCM)로 **컬럼 암호화**(`enc:` 마커, KMS/Vault 는 `SigningKeyCipher` 교체로 후속). 신규 `SigningKeyCipher`/`AesSigningKeyCipher`/`SigningKeyGenerator`/`RsaSigningKeyGenerator`/`SigningKeyRotationService`/`SigningKeyRotationScheduler`/`SigningKeyProperties`, 매퍼 `retireAllActive`/`deleteRetiredOlderThan`, 마이그레이션 V4(framework_lock)/V5(retired_at). 설계 정본 [`../NEXT_SIGNING_KEY_ROTATION.md`](../_internal/planning/NEXT_SIGNING_KEY_ROTATION.md)(편차 2건 = grace 기준 retired_at·회전 순서 retire-then-insert 반영). 순수 JDK 로직 검증 23/23 통과(받는 쪽 Gradle 빌드/`bootRun` 은 별도 — §7).
- **▶ 토큰 발급 라운드트립 통합테스트**: demo-web authorization_code+PKCE, demo-service client_credentials → 게이트웨이 → user-service zero-trust 재검증.
  - local 경로(LocalDemo demo/demo): `TokenIssuanceRoundTripTest`·`OidcIdTokenIssuanceTest` ✅(이전 세션).
  - **prod 경로(DbAuthenticator, smoke 시더) ✅ 검증 완료(2026-06-06)**: `SmokeClientSeeder`(옵트인 `framework.auth.seed-smoke-client`) + `SmokeClientDbAuthFlowTest`(`@ActiveProfiles("smoketest")`) — `tester`/`Test1234!`(authdb `app_user`) 폼 로그인 → authorization_code+PKCE → access/id_token(sub=`tester`). **자동 테스트 통과 + kind 실배포 확인**(overlays/local 플래그 on → prod 프로파일 위 `oauth2_registered_client` 에 `demo-web`/`demo-service` 2 rows 등록). 배포 중 드러난 빌드/배포 함정 2건(`.dockerignore` 부재 → stale jar / 같은 `:local` 태그 노드 캐시)은 코드 아닌 이미지 전달 문제로, `.dockerignore` 추가 + 유니크 태그로 해소(`PITFALLS §1·§9`). 상세 [`../_internal/planning/NEXT_KIND_AUTH_TOKEN_FLOW.md`](../_internal/planning/NEXT_KIND_AUTH_TOKEN_FLOW.md)(ARCHIVED).
  - **✅ confidential `demo-rp` 전체 콜백 흐름 완료(2026-06-06, 받는 쪽 통과 확인)**: `OAuthLoginService.callback()` 이 `OAuthClient.exchangeCodeForTokens`(`client_secret_post`)로 코드 교환 → id_token 검증 → 자체 토큰(검증된 id_token 클레임만으로 신원 구성, `/userinfo` 미사용). `e2e/OidcRpFullCallbackTest`(양성 1 + 음성 3) + `spotlessApply` 통과. `demo-rp` 는 `LocalDemo`·`SmokeClientSeeder` 양쪽 등록(PKCE 불요, consent 미요구). 상세 [`../_internal/planning/NEXT_RP_IDTOKEN_LINK.md`](../_internal/planning/NEXT_RP_IDTOKEN_LINK.md) §B(ARCHIVED).
