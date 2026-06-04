# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**다운스트림 zero-trust 재검증 완료 — AS 토큰(RS256/JWKS)까지(`framework-security`).** VM 배치 대비, 다운스트림이 게이트웨이 주입 헤더를 믿을지 / `Authorization` Bearer 를 직접 재검증할지 **`framework.security.edge-trust.mode` 로 분기**(`zero-trust` 기본 | `gateway-headers`). zero-trust 경로는 자체 JWT(HMAC) + **AS(OP) RS256/JWKS** 토큰을 **이중 발급기 라우팅**으로 모두 로컬 재검증한다. 게이트웨이 이중 발급기(`GatewayJwksTokenVerifier`/`GatewayTokenAuthenticator`)의 **servlet 이식** — `framework-oauth-client` 비의존, jjwt(보유)+RestClient(starter-security 전이)로 자립 구현. 토글 기본값(zero-trust + resource-server off)은 도입 전 동작 100% 동일.

핵심 경계: ① **`iss` 엿보기는 신뢰 경계 아님** — 서명 전 라우팅 힌트, 분기 경로가 서명 끝까지 검증. ② **기본 strict(zero-trust)** — 완화(`gateway-headers`)는 NetworkPolicy 등 격리 확인 후 의도적 옵트인. ③ **AS 재검증은 `resource-server.enabled=true` 필요**(issuer blank=fail-fast). ④ **AS 토큰은 jti 블랙리스트 미조회**(§4, INTERNAL 만). ⑤ **K8s/VM 은 `local|dev|prod` 와 직교** → `EDGE_TRUST_MODE` 환경변수 주입(K8s+NetworkPolicy=gateway-headers / VM=zero-trust). ⑥ `framework-context`-only 서비스는 본질적 헤더 신뢰형 — zero-trust 필요 시 `framework-security` 를 켠다.

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: 다운스트림 zero-trust 재검증 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done) — `framework/framework-security` (+ user-service yml, 문서)
- **신규 `jwt/TokenIssuerKind`**: enum {INTERNAL, AUTHORIZATION_SERVER} (servlet 판, 게이트웨이 동명 enum 과 같은 의미).
- **신규 `jwt/ResourceServerJwtVerifier`**: AS RS256/JWKS 검증기. `JwksKeyResolver` 패턴 자립 재현(Snapshot kid 캐시 TTL · `lazyRefresh`/`forcedRefresh`+60s 쿨다운 · 단일키 pick). jjwt `keyLocator`(HS alg 거부=비대칭 전용) + `iss`==expectedIssuer + (선택)`aud` + `sub` 검증. 반환 `Verified(userId, jti, roles)`. `protected fetchJwksJson(uri)` 테스트 오버라이드 지점. ctor(RestClient, issuer, jwksUri, rolesClaim, audience(null=생략), clockSkew, cacheTtl).
- **신규 `jwt/DownstreamTokenAuthenticator`**: iss 라우터 → Spring `Authentication`. `kindOf`(verifier 부재면 항상 INTERNAL)·`peekIssuer`(base64url+정규식, 서명 전·신뢰 경계 아님)·`tryAuthenticate`(실패 시 null=관용). INTERNAL=`JwtProvider.getAuthentication`+jti, AS=`verifier.verify`→`User`+ROLE_ 접두 `UsernamePasswordAuthenticationToken`. 반환 `Authenticated(authentication, jti, kind)`.
- **수정 `jwt/JwtAuthenticationFilter`**: 생성자 `(DownstreamTokenAuthenticator, TokenStore, Mode, userIdHeader, rolesHeader)`. `Mode.ZERO_TRUST`=`fromBearer`(tryAuthenticate, **INTERNAL 만** `tokenStore.isBlacklisted`), `Mode.GATEWAY_HEADERS`=`fromGatewayHeaders`(X-User-Id/X-User-Roles→ROLE_ 접두 PreAuth, Bearer 생략).
- **수정 `config/FrameworkSecurityProperties`**: nested `EdgeTrust{Mode mode=ZERO_TRUST, userIdHeader=X-User-Id, rolesHeader=X-User-Roles}` + `ResourceServer{enabled=false, issuer, jwksUri, rolesClaim=roles, audience, clockSkew=60s, jwkCacheTtl=1h, resolvedJwksUri()=비면 {issuer}/oauth2/jwks}`.
- **수정 `config/SecurityAutoConfiguration`**: `@ConditionalOnProperty(resource-server.enabled=true) resourceServerJwtVerifier`(issuer/jwksUri blank=fail-fast, `RestClient.create()`) + `downstreamTokenAuthenticator(JwtProvider, ObjectProvider<ResourceServerJwtVerifier>)`(getIfAvailable). 정상 체인 파라미터 `JwtProvider`→`DownstreamTokenAuthenticator`, 필터 생성 시 모드(EdgeTrust→Filter.Mode)+헤더명 전달. (RestClient=starter-security 전이 → build.gradle 무변경.)
- **수정 `services/user-service/application.yml`**: `framework.security.edge-trust.mode: ${EDGE_TRUST_MODE:zero-trust}`(+user-id/roles-header) · `resource-server`(enabled ${RESOURCE_SERVER_ENABLED:false}, issuer ${AUTH_SERVER_ISSUER:}, jwks-uri, roles-claim, audience).
- **테스트 신규 `DownstreamDualIssuerTest`**(11): RSA 키쌍+수기 JWKS, AS 검증·iss 불일치 verifier 거부·HS 토큰 AS경로 거부·iss 라우팅·verifier 부재 INTERNAL 폴백+AS 실패·AS ROLE_ 권한·zero-trust 자체 JWT 인증+블랙리스트 조회·블랙리스트 거부·**AS 토큰 블랙리스트 미조회**·zero-trust 위조헤더 무시·gateway-headers 헤더 신뢰(ROLE_USER,ROLE_ADMIN)·gateway-headers 헤더 없으면 미인증.
- **문서**: `docs/TOKEN_VERIFICATION_GUIDE.md`(§6.4 구현됨·§7.3 real property+env 주입·§5.2-⑤ 해소)·`docs/modules/AUTH_SERVER.md` §8(완료)·`HANDOFF.md`(§3·§6 함정 묶음·§7 상태).

## 검증
- 작성 환경은 **Gradle 배포본(services.gradle.org)+Maven Central 모두 차단** → 컴파일/테스트 불가. 코드 전부 눈으로 정밀 리뷰 + jjwt 0.12.x API 교차 확인(`keyLocator`·`clockSkewSeconds`·`Jwks.setParser().build().parse()`·`jwk.toKey()`·`getAudience()`=Set·`Decoders.BASE64URL`·`.header().keyId().and()`·`Jwts.SIG.RS256`) + 시그니처 정합(`TokenStore` 5메서드·`JwtProperties` record·`JwtProvider.getAuthentication/getJti/validate`·RestClient 전이) + 미사용 import 0 확인. **받는 쪽 `:framework:framework-security:test`+`spotlessApply` 가 최종 검증.**

## 새로 밟은/확정한 함정 (HANDOFF §6 등록 완료)
1. **자립 구현(oauth-client 비의존)** — servlet 측도 jjwt+RestClient(starter-security 전이)로 직접. 게이트웨이 검증기의 servlet 미러.
2. **`iss` ≠ 신뢰 경계** — peek 은 라우팅 힌트, 분기 경로가 서명 끝까지 검증. peek 실패=INTERNAL 안전 강등.
3. **기본 strict** — `edge-trust.mode` 기본 zero-trust, `resource-server.enabled` 기본 false → 도입 전 동작 동일. 완화는 의도적 옵트인.
4. **AS 재검증=`resource-server.enabled=true`** — 없으면 AS 토큰 INTERNAL 분기→HMAC 실패→401. issuer blank=fail-fast.
5. **§4 폐기 경계** — AS 토큰 jti 블랙리스트 미조회(폐기=AS `/oauth2/revoke`), INTERNAL 만 조회. 테스트가 `consulted=false` 고정.
6. **토글 env 주입·`local|dev|prod` 와 직교** — K8s/VM 은 배치 토폴로지(프로파일 축 아님) → `EDGE_TRUST_MODE`/`RESOURCE_SERVER_ENABLED`.
7. **`framework-context`-only=본질적 헤더 신뢰형** — 인증 권한이 보안 체인에 있음. zero-trust 필요 시 framework-security 를 켠다(framework-context 무변경).

## 실행/검증 (받는 쪽)
```bash
./gradlew :framework:framework-security:test spotlessApply   # ★ jjwt/시그니처 확정(최우선)
# VM 배치(zero-trust + AS 재검증):
export EDGE_TRUST_MODE=zero-trust RESOURCE_SERVER_ENABLED=true
export AUTH_SERVER_ISSUER="http://localhost:9000"   # services/auth-server 와 동일(jwks=/oauth2/jwks 자동)
./gradlew :services:user-service:bootRun
# 자체 JWT(HMAC) Bearer → 통과 / AS(:9000) 토큰 Bearer → JWKS 재검증 통과 / 위조 → 401 / 위조 X-User-* → 무시
# K8s 배치(gateway-headers, NetworkPolicy 전제): export EDGE_TRUST_MODE=gateway-headers
```

## 다음 (Next) 후보
- **서명키 회전 스케줄러**(`framework-lock @SchedulerLock` 리더선출 + 개인키 암호화 저장 KMS/Vault) — AS §8.
- **토큰 발급 라운드트립 통합테스트**(demo-web PKCE / demo-service client_credentials → 게이트웨이 → user-service zero-trust 재검증).
- (선택) 게이트웨이측 AS `aud` 검증 추가(가이드 §6.1) · introspection(§6.2).
- (보류) **6.2-B** SP-initiated SLO · **6.4** Passwordless(WebAuthn).

## 받는 쪽 적용 (이번 zip)
```bash
unzip -o si-msa-downstream-zerotrust.zip   # framework/framework-security/** + services/user-service/application.yml + docs/** + HANDOFF*.md
# 신규: jwt/TokenIssuerKind · jwt/ResourceServerJwtVerifier · jwt/DownstreamTokenAuthenticator · test/DownstreamDualIssuerTest
# 수정: jwt/JwtAuthenticationFilter · config/FrameworkSecurityProperties · config/SecurityAutoConfiguration · user-service/application.yml
# 문서: TOKEN_VERIFICATION_GUIDE · AUTH_SERVER · HANDOFF · HANDOFF_SUMMARY. 삭제 대상 없음.
```
> 코드+문서 드롭. **build.gradle 무변경**(RestClient=starter-security 전이). 받는 쪽에서 컴파일/테스트/bootRun 으로 최종 검증.
<!-- 갱신 끝 -->
