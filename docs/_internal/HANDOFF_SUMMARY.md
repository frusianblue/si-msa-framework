# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ RP 전체 콜백 흐름(B안) 완료(2026-06-06) — OIDC 풀루프를 검증기 수준(A안)에 이어 풀루프로 마감.** confidential `demo-rp`(`CLIENT_SECRET_POST`+`requireProofKey(false)`+consent 미요구, secret=`demo-rp-secret`)를 `LocalDemo`·`SmokeClientSeeder` 양쪽에 등록하고, 우리 RP `OAuthLoginService.callback()` 이 **authorize→code→토큰교환(`client_secret_post`)→`IdTokenVerifier` 검증→자체 토큰** 전 구간을 실제 구동하는 `OidcRpFullCallbackTest`(양성 1 + 음성 3) 신규. build.gradle 무변경(A안의 `testImplementation project(':framework:framework-oauth-client')` 재사용). 새 함정 3건(PITFALLS §5): ① RANDOM_PORT e2e 에서 discovery·userinfo 가 죽은 issuer 포트(9000)를 침 → authorization/token/jwks 라이브 명시 + discovery off(no-op) + `userInfoUri` 미설정(검증된 id_token 클레임만으로 신원 구성) ② confidential RP 교환=`client_secret_post`+PKCE 불요 → AS 클라이언트도 `CLIENT_SECRET_POST`+`requireProofKey(false)`(builder 기본 true override) ③ `ProviderRegistry.require` 는 OIDC 라도 `user-name-attribute`(="sub") 요구. 스펙 `NEXT_RP_IDTOKEN_LINK` ✅✅ARCHIVED(A·B 모두 완료).

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: RP 전체 콜백 흐름(B안) 완료 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SC 2025.1.1 / Jackson 3 — 스택 무변경.

## 직전에 한 것 (Done)
**B안(confidential demo-rp 전체 콜백 e2e) 구현 — ✅ 받는 쪽 통과 확인(2026-06-06, 양성 1 + 음성 3).** 변경 파일:
| 파일 | 내용 |
|---|---|
| `services/auth-server/.../config/LocalDemo.java` | demo-rp confidential 클라이언트 추가(CLIENT_SECRET_POST, requireProofKey(false), AC+RT, openid/profile, redirect `…:8082/api/v1/auth/oauth/demo-rp/callback`). javadoc 3종으로 갱신. |
| `services/auth-server/.../config/SmokeClientSeeder.java` | 동일 demo-rp 등록(local↔prod 스모크 자산 일치). javadoc(로드맵→등록완료). |
| `services/auth-server/src/test/.../e2e/OidcRpFullCallbackTest.java` (신규) | `@SpringBootTest(RANDOM_PORT)` `@ActiveProfiles("local")`. RP 스택 수기 조립(ProviderRegistry/OAuthClient/InMemoryOAuthStateStore/포착 resolver/echo issuer/no-op OidcMetadataResolver/IdTokenVerifier) → `OAuthLoginService.callback()` 전 구간. 양성 1 + 음성 3(unknown-state·provider-mismatch·wrong-secret). |
| 문서 | `AUTH_SERVER.md` §4·§6·§6.5·§8 · `OIDC_HARDENING.md` §7(B안 완료) · `PITFALLS §5`(+빠른참조 2행) · `HANDOFF §6` · 스펙 ✅✅ARCHIVED. |

## 현재 상태 (적용/검증)
- **정적 교차검증 완료**: brace/paren 균형 OK · `com.fasterxml.jackson` 미사용 OK · SS7 API 권위 확인(`ClientAuthenticationMethod.CLIENT_SECRET_POST` 존재 · `ClientSettings.builder()` 기본 `requireProofKey(true)` → false override 정당) · RP SPI 시그니처 전부 실소스 대조.
- **✅ 받는 쪽 통과 확인(2026-06-06)**: `:services:auth-server:test`(`OidcRpFullCallbackTest` 양성 1 + 음성 3) + `spotlessApply`. (작성환경 Maven Central·Gradle 배포서버 차단 → 실행은 Chae 측.)
- **외부 결합 없음**: discovery off(no-op) + `userInfoUri` 미설정으로 콜백이 검증된 id_token 클레임만으로 신원을 구성(초안의 `/userinfo`/discovery 라이브 결합 → 받는 쪽 실행에서 콜백 `BusinessException` 확인 후 제거).

## 바로 다음 할 일 (Next)
1. **commit/push** — 이번 변경(demo-rp 2개 시드 + OidcRpFullCallbackTest + 문서 6건) + 이전 누적분(게이트웨이 런타임 점검 · smoke 시더).
2. **모듈 README 샘플 코드 롤아웃**(`NEXT_README_SAMPLES.md`): security·redis·session 완료, 나머지 모듈 큐.
- 백로그: CI 게이트 + 멀티모듈 Jacoco aggregate, SP-initiated SLO(6.2-B), WebAuthn(6.4), K8s addons(metrics-server/Prometheus/ingress).

## 이번 세션에서 새로 박힌 함정/원칙 (되돌리지 말 것 — 전부 PITFALLS §5)
- **RANDOM_PORT OIDC RP e2e: discovery·userinfo 가 죽은 issuer 포트(9000)를 친다** → authorization/token/jwks 를 라이브 명시 + discovery off(`ensureResolved` no-op) + `userInfoUri` 미설정(콜백이 검증된 id_token 클레임만으로 신원 구성, AS `/userinfo` resource-server 결합 제거). issuer 는 비우면 `IdTokenVerifier` 가 iss 체크 스킵 → `AuthorizationServerSettings.getIssuer()` 로 핀.
- **confidential RP 전체 콜백 = `client_secret_post`+PKCE 불요** → AS 클라이언트 `CLIENT_SECRET_POST`+`requireProofKey(false)`. `ClientSettings.builder()` 기본이 `requireProofKey(true)` 라 명시 override 필수.
- **`ProviderRegistry.require` 는 OIDC 라도 `user-name-attribute` 요구** — 수기 Provider 구성 시 `userNameAttribute="sub"` 누락 주의.
- **MockMvc-민 code ↔ 실HTTP 토큰교환 호환**: 둘 다 동일 컨텍스트의 공유 `JdbcOAuth2AuthorizationService`(H2)를 쓰므로 전송 방식이 달라도 code 가 조회된다(RANDOM_PORT 컨텍스트=임베디드 서버=MockMvc 컨텍스트).
<!-- 갱신 끝 -->
