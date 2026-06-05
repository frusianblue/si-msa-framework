# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**A1 후속 ③ WebAuthn rpId/origin 멀티서비스 일원화 정책(2026-06-05) — A1(passkey) 묶음 완결.** 패스키는 rpId(등록가능 도메인)에 바인딩되고 origin host 는 rpId 와 같거나 하위 도메인이어야 하므로, MSA 에서 서비스별 설정이 갈리면 한 서비스 등록 패스키를 다른 서비스가 검증 못 한다. 정책 문서 신설(`docs/guide/WEBAUTHN_RPID_ORIGIN_POLICY.md`) + **정책을 코드로 강제하는 기동 검증 가드** `WebAuthnRpSafetyGuard`(jwt-secret/session-store 가드와 동일 패턴) 추가. 가드는 서비스마다 부팅 시 rp-id↔origin 정합(origin host 가 rp-id 등록가능 도메인 안인지)·prod https·localhost 오용·allowed-origins 누락을 검사 → **prod 위반은 부팅 실패, 비-prod 는 경고**. `diagnose()` static 분리로 프로파일/부팅 없이 단위 검증. MFA WebAuthn 2차(②)는 동일 RP 빈 재사용이라 rpId/origin 자동 일관(별도 설정 0).

## 최종 갱신
- 일자: 2026-06-05 · 갱신자: A1 후속 ③ rpId/origin 일원화 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / Jackson 3(tools.jackson.*) — **스택·build.gradle 무변경**(spring-security-webauthn 이미 존재, 신규 의존 0).

## 직전에 한 것 (Done — 정적 검증, 받는 쪽 컴파일/테스트 확인)
- **신규 소스 1종**: `config/WebAuthnRpSafetyGuard`(InitializingBean + Environment prod 판정 → prod FAIL / 비-prod WARN; `diagnose(rpId, origins, prod)` static: rp-id 공백·localhost(prod)·origins 공백(prod)·origin 형식/scheme·https(prod, localhost 예외)·origin host 가 rp-id 등록가능 도메인 밖 검사. `hostMatchesRpId` = host==rpId || host.endsWith("."+rpId)).
- **수정 1종(소스)**: `config/WebAuthnAutoConfiguration`(+`webAuthnRpSafetyGuard` 빈 — RP 연산 빈 옆, `@ConditionalOnMissingBean`, enabled 컨텍스트; +`Environment` import).
- **신규 테스트 1종**: `WebAuthnRpSafetyGuardTest`(10) — prod 정상(상위도메인 rpId+서브도메인 origin / rpId==origin)·로컬 정상·비prod origins공백 허용·rpId공백 거부·prod localhost rpId 거부·prod origins공백 거부·prod http 거부·origin host 무관 거부·형식오류 거부.
- **신규 문서 1종**: `docs/guide/WEBAUTHN_RPID_ORIGIN_POLICY.md`(rpId 범위 결정표·권장 토폴로지·설정 일원화(Config/ConfigMap)·가드 표·안티패턴·배포 체크리스트).
- **문서 동반 갱신 4종**: framework-webauthn README(HTTPS 노트 옆 일원화/가드 + 정책 링크) · PITFALLS §5(+1건 rpId/origin 일원화·가드) · AUTH_COMPOSITION_GUIDE(future-work ①②③ 완료) · 본 HANDOFF_SUMMARY.

## 현재 상태 (적용/검증)
- 작성환경 Maven Central 차단 → 정적 작성. **받는 쪽에서 컴파일/테스트 확인 필요**: `:framework:framework-webauthn:test`(기존 + 신규 `WebAuthnRpSafetyGuardTest` 10) · `spotlessApply`(Palantir) · `:framework:framework-archtest:test`.
- 가드는 **정합성 검사만** — 실제 ceremony/credential 공유 동작은 멀티서비스 배포(공통 rp-id+공유 jdbc 저장소+게이트웨이 라우팅)에서 검증. 정책 문서의 배포 체크리스트 참조.

## 바로 다음 할 일 (Next)
- 받는 쪽에서 위 3개 그래들 태스크 확인.
- **A1(passkey) 묶음 완결**(①2차 MFA factor ②패스키 관리 ③rpId/origin 정책 모두 done). 다음 큰 축: **A2(SAML SP-initiated SLO)·A3(KMS/Vault 키관리)** 각 독립 세션.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **멀티서비스 rpId/origin 일원화** — 전 서비스 동일 rp-id(공통 상위 도메인)+동일 allowed-origins+credential 저장소 공유+ceremony 전담 서비스. 서비스별 yml 중복 금지(Config/ConfigMap 단일 출처). origin host ⊄ rp-id 등록가능 도메인 = ceremony 거부 [PITFALLS §5, WEBAUTHN_RPID_ORIGIN_POLICY.md].
- **`WebAuthnRpSafetyGuard` = jwt-secret 가드 패턴**(InitializingBean+Environment, prod FAIL/비-prod WARN). 진단 로직은 `diagnose()` static 분리 → 단위 테스트 용이. 4번째 SafetyGuard 계열(jwt/session/devauth/password 와 동형).
- **MFA WebAuthn 2차는 rpId/origin 자동 일관** — 동일 RP 빈 재사용이라 framework.mfa 쪽에 rpId/origin 설정 없음(framework.webauthn.* 단일 출처).
<!-- 갱신 끝 -->
