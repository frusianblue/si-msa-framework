# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**A1 후속 ② 패스키 관리(목록/삭제) 구현(2026-06-05).** 기존 `framework-webauthn` 에 자격증명 관리 엔드포인트 추가 — `GET {credentials-path}`(내 패스키 요약 목록)·`DELETE {credentials-path}/{credentialId}`(소유 1건 삭제). 착수 철칙대로 spring-security **7.0.x 소스를 blobless 클론**해 `UserCredentialRepository`/`PublicKeyCredentialUserEntityRepository`/`CredentialRecord`/`Bytes`/`CredentialRecordOwnerAuthorizationManager` 시그니처를 전수 확인 후 코딩. **삭제 소유권은 자체 비교 대신 SS7 네이티브 `CredentialRecordOwnerAuthorizationManager`(since 6.5.10) 재사용** — 인증·존재·소유(handle 일치)를 한 번에 판정하고 소유아님·미존재를 모두 deny → 우리는 **deny→`NOT_FOUND(404)`** 로 변환해 존재 여부 비노출(WebAuthn §14.6.3). 관리 엔드포인트는 등록과 **동일한 세션+CSRF 전용 체인**의 `authenticated()` 안(JWT 무상태 주류만 가진 호출자는 진입 불가 — 의도된 경계). 서비스 단위테스트 5종(인터페이스 mock + 실제 `Bytes`). 코드+문서 동시 갱신. **새 외부 의존성 0·배선 무변경(신규 모듈 아님).**

## 최종 갱신
- 일자: 2026-06-05 · 갱신자: A1 후속 ② 패스키 관리 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / Jackson 3(tools.jackson.*) — **스택·build.gradle 무변경**(web/jdbc/spring-security-webauthn 이미 test 재선언됨)

## 직전에 한 것 (Done — 정적 검증, 받는 쪽 컴파일/테스트 확인)
- **신규 소스 3종**(`web/`): `WebAuthnCredentialSummary`(record DTO — credentialId(base64url)/label/type/transports/signatureCount/backupEligible/backupState/created/lastUsed, 공개키·attestation·user handle 비노출) · `WebAuthnCredentialService`(목록=username→`findByUsername`→handle→`findByUserId`→매핑, 삭제=`Bytes.fromBase64`→`CredentialRecordOwnerAuthorizationManager.authorize`→deny시 NOT_FOUND→`delete`) · `WebAuthnCredentialController`(`@GetMapping`/`@DeleteMapping` + `${framework.webauthn.credentials-path:...}`, GET CSRF 불요·DELETE CSRF 필수).
- **수정 2종(소스)**: `WebAuthnProperties`(+`credentialsPath` 기본 `/api/v1/auth/webauthn/credentials`) · `WebAuthnAutoConfiguration`(전용 체인 `securityMatcher` 에 `{credentials-path}`·`{credentials-path}/**` 추가 + 빈 3종 `webAuthnCredentialOwnerAuthorizationManager`/`webAuthnCredentialService`/`webAuthnCredentialController`, 전부 `@ConditionalOnMissingBean`, SERVLET 중첩 config).
- **신규 테스트 1종**: `WebAuthnCredentialServiceTest`(5) — 목록 필드매핑·handle 미존재→빈목록·소유삭제→delete호출·비소유→BusinessException+delete미호출·잘못된 base64url→BusinessException. `CredentialRecord`/`PublicKeyCredentialUserEntity` 인터페이스 mock + 실제 `Bytes` + 실제 `CredentialRecordOwnerAuthorizationManager(mockRepos)`.
- **SS7 API 그라운딩(7.0.x 소스)**: `Bytes.fromBase64(String)`/`toBase64UrlString()`(url-safe no-pad, equals=base64url 문자열 비교) · `CredentialRecord.get{CredentialId,Label,Created,LastUsed,SignatureCount,Transports,CredentialType,UserEntityUserId}`/`isBackup{Eligible,State}` · `AuthenticatorTransport.getValue()`/`PublicKeyCredentialType.getValue()`·`.PUBLIC_KEY` · `CredentialRecordOwnerAuthorizationManager(userCreds, userEntities).authorize(Supplier<Authentication>, Bytes)→AuthorizationResult.isGranted()`.
- **문서 동반 갱신 5종**: 모듈 README(엔드포인트 표 +목록/삭제 행·"인증 컨텍스트(설계 경계)" 노트·실전 curl·덮어쓰기에 `WebAuthnCredentialService`) · AUTH_COMPOSITION_GUIDE(§0 상태표·future-work) · PITFALLS §5(+4건: 소유권 매니저 재사용·인증 컨텍스트 경계·인터페이스 mock 테스트 패턴) · FRAMEWORK_MODULES(카탈로그 +관리·`credentials-path`) · framework/README(webauthn 행).

## 현재 상태 (적용/검증)
- 작성환경 Maven Central 차단 → SS 7.0.x 소스 대조 기반 정적 작성. **받는 쪽에서 컴파일/테스트 확인 필요**: `:framework:framework-webauthn:test`(기존 토글 3 + 신규 서비스 5 = 8) · `spotlessApply`(Palantir, 작성환경 미실행) · `:framework:framework-archtest:test`.
- **브라우저 ceremony 실서명·MockMvc 관리 엔드포인트 스모크는 web 앱(`UserDetailsService`+`spring-security-webauthn`+HTTPS)에서 검증 잔여**(기존과 동일). `CredentialRecordOwnerAuthorizationManager` 는 SS ≥ 7.0.0 필요(Boot 4.0.6 BOM 충족).

## 바로 다음 할 일 (Next)
- 받는 쪽에서 위 3개 그래들 태스크 확인.
- **A1 남은 후속**: ① 2차 MFA factor 연계(mfa `MfaMethod` 에 `WEBAUTHN` 추가 — 세션 ceremony ↔ 티켓기반 `MfaService.verify` 접합, 횡단적·별도 설계) ③ rpId/origin 멀티서비스 일원화 정책 문서.
- 이후 A2(SP-initiated SLO)·A3(KMS/Vault) 각 독립 세션.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **패스키 관리 소유권 = SS7 `CredentialRecordOwnerAuthorizationManager` 재사용**(자체 equals 비교 금지 — 드리프트 위험). 소유아님·미존재 모두 deny → **deny→404** 로 존재 비노출 [PITFALLS §5].
- **관리 엔드포인트 인증 컨텍스트 경계** — 등록/목록/삭제는 세션+CSRF 전용 체인 `authenticated()` 안. JWT 무상태 주류만 가진 호출자 진입 불가(의도). JWT 사용자가 관리하려면 받는 앱이 전용 체인에 1차 인증 추가(향후) [PITFALLS §5].
- **`CredentialRecord`/`PublicKeyCredentialUserEntity` 는 인터페이스** → Mockito mock + 실제 `Bytes`(concrete final)로 웹/크립토 없이 단위검증(Central 차단 적합) [PITFALLS §5].
- 사용자 식별 키는 ceremony·SS authz 매니저와 동일하게 principal username(`Authentication#getName()`)→`findByUsername`→user handle(`Bytes`).
<!-- 갱신 끝 -->
