# framework-mfa

2단계 인증(MFA) — TOTP(RFC 6238)·OTP(SMS/메일/알림톡)·ISMS-P 일회용 복구코드. `framework-security` 로그인이 `MfaGate` SPI 로 2단계 분기한다(미사용 시 단일 단계 그대로). 외부 의존성 0(Base32/HOTP/TOTP/복구코드 전부 JDK).

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-mfa') }   // framework-security 전제
```
```yaml
framework:
  mfa:
    enabled: true            # 기본 false
    issuer: si-msa           # otpauth 발급자 라벨
    policy: ENROLLED         # ENROLLED(등록자만 2단계) | OFF
    totp: { enabled: true, algorithm: SHA1, digits: 6, period-seconds: 30, recovery-codes: 10 }
    otp:  { enabled: false, length: 6 }
    webauthn: { enabled: true }   # 패스키 2차(독립 등록형). framework-webauthn 활성(RP 빈 존재) 시에만 동작
    challenge:  { store: { type: memory }, ttl: 5m, max-attempts: 5 }   # 멀티 인스턴스는 redis 필수
    enrollment: { store: { type: memory } }                            # memory | jdbc
```

## 쓰는 법
```java
private final MfaService mfa;

MfaService.EnrollmentStart start = mfa.beginTotpEnrollment(userId, account);  // secret + otpauth URI
// otpauth URI → QR 은 framework-qr 로 PNG 변환
List<String> recovery = mfa.confirmTotp(userId, code);   // 등록 확정 + 복구코드 발급
MfaService.MfaStatus st = mfa.status(userId);            // confirmed / pending
mfa.disable(userId, MfaMethod.TOTP);
```
- OTP 발송 채널은 `OtpSender` SPI 구현(보통 framework-notification 연동).
- 로그인 흐름: security 가 1차 인증 후 `MfaGate`(=`DefaultMfaGate`)로 2차 챌린지 발급/검증.
- REST: `MfaEnrollmentController` / `MfaVerificationController`.

### JDBC 등록 저장소
`src/main/resources/db/mfa-postgres.sql`. Flyway 권장.


## 실전 사용 예 (코드)

TOTP 등록·확인과 로그인 2차 인증(챌린지→검증)을 제공한다. 내장 컨트롤러(`/api/v1/mfa`, `/api/v1/auth/mfa`)로 바로 쓰거나 `MfaService` 를 직접 호출한다.
```java
// com.company.framework.mfa.core.MfaService
MfaService.EnrollmentStart s = mfa.beginTotpEnrollment(userId, "alice@corp");  // s.otpauthUri() → QR
mfa.confirmTotp(userId, "123456");                                             // 사용자 입력으로 확정

String ticket = mfa.createChallenge(userId, roles, List.of("TOTP"));          // 로그인 1차 성공 후
MfaService.MfaVerification v = mfa.verify(ticket, "TOTP", "654321", clientIp); // 2차 검증 통과 → 토큰 발급 진행
```
```bash
curl -X POST http://localhost:8080/api/v1/mfa/enroll/totp -H 'Authorization: Bearer <t>'
curl -X POST http://localhost:8080/api/v1/auth/mfa/verify \
  -H 'Content-Type: application/json' -d '{"ticket":"...","method":"TOTP","code":"654321"}'
```

### WebAuthn(패스키) 2차 — 독립 등록형
TOTP 처럼 별도 enroll/confirm 로 등록하고 로그인 2단계에서 검증한다. 등록/검증 ceremony 는 framework-webauthn 의 RP 연산·자격증명 저장소를 재사용하며(중복 설정 0, rpId/origin 은 `framework.webauthn.*` 를 따름), challenge 옵션은 세션 대신 발급 티켓에 바인딩한다. attestation/assertion 은 브라우저 `navigator.credentials.*` 결과를 `JSON.stringify` 한 `credentialJson` 으로 보낸다(서버가 SS7 Jackson 3 모듈로 파싱 — 글로벌 Jackson 무영향).
```bash
# 1) 등록(인증 필요): 옵션·티켓 수신 → navigator.credentials.create → 확정
curl -X POST http://localhost:8080/api/v1/mfa/webauthn/enroll -H 'Authorization: Bearer <t>'
#   → { data: { ticket, optionsJson } }   (optionsJson 을 JSON.parse 하여 create 입력)
curl -X POST http://localhost:8080/api/v1/mfa/webauthn/enroll/confirm -H 'Authorization: Bearer <t>' \
  -H 'Content-Type: application/json' \
  -d '{"ticket":"...","credentialJson":"{...attestation...}","label":"내 노트북"}'

# 2) 로그인 2단계(permitAll): 1차 통과 티켓으로 옵션 수신 → navigator.credentials.get → 검증→토큰
curl -X POST http://localhost:8080/api/v1/auth/mfa/webauthn/options \
  -H 'Content-Type: application/json' -d '{"ticket":"<로그인1차_ticket>"}'
#   → { data: "<requestOptions JSON>" }   (JSON.parse 하여 get 입력)
curl -X POST http://localhost:8080/api/v1/auth/mfa/webauthn/verify \
  -H 'Content-Type: application/json' \
  -d '{"ticket":"<로그인1차_ticket>","credentialJson":"{...assertion...}"}'
#   → 성공 시 기존 로그인과 동일한 TokenResponse
```
서버측 직접 호출(`com.company.framework.mfa.core.MfaWebAuthnService`)도 가능: `beginEnrollment(userId)` → `confirmEnrollment(userId, ticket, credentialJson, label)`, `beginAssertion(ticket)` → `verify(ticket, credentialJson, clientIp)`. 자격증명 본체는 RP 가 `UserCredentialRepository` 에 저장하고, MFA 는 `MfaEnrollment(WEBAUTHN, confirmed)` 메타만 기록한다(`status`/`confirmedMethods`/`disable` 은 기존 방식과 동일하게 동작).

## 끄는 법
`framework.mfa.enabled: false`(또는 `policy: OFF`) 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`MfaGate`/`OtpSender`/`MfaEnrollmentStore`/`MfaChallengeStore` SPI 빈 등록 시 교체.

## 버전 관리
**신규 외부 의존성 0**. web/jdbc/data-redis 는 `compileOnly`(테스트는 재선언 필요 — `@ConditionalOnClass` 무관하게 ApplicationContextRunner 가 로드).
