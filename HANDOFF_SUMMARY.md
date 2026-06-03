# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**인증 로드맵 3) SSO — A) 중앙 로그아웃/logout-all + B-OIDC) OIDC RP 강화 완료.** 다음 = **B-SAML) SAML 2.0 SP**.

**(A) 사내 SSO 중앙 로그아웃**(이전 드롭, 본 세션서 컴파일 에러 2건 수정 반영): 게이트웨이가 jti 추출 후 `RedisGatewayTokenBlacklist`(`bl:{jti}` reactive 조회)로 **로그아웃 토큰을 엣지 401 차단**(`gateway.auth.blacklist-check.enabled`, redis 전용). `LoginService.logoutAll(access, ip)` = 현재 토큰 항상 블랙리스트(안전망) + `ConcurrentSessionService` 순회로 전 세션 무효화, `POST /api/v1/auth/logout-all`. 완전 커버는 `concurrent-session.enabled=true`+`token.store=redis`. 문서 `docs/modules/SSO_CENTRAL_LOGOUT.md`.

**(B-OIDC) framework-oauth-client OIDC 강화**(본 세션 메인): 지금까지 access_token+userinfo 만 쓰고 **id_token 을 받지도 검증하지도 않던** OAuth2 흐름에, **per-provider `oidc` 블록(기본 off)** 으로 켜는 표준 OIDC RP 검증을 추가. 켜면 callback 에서 **id_token 검증**(JWKS 의 RSA/EC 서명 또는 HS=client-secret HMAC · iss · aud⊇client-id · exp/nbf±clock-skew · nonce · sub) + **discovery 자동적용**(issuer/discovery-uri → authorization/token/userinfo/jwks 보충, 지연·1회·캐시) + **nonce 바인딩**(authorize 발급→state 와 함께 저장→callback id_token 과 대조, 재생/주입 차단). 신원은 검증된 id_token 클레임으로 구성(userInfoUri 있으면 빈 필드만 userinfo 로 보충, id_token 우선). kakao/naver 등 비OIDC 는 100% 그대로. 문서 `docs/modules/OIDC_HARDENING.md`(+`OAUTH_CLIENT.md` §8).

**사용자 환경 컴파일 통과 + 26 테스트 그린 확인.** 다음 세션 설계는 `docs/NEXT_SSO.md` §5(SAML SP) 에 정리해 둠(읽고 시작).

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: SSO(A 중앙로그아웃 + B-OIDC 강화) 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### B-OIDC: framework-oauth-client OIDC RP 강화 (신규 oidc/ 패키지 + 기존 8파일 수정)
- **신규 `oidc/`**:
  - `OidcDiscoveryClient` — `/.well-known/openid-configuration` 조회(RestClient + Jackson3 직접 파싱) → `Metadata(issuer/authz/token/userinfo/jwks)`.
  - `JwksKeyResolver` — JWKS 캐시(`{kid→Key}` 스냅샷, TTL 기본 1h) + kid 해석 + **키 회전 재조회**(미발견 시 강제 fetch) + **쿨다운 60s throttle**(가짜 kid 폭주 방지) + 단일키 null-kid fallback. 파싱은 jjwt `Jwks.setParser()`. 조회는 `protected fetchJwksJson`(테스트 오버라이드 지점).
  - `IdTokenVerifier` — `Jwts.parser().keyLocator(...).clockSkewSeconds(...)`; alg HS*→`Keys.hmacShaKeyFor(clientSecret)`, 그 외→JWKS. 수동 검증 iss/aud(Set contains)/nonce/sub, 클레임 `LinkedHashMap` 반환.
  - `OidcMetadataResolver` — discovery 를 Provider 에 1회 반영(blank 필드만, 지연·캐시, 실패 시 재시도).
- **수정**: `OAuthClientProperties`(Provider.`oidc` nested: enabled/issuer/jwksUri/discoveryUri/clockSkew=60s/nonce=true + google issuer 프리셋 + OIDC sub/email/name 기본) · `OAuthClient`(`exchangeCodeForTokens`=id_token 포함 전체 응답) · `ProviderRegistry`(OIDC `require()` 완화: userinfo 선택, discovery 또는 명시 endpoints+jwks; `authorizationUrl(...,nonce)`=openid 강제+nonce) · `OAuthStateStore`/Memory/Redis(`save(...,nonce,...)`+`consumeState`→`StateData(providerId,nonce)`) · `OAuthLoginService`(OIDC 분기: discovery→nonce→id_token 검증→userinfo 보충) · `OAuthClientAutoConfiguration`(빈 4종 + `oauthLoginService` 8-arg) · `build.gradle`(`testRuntimeOnly jjwt-impl/jjwt-jackson`).
- jjwt-api 는 framework-security 가 `api` 로 노출 → oauth-client 컴파일 클래스패스에 이미 존재(운영 런타임 impl/jackson 도 security 가 전이).

### A: 중앙 로그아웃 컴파일 에러 수정(이전 드롭 보정)
- `LoginService.java` `issue(...)` 시그니처 줄 누락 복구, `AuthController.java` 클래스 닫는 `}` 복구(str_replace 가 old_str 경계줄을 떨어뜨린 것).

## 현재 상태 (적용/검증)
- ✅ **사용자 환경 컴파일 통과 + `:framework:framework-oauth-client:test` 26개 그린 확인(2026-06-03).**
- ⚙️ **세션 중 수정 3건**(전부 받는 쪽 로그로 발견·수정):
  1. A 중앙로그아웃 컴파일 에러 2건(issue 시그니처/클래스 `}` 누락) → 복구.
  2. `JwksKeyResolverTest` 회전 테스트 실패 — `refresh()` 재진입 가드가 **강제 재조회까지 차단**(fetch 1회) → lazy/forced 분리 + 쿨다운으로 강제 fetch 보장(2회).
  3. `IdTokenVerifier` javadoc 의 `RS*/ES*/PS*` 속 **`*/` 가 블록 주석 조기 종료** → 컴파일 에러("class/interface expected") → `RS/ES/PS 계열` 로 교체.
- 신규 파일: oidc/ 4 + test 3(`ProviderRegistryOidcTest`·`IdTokenVerifierTest`·`JwksKeyResolverTest`). 수정 8 + 문서 2(`OIDC_HARDENING.md` 신규·`OAUTH_CLIENT.md`).

## 켜는 법
```yaml
framework:
  oauth-client:
    enabled: true
    state: { store: { type: redis } }   # 멀티 파드면 redis(nonce 도 함께 바인딩)
    providers:
      corp:                              # Keycloak/Azure AD/사내 OIDC IdP
        client-id: "${OIDC_CLIENT_ID}"
        client-secret: "${OIDC_CLIENT_SECRET}"
        oidc:
          enabled: true
          issuer: "https://sso.example.com/realms/corp"   # discovery 출처 + iss 기대값(엔드포인트 자동보충)
```
```bash
# 검증(받는 쪽)
./gradlew :framework:framework-oauth-client:test :framework:framework-archtest:test spotlessApply
```

## 바로 다음 할 일 (Next)
1. **B-SAML) SAML 2.0 SP — `framework/framework-saml-sp` 신설** (설계 `docs/NEXT_SSO.md` §5). Spring Security SAML2 SP 로 외부 SAML IdP 연동 → `SamlUserResolver` 매핑 → 자체 JWT(OAuth 발급기 재사용). **착수 전 §5.3 함정 필독**: OpenSAML 전이 의존(첫 "새 외부 의존성 0" 예외)·리포지터리(Central/Shibboleth)·멀티 파드 `Saml2AuthenticationRequestRepository`(redis). §5.6 결정 먼저.
2. (이후) **C) Authorization Server**(별도 `services/auth-server`, 명시 요구 시) · 4) Passwordless(WebAuthn).
3. (devops, 병행 가능) CI 게이트(`:framework-archtest:test`+전 모듈 `:test` PR 차단)+멀티모듈 jacoco 집계 · 게이트웨이 런타임 점검(CORS preflight·rate-limit 429)·k8s 멀티서비스.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **블록 주석 안에 `*/` 금지**: javadoc/주석 텍스트에 `RS*/ES*/PS*` 처럼 `*/` 가 들어가면 **주석이 거기서 닫혀** 뒤가 코드로 파싱돼 컴파일 에러(+spotless 가 엉뚱한 lint 로 오인). 알고리즘 나열은 `RS/ES/PS 계열` 처럼 `*/` 를 피한다. **brace/paren 점검에 더해 "주석 내 `*/`" 도 점검 항목**.
- **JWKS 회전 재조회 ≠ 신선도 가드**: `refresh()` 의 "캐시 신선하면 그대로 반환" 재진입 가드는 동시 갱신 dedupe 용 → **unknown kid 강제 재조회**는 이 가드를 우회해야 한다(lazy/forced 분리). 단, 가짜 kid 남용 폭주 방지로 **강제 재조회는 쿨다운(60s) throttle**(per-jwks_uri lastForced, 최초는 EPOCH 라 즉시 허용 → 테스트 fetch=2).
- **OIDC id_token 검증은 jjwt 재사용**: jjwt-api 가 security `api` 로 oauth-client 에 전이됨(별도 추가 불필요). JWKS 파싱 `Jwks.setParser()`, alg HS*는 client-secret HMAC·그 외 JWKS. 테스트용 jjwt-impl/jackson 은 `testRuntimeOnly`.
- **OIDC nonce 는 state 와 함께 저장**: `OAuthStateStore.save(state,providerId,nonce,ttl)`/`consumeState→StateData`. Redis 값은 `providerId\nnonce`(개행은 providerId 에 없음), 비OIDC 는 기존 `consume(state):Optional<String>` 그대로(하위호환 default 메서드).
- **str_replace 경계줄 보존**: 메서드 삽입 시 old_str 끝의 다음 멤버 시그니처/클래스 `}` 를 new_str 에 반드시 다시 넣는다(A 중앙로그아웃 2차 컴파일 에러 원인 — 재발 방지).
- (지난·유효) Jackson3(`tools.jackson.*`, annotation만 예외) / compileOnly·implementation 타입 test 재선언(introspection) / 새 오토컨피그 `.imports`+등록가드 / EPP 는 spring.factories(Boot4 패키지) / spotless Palantir·`lineEndings=UNIX`·설정캐시 / 필터·EPP 는 GlobalExceptionHandler 밖 / prod 가드(JWT·DevAuth·Password·AES마스터키) / Boot4 패키지 리네임 추측 금지 / 새 모듈=settings+archtest+imports 동시 등록.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring/라이브러리 무의존 코어로 분리해 JDK 단독 검증.
2. 기존 인터페이스는 capability/오버로드로 확장(이번 OIDC: state store nonce default 메서드, `authorizationUrl` 오버로드, `require()` 분기). 생성자 변경 시 빈 배선(autoconfig)도 같이.
3. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+test 재선언), BOM 밖=`implementation`(+카탈로그/ext 핀). 테스트 전용 런타임은 `testRuntimeOnly`(이번 jjwt-impl).
4. 새 오토컨피그면 `.imports`+등록가드. 신규 모듈은 settings include + archtest testImplementation.
5. 오토컨피그 토글/`@ConditionalOnProperty` + `@ConditionalOnMissingBean` + 라이브러리 `@ConditionalOnClass` 백오프.
6. 테스트: 순수 알고리즘(JDK) + (라이브러리 필요시) 오버라이드 지점으로 네트워크 없이 검증(이번 `fetchJwksJson` 스텁 + 손수 만든 JWKS) + 오토컨피그 토글/`FilteredClassLoader`.
7. 드롭인: 신규+변경 파일 한 zip, 루트 `unzip -o`. 문서 동기화(README/STACK/FRAMEWORK_MODULES/HANDOFF/HANDOFF_SUMMARY/NEXT_SSO). 받는 쪽 `./gradlew :...:test :framework-archtest:test spotlessApply`.
<!-- 갱신 끝 -->
