# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**직전 = OIDC id_token 발급 완료 + 직전 진단 정정(2026-06-04).** 라운드트립 e2e 가 미룬 조각(`openid` scope 코드 교환 → id_token)을 마감했다. **직전 함정 ②(`auth_time` ← `SessionInformation` null)는 SS7 7.0 기준 오진**으로 판명 — GitHub `7.0.x` 정본 `JwtGenerator` 대조 결과, `auth_time`/`sid` 는 `if (sessionInformation != null)` 가드 **안에서** 부여되고(즉 null 이면 Assert 가 아니라 조용히 생략) `auth_time` 값은 principal 의 **`FactorGrantedAuthority` 최신 `issuedAt`** 에서 산출된다. 진짜 원인 = **커스텀 `FrameworkAuthenticationProvider` 가 인증 팩터를 안 붙인 것**(표준 provider 는 `FACTOR_PASSWORD` 자동 부착). **수정 2건(auth-server 내부, framework-security 무변경)** + 신규 `e2e/OidcIdTokenIssuanceTest`(2테스트). MockMvc 로 충분(WebTestClient/SessionRegistry 시드 불필요). **작성환경 Central 차단 → 정적 리뷰만, 받는 쪽(로컬/CI) 실행 대기.** **바로 다음 = (선택) RP `IdTokenVerifier` 연계로 OIDC 풀루프 마감 · 또는 devops CI 게이트.**

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: OIDC id_token 발급 + 진단 정정 세션 (섹션 종료)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)** / Nimbus(SAS 전이)

## 직전에 한 것 (Done — 정적 리뷰 완료, 받는 쪽 실행 대기)
- **근본 원인 재조사(정정)**: GitHub `7.0.x` 브랜치의 `JwtGenerator`/`FactorGrantedAuthority`/`CoreJacksonModule` 정본 소스를 받아 직전 진단을 검증 → **SessionInformation 가설 반증**. Assert(`"authenticationTime cannot be null"`)는 `getAuthenticationTime(principal)` 안에 있고, 그 호출은 `if (sessionInformation != null)` 가드를 통과한 뒤라 → **SessionInformation 은 MockMvc 에서도 non-null**(SAS `oidc()` 기본 OIDC 세션 추적이 채움). auth_time 은 SAS 1.x 의 세션 기반이 아니라 **인증 팩터 기반**(SS7 7.0 변경점).
- **수정 ① `FrameworkAuthenticationProvider`**: 반환 인증 토큰 authorities 에 `FactorGrantedAuthority.fromAuthority(PASSWORD_AUTHORITY)` 부착(principal=`User` 은 역할만 — 표준 form-login 과 동일). `issuedAt` 기본 = `Instant.now()` = auth_time. 직렬화는 SS core Jackson **3** `CoreJacksonModule`(`allowIfSubType`+mixin)이 지원.
- **수정 ② `RoleClaimTokenCustomizer`**: `roles` 클레임 생성 시 `FactorGrantedAuthority` **필터 제외**(인증 팩터가 앱 역할/다운스트림 권한으로 누수 방지). 기존 테스트는 `.contains` 만 단언 → 영향 없음.
- **신규 e2e** `services/auth-server/.../e2e/OidcIdTokenIssuanceTest`(@SpringBootTest RANDOM_PORT, profile local, MockMvc=webAppContextSetup+springSecurity() — 라운드트립 하네스 재사용):
  - test1: formLogin demo/demo → authorize(scope=`openid profile`, nonce, PKCE S256) → 코드 교환 → **id_token+access_token 발급**. id_token 을 실 `/oauth2/jwks`(RS256)+`ResourceServerJwtVerifier`(`expectedAudience=demo-web` 활성)로 검증 + payload 디코드로 `iss`/`sub`/`auth_time`(non-null·양수)/`exp`/`nonce` 왕복/`sid` not-blank/`roles` 단언.
  - test2: 검증기 `expectedAudience="some-other-client"` → 진짜 id_token 도 aud 불일치로 `JwtException`(음성).
- **문서 정비**: `docs/NEXT_OIDC_ID_TOKEN.md`(착수 전 → **✅ 완료 + 정정 배너**로 재작성) · `HANDOFF.md` §6 함정 ②(✅🔧 정정 엔트리) + §7 dated 완료 엔트리 + 우선순위 OIDC 항목 ✅ · `docs/modules/AUTH_SERVER.md` §4(leg2 노트 정정 + ✅ 발급 완료). README/STACK/FRAMEWORK_MODULES 는 **AS-side id_token 미참조**(전부 RP-side `framework-oauth-client`) → 무변경.

## 새로 확정/정정한 함정 (HANDOFF §6 등록)
- **✅🔧 ② [정정] id_token `auth_time` ← `FactorGrantedAuthority`(SessionInformation 아님)**: SS7 7.0 `JwtGenerator.getAuthenticationTime` 은 principal 권한의 `FactorGrantedAuthority` 최신 `issuedAt` 으로 산출, 하나도 없으면 Assert. **SAS 와 함께 쓰는 커스텀 `AuthenticationProvider` 는 표준 provider 처럼 인증 팩터(`FACTOR_PASSWORD`)를 부착해야** OIDC auth_time 이 나온다. `auth_time`/`sid` 는 한 `if (sessionInformation != null)` 가드에서 동시 부여(둘 중 하나 나오면 같이). 인증 팩터는 인가 역할이 아니므로 `roles` 클레임에서 제외.
- (작업 환경 — 유효) **Maven Central 차단**(`host_not_allowed`) → SB4/SS7 의존성 다운로드 불가 = 이 환경 빌드/테스트 불가(정적 리뷰만). GitHub raw 소스 대조는 가능 → 정본 `JwtGenerator` 확인에 활용.
- (테스트 헬퍼 — 유효) JWT 서명 변조 음성테스트는 **중간 문자**를 바꿔야 함(마지막 base64url 문자는 trailing-bit 무효 가능).

## 실행/검증 (받는 쪽 — 로컬/CI 에서 실행 필요)
```bash
./gradlew :services:auth-server:test --tests "*OidcIdTokenIssuanceTest"   # 신규 2테스트
./gradlew :services:auth-server:test --tests "*TokenIssuanceRoundTripTest" # 회귀(여전히 4/4 기대)
```
> 정적 리뷰 근거: ① 검증기 생성자 시그니처(7-arg, expectedAudience 5번째) 라운드트립과 동일 ② 포트 주입 `@Value("${local.server.port}")` 동일 ③ `FactorGrantedAuthority` 직렬화 = SS core Jackson3 지원(표준 form-login 도 SAS JDBC 에 저장하는 검증된 경로). 실행 시 sid 부재/클레임 차이가 나오면 재검토(고신뢰 분석).

## 다음 (Next) 후보
- **▶ (선택) RP `IdTokenVerifier` 연계** — AS 발급 id_token 을 `framework-oauth-client` `IdTokenVerifier` 가 그대로 검증하는 경로까지 e2e 로 닫으면 OIDC 전 구간(발급↔검증) 완결.
- (선택) 게이트웨이측 AS `aud` 검증 · introspection · 서명키 KMS/Vault 백엔드(`SigningKeyCipher` 교체).
- (devops) **CI 게이트**(archtest + 전 모듈 test PR 차단) · 멀티모듈 jacoco 집계 · k8s 멀티서비스/observability 실배포.
- (보류) SSO 6.2-B SP-initiated SLO · 6.4 Passwordless(WebAuthn).
<!-- 갱신 끝 -->
