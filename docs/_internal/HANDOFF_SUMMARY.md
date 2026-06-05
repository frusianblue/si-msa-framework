# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**A1 후속 ① WebAuthn 2차 MFA factor 연계 구현(2026-06-05, 독립 등록형).** `framework-mfa` 에 `MfaMethod.WEBAUTHN` 추가 — TOTP 처럼 별도 enroll/confirm 로 등록(`/api/v1/mfa/webauthn/**`, 인증)하고 로그인 2단계에서 검증(`/api/v1/auth/mfa/webauthn/options`·`/verify`, permitAll). 착수 철칙대로 SS7 **7.0.x 소스 전수 확인**: RP `WebAuthnRelyingPartyOperations`(create{Creation,Request}Options·register·authenticate), 요청 타입 SAM/concrete 생성자, **Jackson 3 직렬화 모듈** `WebauthnJacksonModule`(접미사 없음=`tools.jackson.*`; `*Jackson2*`=`com.fasterxml`)이 옵션/attestation/assertion 전부 커버. **세션 ceremony↔무상태 티켓 접합 = challenge 옵션을 발급 티켓(`PendingAuth.webauthnOptionsJson`)에 바인딩**(RP authenticate 가 옵션 재제출 stateful), 소유 판정은 `authenticate` 반환 `userEntity.getName()==ticket.userId`. RP 연산·자격증명 저장소는 framework-webauthn 재사용(중복 0, rpId/origin 은 `framework.webauthn.*`). **SS 결합은 `MfaWebAuthnService`/`MfaWebAuthnController` 로 격리**(핵심 빈 `MfaService` 는 무지) — autoconfig 중첩 `@ConditionalOnClass(WebAuthnRelyingPartyOperations)` 에서만 빈 생성(부재 앱 `NoClassDefFoundError` 차단). 코드+문서 동시 갱신.

## 최종 갱신
- 일자: 2026-06-05 · 갱신자: A1 후속 ① WebAuthn 2차 MFA 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / Jackson 3(tools.jackson.*) — **build.gradle 변경**: framework-mfa 에 `spring-security-webauthn` compileOnly + test 재선언 추가.

## 직전에 한 것 (Done — 정적 검증, 받는 쪽 컴파일/테스트 확인)
- **신규 소스 3종**: `core/MfaWebAuthnSupport`(상태 없는 RP+직렬화 헬퍼 — 전용 `JsonMapper(WebauthnJacksonModule)` 보유, create{Registration,Assertion}OptionsJson·registerCredential·authenticate, 제네릭 자격증명은 `tools.jackson.core.type.TypeReference`) · `core/MfaWebAuthnService`(ceremony 오케스트레이션 — beginEnrollment/confirmEnrollment/beginAssertion/verify, 티켓 바인딩·`MfaEnrollment(WEBAUTHN,confirmed)` 기록·시도 누적) · `web/MfaWebAuthnController`(절대경로 등록[인증]+검증[permitAll], attestation/assertion 은 `credentialJson`=클라 stringify).
- **수정 5종(소스)**: `core/MfaMethod`(+`WEBAUTHN`+`from`) · `store/PendingAuth`(+`webauthnOptionsJson` 6번째 필드+wither) · `store/RedisMfaChallengeStore`(코덱 6번째 base64 필드+하위호환 길이체크) · `config/MfaProperties`(+`Webauthn{enabled=true}`) · `config/MfaAutoConfiguration`(중첩 `WebAuthnMfaConfiguration` = support+service+controller 빈, `mfaService` 는 원복 유지).
- **신규 테스트 1종**: `MfaWebAuthnServiceTest`(10) — support mock + InMemory stores 로 등록 바인딩·확정 메타·userId 불일치 거부·assertion 옵션 바인딩·미가용 챌린지 거부·검증 성공/username불일치 누적/시도초과 LOGIN_LOCKED·webauthn 비활성 FORBIDDEN.
- **SS7 API 그라운딩(7.0.x)**: RP 4메서드 시그니처 · `RelyingPartyAuthenticationRequest(options, PublicKeyCredential<AuthenticatorAssertionResponse>)` concrete · `RelyingPartyPublicKey(credential, label)` · `RelyingPartyRegistrationRequest`(SAM 2메서드 익명구현) · `PublicKeyCredential{Creation,Request}OptionsRequest`(SAM `getAuthentication`→람다) · register/authenticate 가 `userCredentials.save` 직접 수행 · `PublicKeyCredentialUserEntity.getName()` · 옵션 타입 `final class`.
- **문서 동반 갱신 6종**: framework-mfa README(yaml `webauthn.enabled`+실전 WebAuthn enroll/options/verify curl) · PITFALLS §5(+4건: SS캡슐화→핵심빈 금지·중첩격리 / Jackson3 모듈 재사용 / 티켓 바인딩+RP 자가저장 / TypeReference 제네릭) · AUTH_COMPOSITION_GUIDE(§4 factor 표 +패스키 2차 행·future-work) · FRAMEWORK_MODULES(mfa 카탈로그 +webauthn factor·`webauthn.enabled`) · framework/README(mfa 행) · 본 HANDOFF_SUMMARY.

## 현재 상태 (적용/검증)
- 작성환경 Maven Central 차단 → SS 7.0.x 소스 대조 기반 정적 작성. **받는 쪽에서 컴파일/테스트 확인 필요**: `:framework:framework-mfa:test`(기존 + 신규 `MfaWebAuthnServiceTest` 10) · `spotlessApply`(Palantir, 작성환경 미실행) · `:framework:framework-archtest:test`.
- **브라우저 실서명(navigator.credentials.create/get)·MockMvc 엔드포인트 스모크는 web 앱(framework-webauthn 활성+`UserDetailsService`+HTTPS)에서 검증 잔여.** WebAuthn 2차는 framework-webauthn 활성(RP 빈) + `framework.mfa.webauthn.enabled=true` 동시 필요.

## 바로 다음 할 일 (Next)
- 받는 쪽에서 위 3개 그래들 태스크 확인.
- **A1 남은 후속**: ③ rpId/origin 멀티서비스 일원화 정책 문서(②①은 완료).
- 이후 A2(SP-initiated SLO)·A3(KMS/Vault) 각 독립 세션.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **SS 캡슐화 타입을 항상 로드되는 핵심 빈 필드로 들지 말 것** — `MfaService` 에 `MfaWebAuthnSupport`(SS 결합) 넣으면 webauthn 부재 앱 `NoClassDefFoundError`. → `MfaWebAuthnService`/`MfaWebAuthnController` 분리 + 중첩 `@ConditionalOnClass` 빈 [PITFALLS §5 ★].
- **WebAuthn 직렬화는 SS7 Jackson 3 `WebauthnJacksonModule` 재사용**(수기 코덱 금지). 제네릭 자격증명은 `tools.jackson.core.type.TypeReference`. MFA 전용 매퍼로 글로벌 Jackson 무영향 [PITFALLS §5].
- **세션 ceremony↔무상태 MFA = challenge 를 발급 티켓에 바인딩**(`PendingAuth.webauthnOptionsJson`). RP authenticate 가 옵션 재제출 stateful + 등록/검증 시 저장소 자가 save → MFA 는 메타만. 소유 판정 `userEntity.getName()==ticket.userId` [PITFALLS §5].
- **`credentialJson` = 클라 stringify(attestation/assertion)** — `Map<String,String>` 로 받아 전용 매퍼로 파싱(글로벌 Jackson 에 webauthn 모듈 등록 불요). 컨트롤러 경로는 절대경로(등록 `/api/v1/mfa/webauthn/**` 인증·검증 `/api/v1/auth/mfa/webauthn/**` permitAll).
<!-- 갱신 끝 -->
