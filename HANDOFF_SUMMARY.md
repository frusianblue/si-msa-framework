# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**SSO §6.3 후속 — 게이트웨이 이중 발급기 정합 완료(프레임워크/다운스트림 무변경).** AS(OP) 발급 **RS256/JWKS** 토큰을 게이트웨이 엣지에서 자체 JWT(HMAC)와 **함께** 받도록 정합. **정합 지점 = 게이트웨이(1차 canonical 검증자)** — 게이트웨이가 AS 토큰을 검증해 `X-User-Id`/`X-User-Roles` 를 주입하면 다운스트림(`framework-context`)은 **무변경으로 AS 발급자를 투명 수용**. 토큰 `iss` 로 분기(AS issuer면 JWKS 경로, 아니면 HMAC). `framework-oauth-client.JwksKeyResolver` 패턴(kid 캐시 TTL+미발견 강제 재조회+쿨다운+단일키 fallback)을 **servlet 의존 없이** 게이트웨이 안에 jjwt `keyLocator` 로 자립 재현. 옵트인 기본 off → 켜기 전 동작 100% 동일.

핵심 경계: ① **`iss` 엿보기는 신뢰 경계 아님** — 서명 검증 전 라우팅 힌트일 뿐, 분기된 경로가 서명을 끝까지 검증(위조 iss=라우팅만 바뀌고 탈락). ② **JWKS 조회(블로킹)는 AS 토큰일 때만 `boundedElastic` 오프로드** — 자체 JWT 핫패스는 이벤트 루프 그대로. ③ **AS 토큰은 자체 jti 블랙리스트 미조회**(폐기=AS `/oauth2/revoke`, §4 혼용 금지). ④ WebFlux 게이트웨이에 servlet/Tomcat(`framework-oauth-client`) **드래그인 금지** → jjwt 만.

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: 게이트웨이 이중 발급기 정합 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done) — `services/gateway` (프레임워크 라이브러리 무변경)
- **신규 `auth/TokenIssuerKind`**: enum {INTERNAL, AUTHORIZATION_SERVER}. 검증 경로 + 로그아웃/폐기 경계 구분.
- **신규 `auth/GatewayJwksTokenVerifier`**: AS RS256/JWKS 검증기. `JwksKeyResolver` 패턴 자립 재현(Snapshot kid 캐시 TTL · `lazyRefresh`/`forcedRefresh`+60s 쿨다운 · 단일키 pick). jjwt `Jwts.parser().keyLocator(this::locateKey).clockSkewSeconds(...)`. `locateKey` 는 HS alg 거부(비대칭 전용). `iss`==expectedIssuer + `sub` 필수 검증. 반환=`GatewayTokenVerifier.Verified`(동일 타입 → 헤더 주입 동일). JWKS 조회=`RestClient`(블로킹), `protected fetchJwksJson(uri)` 테스트 오버라이드 지점.
- **신규 `auth/GatewayTokenAuthenticator`**: iss 라우터. `kindOf(token)`(순수 CPU)·`peekIssuer`(payload base64url 디코드+정규식, 서명 전·신뢰 경계 아님)·`verify(token, kind)` 경로 분기.
- **수정 `auth/GatewayAuthGlobalFilter`**: `verifier`→`authenticator`. `kind=authenticator.kindOf(token)` 후 AS면 `Mono.fromCallable(...).subscribeOn(boundedElastic())`, INTERNAL이면 이벤트 루프. `afterVerified`: **INTERNAL만 jti 블랙리스트 조회**(AS는 `Mono.just(false)`), 그 후 헤더 주입.
- **수정 `config/GatewayAuthProperties`**: nested `AuthorizationServer`{enabled=false, issuer, jwksUri, rolesClaim=roles, clockSkew=60s, jwkCacheTtl=1h, `resolvedJwksUri()`(생략 시 {issuer}/oauth2/jwks)}.
- **수정 `config/GatewayAuthConfiguration`**: `@ConditionalOnProperty(authorization-server.enabled=true) gatewayJwksTokenVerifier`(issuer blank면 fail-fast) + `gatewayTokenAuthenticator(verifier, ObjectProvider<GatewayJwksTokenVerifier>)`(getIfAvailable→null이면 AS 경로 비활성).
- **수정 `application.yml`**: `gateway.auth.authorization-server` 블록(enabled=${GATEWAY_AS_ENABLED:false}, issuer=${AUTH_SERVER_ISSUER:}, jwks-uri=${AUTH_SERVER_JWKS_URI:}, roles-claim: roles).
- **테스트**: 신규 `GatewayDualIssuerTest`(RSA 키쌍+수기 JWKS JSON, AS 검증·iss 불일치 거부·HS 토큰 AS경로 거부·iss 라우팅·AS검증기 null이면 전부 INTERNAL·**AS 토큰 jti 블랙리스트 미조회**·INTERNAL은 조회) + `GatewayAuthGlobalFilterTest` authenticator 마이그레이션.
- **문서**: `docs/modules/GATEWAY_EDGE_AUTH.md`(이중 발급기 절+켜는 법)·`docs/modules/AUTH_SERVER.md`(§4 정합 지점=게이트웨이·§8 후속 재조정)·`docs/NEXT_SSO.md`(§6.3 후속 완료)·`HANDOFF.md`(§3 게이트웨이 항목·§6 함정 묶음·§7 상태).

## 검증
- 작성 환경은 **Gradle 배포본(services.gradle.org)+Maven Central 모두 차단** → 컴파일/기동 불가(이전 세션과 동일 제약). 코드 전부 눈으로 정밀 리뷰 + jjwt 0.12.x API 교차 확인(`keyLocator`·`clockSkewSeconds`·`Jwks.setParser().build().parse()`·`jwk.toKey()`·`Decoders.BASE64URL`·`.header().keyId().and()`·`Jwts.SIG.RS256`) + 기존 시그니처 정합 확인(`GatewayTokenBlacklist` 함수형·`Verified(userId,jti,roles)`·`GatewayTokenVerifier(secret,type)`·HMAC 키 유도·`typ=access`·permitAll 기본값). **받는 쪽 `:services:gateway:compileJava :services:gateway:test`+`bootRun` 이 최종 검증.**

## 새로 밟은/확정한 함정 (HANDOFF §6 등록 완료)
1. **정합 지점=게이트웨이(1차 검증자)** — 게이트웨이 헤더 주입 → 다운스트림 무변경 AS 수용. 더 강한 zero-trust(다운스트림 직접 재검증)는 framework-security(servlet) 변경 수반 → 별도 드롭. K8s NetworkPolicy 가 정석 신뢰 경계.
2. **servlet 드래그인 회피 → `JwksKeyResolver` 자립 재현** — WebFlux 게이트웨이에 `framework-oauth-client`(servlet/Tomcat 전이) 의존 금지. jjwt `keyLocator` 로 재구현, HS alg 거부.
3. **`iss` 엿보기 ≠ 신뢰 경계** — 서명 전 라우팅 힌트. 분기 경로가 서명 끝까지 검증하므로 iss 위조=라우팅만 바뀌고 탈락. peek 실패=INTERNAL 안전 강등.
4. **이벤트 루프 — AS 토큰만 `boundedElastic`** — `kindOf`(CPU)로 분기 후 AS 경로만 오프로드. 자체 JWT 핫패스 보존, 캐시 적중 시 네트워크 0.
5. **§4 폐기 경계** — AS 토큰은 jti 블랙리스트 미조회(폐기=AS `/oauth2/revoke`). 테스트가 `blacklistConsulted=false` 로 고정.
6. **옵트인 배선** — JWKS 검증기 `@ConditionalOnProperty(...enabled=true)`, 인증기는 `ObjectProvider.getIfAvailable()`(null=비활성). 기본 off → 항상 INTERNAL = 도입 전 동작 동일. enabled=true+issuer blank=fail-fast.

## 실행/검증 (받는 쪽)
```bash
./gradlew :services:gateway:compileJava :services:gateway:test   # ★ jjwt/시그니처 확정(최우선)
# 이중 발급기 켜기(옵트인):
export GATEWAY_AUTH_ENABLED=true GATEWAY_AS_ENABLED=true
export JWT_SECRET="...자체 JWT 비밀(framework.security.jwt.secret 동일)..."
export AUTH_SERVER_ISSUER="http://localhost:9000"   # services/auth-server 와 동일
./gradlew :services:gateway:bootRun
# 자체 JWT(HMAC) Bearer → 통과 / AS(:9000) 토큰 Bearer → JWKS 검증 통과 / 위조 iss → 401
```

## 다음 (Next) 후보
- **서명키 회전 스케줄러**(`framework-lock @SchedulerLock` 리더선출 + 개인키 암호화 저장 KMS/Vault) — AS §8.
- (선택) **다운스트림 servlet zero-trust 재검증** — user-service 등이 Authorization Bearer 를 직접 이중 issuer 재검증. `framework-security`(servlet) 변경 수반 → 별도 드롭. (기본은 게이트웨이 헤더 신뢰 + K8s NetworkPolicy 권장.)
- **토큰 발급 라운드트립 통합테스트**(demo-web PKCE / demo-service client_credentials).
- (보류) **6.2-B** SP-initiated SLO · **6.4** Passwordless(WebAuthn).

## 받는 쪽 적용 (이번 zip)
```bash
unzip -o si-msa-gateway-dual-issuer.zip   # services/gateway/** + docs/** + HANDOFF*.md 덮어쓰기
# 신규 4파일(TokenIssuerKind·GatewayJwksTokenVerifier·GatewayTokenAuthenticator·GatewayDualIssuerTest)
# + 수정 5파일(GatewayAuthGlobalFilter·GatewayAuthConfiguration·GatewayAuthProperties·application.yml·GatewayAuthGlobalFilterTest)
# + 문서(GATEWAY_EDGE_AUTH/AUTH_SERVER/NEXT_SSO/HANDOFF/HANDOFF_SUMMARY). 삭제 대상 없음.
```
> 코드+문서 드롭. **프레임워크 라이브러리 무변경**(게이트웨이/문서만). 받는 쪽에서 컴파일/테스트/bootRun 으로 최종 검증.
<!-- 갱신 끝 -->
