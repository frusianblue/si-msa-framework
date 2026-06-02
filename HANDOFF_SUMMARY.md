# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**framework-mfa(2단계 인증) 신규 모듈** 완성 — TOTP(RFC 6238)·OTP(`OtpSender` SPI)·**ISMS-P 일회용 복구코드**(SHA-256)를 제공하고, framework-security 로그인 흐름에 **`MfaGate` SPI**(nullable 선택주입)로 2단계 분기를 연결. **MFA 미사용/미의존이면 기존 단일단계 로그인과 완전 동일**(하위호환). **새 외부 의존성 0개**(Base32/HOTP/TOTP/복구코드 전부 JDK `javax.crypto`/`SecureRandom` 직접 구현).

## 최종 갱신
- 일자: 2026-06-02 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **framework-security 확장(SPI 신설, 가산·하위호환)**:
  - **`auth/MfaGate`**(인터페이스) — `isRequired(AuthenticatedUser)` / `issueChallenge(user, clientIp)→MfaTicket`. security 는 이 타입만 알고 구현은 모름(의존 방향 mfa→security 단방향).
  - **`auth/MfaTicket`**(record: ticket, methods, expiresInSeconds) · **`auth/LoginOutcome`**(sealed: `Authenticated(tokens)` | `MfaRequired(ticket)`).
  - **`LoginService`** — nullable `MfaGate` 필드 + **3번째 생성자(9-arg)**. `beginLogin(cmd, ip)→LoginOutcome`(1차 성공 후 MfaGate 있으면 챌린지, 없으면 즉시 토큰) · `completeMfa(userId, roles, ip)→TokenResponse`(2단계 통과 후 발급). 기존 5/8-arg 생성자·`login()` 은 null 위임으로 유지.
  - **`AuthController#login`** 반환형 `ApiResponse<Object>` 로 변경(MFA 필요 시 `MfaTicket` 반환). **`AuthAutoConfiguration`** loginService 빈이 `ObjectProvider<MfaGate>` 주입. **`LoginAuditEvent.Type`** 에 MFA_CHALLENGE/SUCCESS/FAILURE/ENROLLED/DISABLED 추가. **framework-audit `LoginAuditListener`** 실패 판정을 `name().endsWith("FAILURE")` 로 일반화.
- **framework-mfa 신규 모듈**(base `com.company.framework.mfa`, 토글 `framework.mfa.enabled` 기본 false, `policy=ENROLLED|OFF`):
  - **totp**: `Base32`(RFC 4648) · `Totp`(RFC 6238, HmacSHA1/256/512, ±window, 상수시간 비교) · `TotpSecretGenerator`(시크릿+otpauth:// URI).
  - **core**: `MfaMethod`(TOTP|OTP) · `MfaCrypto`(numericOtp/복구코드/SHA-256/상수시간 매칭) · **`MfaService`**(등록 시작·확정·OTP 등록·해제·상태·챌린지 생성·재발송·검증) · **`DefaultMfaGate`**(MfaGate 구현).
  - **store**: `MfaEnrollment(Store)`+`InMemory`/`Jdbc`(영속) · `PendingAuth`+`MfaChallengeStore`+`InMemory`/`Redis`(단기, 수기 직렬화). **otp**: `OtpSender` SPI(프로젝트 구현, 없으면 OTP 비활성·TOTP 는 동작).
  - **web**: `MfaEnrollmentController`(`/api/v1/mfa/**`, **인증 필요**: enroll/totp·confirm·otp·status·disable) · `MfaVerificationController`(`/api/v1/auth/mfa/**`, **permitAll**: verify·challenge/resend).
  - **config**: `MfaProperties` · `MfaAutoConfiguration`(3단 토글, 등록 store memory|jdbc / 챌린지 store memory|redis, 컨트롤러·게이트 빈). `imports` 등록 · `db/mfa-postgres.sql`(jdbc 시).
  - `build.gradle`: `api :framework-security` + `compileOnly` web/jdbc/data-redis. **새 라이브러리 0**.
- **등록/문서**: `settings.gradle` 에 `framework:framework-mfa` 추가 · `docs/FRAMEWORK_MODULES.md`(진행현황·카탈로그 2.6) · `HANDOFF.md`(1·6·7절). **STACK.md/libs.versions.toml/루트 build.gradle 무변경**(버전 0).

## 현재 상태 (적용/검증)
- 신규/변경 파일 모두 repo 반영. 정적 점검 통과(괄호 균형, 패키지=디렉터리, **Jackson2(databind/core) import 0**). MfaService/Properties/스토어 시그니처와 컨트롤러·게이트 호출 교차검증 완료.
- ⚠️ **실제 gradle 컴파일 미검증**(작성 환경 Maven Central 차단). 받는 쪽: `./gradlew :framework:framework-mfa:compileJava :framework:framework-security:compileJava` + `./gradlew spotlessApply`.
- 런타임 전제: 멀티 인스턴스는 **챌린지 store=redis 필수**(로그인 1·2단계가 다른 파드일 수 있음), 운영 등록 store=jdbc(`mfa-postgres.sql`). OTP 쓰려면 `OtpSender` 빈 등록 필요(미등록 시 TOTP 만).

## 켜는 법 (application.yml)
```yaml
framework:
  mfa:
    enabled: true
    policy: ENROLLED          # ENROLLED=등록자만 2차 인증(점진 도입) | OFF=강제 비활성
    issuer: "si-msa"          # otpauth 라벨
    totp: { enabled: true, algorithm: SHA1, digits: 6, period-seconds: 30, window: 1, recovery-codes: 10 }
    otp:  { enabled: false, length: 6, auto-send: true }     # OtpSender 빈 있을 때만
    challenge:  { ttl: PT5M, max-attempts: 5, store: { type: redis } }   # 멀티 인스턴스=redis
    enrollment: { store: { type: jdbc } }                                # 운영=jdbc
```
- 흐름: `POST /api/v1/auth/login` → (MFA 필요) `data=MfaTicket{ticket,methods,...}` → `POST /api/v1/auth/mfa/verify {ticket,method,code}` → `TokenResponse`. 등록: 인증 후 `POST /api/v1/mfa/enroll/totp` → `/confirm {code}`(복구코드 반환). 빈: `MfaService`·`MfaGate`(자동), OTP 발송은 프로젝트가 `OtpSender` 구현.

## 바로 다음 할 일 (Next)
1. 받는 쪽 컴파일 확인 + `spotlessApply`. 멀티 인스턴스면 challenge=redis·enrollment=jdbc 설정 + `mfa-postgres.sql` 적용. OTP 쓰면 `OtpSender`(framework-notification 연계 가능) 구현.
2. **다음 묶음 선택**: 규제특화 잔여(pki/hsm/recon/egov, 해당 사업만) 또는 **관측(observability)** — 분산추적은 core 에 micrometer-tracing-otel 보유, 메트릭/로그 표준화·대시보드가 후보.
   - (선택) idempotency 에 **JdbcIdempotencyStore** 추가(현재 memory/redis만).
3. 이후: 게이트웨이/k8s/CI-CD 멀티서비스화. (상세 `docs/FRAMEWORK_MODULES.md` 4절)

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **`MfaGate` 는 framework-security 의 nullable SPI** — `LoginService` 3번째 생성자(9-arg) 추가, **기존 5/8-arg 생성자 유지**(하위호환). `AuthController#login` 반환형이 `ApiResponse<Object>`(토큰 케이스 JSON 은 동일). MFA 빈 없으면 단일단계 그대로.
- **`LoginAuditListener` 실패 판정 일반화**: `==LOGIN_FAILURE` → `name().endsWith("FAILURE")`(MFA_FAILURE 포함, 미래 이벤트 대비).
- **MFA 경로 보안 경계**: 2단계 검증은 JWT 이전이라 `/api/v1/auth/mfa/**`(기존 permitAll `/api/*/auth/**` 에 포함, **SecurityAutoConfiguration 무수정**). 등록/관리는 `/api/v1/mfa/**`(인증). 현재 사용자=`CurrentUserProvider.getCurrentUser()`=JWT subject.
- **챌린지 store 는 로그인 1·2단계 사이 단기 상태** → 인메모리는 멀티 파드에서 무력 → **멀티 인스턴스 redis 필수**. 등록은 영속 → jdbc.
- **새 외부 의존성 0 유지**: TOTP/Base32/HOTP/복구코드 전부 JDK 자체 구현(googleauth/zxing/commons-codec 불요). 인프라성 JSON 은 수기 직렬화(`RedisMfaChallengeStore` Base64 수기, Jackson 미사용).
- (기존) 새 모듈/변경은 `settings.gradle`/`imports` 등록 · BOM 밖 새 라이브러리만 카탈로그 핀(이번 0) · `IdempotencyStore.remove` SPI · SXSSF `close()` · Batch6 패키지 이동 & JobLauncher→JobOperator · Spring7 메일 jakarta.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규: `framework/framework-<X>/`(config Properties+AutoConfiguration · 도메인 패키지 · imports FQCN). 확장: 기존 모듈에 패키지/빈 추가 + imports 에 새 autoconfig 줄 추가.
2. `build.gradle`: 능력 전이=`api`, 내부구현=`implementation`, 호스트/선택 의존=`compileOnly`(+test 에 동일 모듈 추가). **BOM 밖 새 라이브러리만** 카탈로그+ext 핀.
3. `settings.gradle`(신규 모듈) / `imports`(새 autoconfig) 등록 잊지 말 것.
4. 코드 전 **Boot4/Spring7/Jackson3 + 외부 라이브러리 API 를 공식 소스(GitHub raw)로 확정**(메이저 버전업=패키지 이동 잦음, 컴파일 미검증 환경).
5. 오토컨피그: `@AutoConfiguration(afterName=관련 Boot/프레임워크 autoconfig)` + `@ConditionalOnClass/Property` + 빈 `@ConditionalOnMissingBean`. 선택 의존 모듈 연계는 `@ConditionalOnClass(타 모듈 마커)`+`@ConditionalOnBean`(+compileOnly). 교체형 SPI 는 `@ConditionalOnMissingBean(타입)` 으로 기본구현.
6. 검증: `./gradlew :...:compileJava` (+`spotlessApply`).
7. 드롭인: 변경 파일 전부(모듈 폴더 + 변경된 기존 파일 + `settings.gradle`/`imports`/필요 시 카탈로그·문서) → 한 zip, 루트에서 `unzip -o`.


<!-- 갱신 끝 -->
