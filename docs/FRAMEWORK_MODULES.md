# FRAMEWORK_MODULES.md — 전 기능 On/Off 모듈 설계서 (금융 우선)

> 목적: 프레임워크의 **모든 기능을 일관된 규약으로 켜고 끄도록** 모듈을 분해하고,
> 공공·금융·일반 SI를 같은 코어로 커버한다. 기존 저장소의 오토컨피그 패턴(`@AutoConfiguration` + `.imports` +
> `@ConditionalOnProperty` + `@ConditionalOnMissingBean`)을 그대로 확장한다.
> 기준일: 2026-05-31

---

## 0. 진행 현황 (2026-06-02)
- ✅ **토대**: framework-idempotency · framework-i18n · framework-idgen · framework-client (선택형, 3단 토글 적용)
- ✅ **보안 완성(ISMS-P)**: framework-security 확장(비번 만료/이력·동시로그인) · framework-audit(접속/감사 로그 적재·조회, logging|jdbc|kafka)
- ✅ **framework-secure-web**: 보안헤더·경로조작 차단·인젝션 스크리닝·CSRF 더블서브밋(필터 계층, XSS 본문은 core)
- ✅ **금융 핵심**: framework-datasource(읽기/쓰기 분리 라우팅) · framework-messaging(Transactional Outbox + Kafka 릴레이 **+ 소비자측 멱등 소비**, `x-event-id`↔framework-idempotency) · audit↔messaging 연동(`store.type=kafka`)
- ✅ **업무 생산성**: framework-excel(POI 스트리밍/양식검증) · framework-batch(Batch6+Quartz) · framework-notification(메일/SMS/알림톡)
- ✅ **규제특화 시작**: framework-mfa(2단계 인증 — TOTP/OTP + ISMS-P 복구코드, **외부 의존성 0개**, security 로그인 흐름에 `MfaGate` SPI 로 연결)
- ✅ **SI 공통 유틸 보강(2026-06-02)**: `framework-core/util` 에 검증(`KoreanRegNoUtils`·`ValidationUtils`)·날짜/영업일(`DateUtils`·`HolidayUtils`)·금액(`MoneyUtils`)·한글(`HangulUtils`)·해시(`HashUtils`)·JSON(`JsonUtils`) 신규 + `MaskingUtils` 확장. 빈/오토컨피그 없는 순수 정적, **새 외부 의존성 0**(JSON 만 Jackson 3). 회귀 테스트 `CoreUtilsTest`. + **빌드 인프라 픽스**: 루트 `subprojects` 에 `testRuntimeOnly junit-platform-launcher` 추가(Gradle 9 에서 테스트 발견 단계 실패 방지, 전 모듈 공통).
- ⏭️ **다음 후보**: 규제특화 잔여(pki/hsm/recon/egov, 해당 사업만) 또는 관측(observability — 분산추적은 core 에 보유, 메트릭/로그 표준화) · (선택) idempotency JdbcIdempotencyStore
- ℹ️ **DB 범위 정리(2026-05-31)**: 읽기/쓰기 분리(primary/replica)까지 완료. *서로 다른 독립 DB 다중 연결*(DB별 SqlSessionFactory/tx매니저/@MapperScan)은 **미구현 — 필요 시 추가**. 분산 원자성은 XA 대신 Outbox/Saga로.
- 표기: ✅ 구현완료 · ⏭️ 다음 · (무표기) 예정. 세션 단위 상세는 `HANDOFF_SUMMARY.md`.

---

## 1. 표준 토글 규약 (모든 모듈 공통 — 이 3단을 반드시 따른다)

| 단계 | 무엇을 켜고 끄나 | 메커니즘 | 효과 |
|---|---|---|---|
| **1단 · 모듈** | 기능 묶음 자체의 존재 | `settings.gradle` include + 프로젝트 `build.gradle` 의존성 추가 → 모듈의 오토컨피그가 `@ConditionalOnClass(마커클래스)` | 의존성을 안 넣으면 클래스 자체가 없어 비용 0 |
| **2단 · 기능** | 모듈 안 개별 기능 | `framework.<module>.enabled` 및 세부 플래그 + `@ConditionalOnProperty` | yml 한 줄로 on/off |
| **3단 · 구현 교체** | 같은 인터페이스의 구현 선택 | `framework.<module>.<x>.type = memory \| redis \| jdbc …` (상호배제) + `@ConditionalOnMissingBean` | 환경별 백엔드 교체, 프로젝트가 빈 등록 시 자동 양보 |

**기본값 규칙**
- **코어 모듈**(core/security/mybatis): 모듈은 항상 탑재, 기능 플래그 기본 `true`. 단 모든 기능에 `enabled` 플래그를 둬 **끌 수 있게** 한다.
- **선택형 모듈**: 의존성을 넣어도 `framework.<module>.enabled` 기본 `false` → **명시적으로 켜야** 동작(안전 기본값).

**오토컨피그 스켈레톤(모든 신규 모듈 동일)**
```java
@AutoConfiguration
@ConditionalOnClass(Xxx.class)                                  // 1단: 모듈 존재
@ConditionalOnProperty(prefix = "framework.xxx", name = "enabled", havingValue = "true")  // 2단
@EnableConfigurationProperties(XxxProperties.class)
public class XxxAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean                                   // 3단: 프로젝트 override 허용
    public XxxService xxxService(XxxProperties p) { ... }
}
```
등록: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 에 FQCN 한 줄.

---

## 2. 전체 모듈 카탈로그

분류 표기 — **[코어]** 항상 탑재(기능별 토글) · **[선택]** 의존성 추가형
규제 표기 — 공통(전부) · 공(공공) · 금(금융)

### 2.1 기존 (재사용)

| 모듈 | 책임 | 대표 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-core | 응답/에러/페이징/AOP/로깅/트레이스/XSS/캐시/AES + **SI 공통 유틸(`util`: 검증·마스킹·날짜/영업일·금액·한글·해시·JSON)** | `framework.core.{trace,httpLogging,xss,auditAspect,...}` (util 은 토글 없는 정적) | [코어] | 공통 |
| framework-mybatis | 감사필드·암호화 타입핸들러·CurrentUser | (코어 연동) | [코어] | 공통 |
| framework-security | 인증추상화·JWT·TokenStore·RBAC·비번정책·로그인잠금 | `framework.security.*` | [코어] | 공통 |
| framework-openapi | API 문서 | `framework.openapi.enabled` | [선택] | 공통 |
| framework-redis | Redis TokenStore/LoginAttempt | `...type=redis` | [선택] | 공통 |
| framework-commoncode | 공통코드 CRUD | `framework.commoncode.enabled` | [선택] | 공통 |
| framework-file / -s3 | 파일 저장(로컬/NAS/S3) | `framework.file.enabled`,`storage.type` | [선택] | 공통 |

### 2.2 신규 — 토대 (다른 기능의 전제, 먼저 깔 것)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ framework-i18n | MessageSource·에러메시지 외부화·다국어 | `framework.i18n.enabled` | [선택]→코어승격 권장 | 공통 |
| ✅ framework-idgen | 채번(Sequence/Table/Snowflake) | `framework.idgen.enabled` + `type` | [선택] | 공통 |
| ✅ framework-client | 외부 API 표준 클라이언트(타임아웃·재시도·서킷·연계로그) | `framework.client.enabled` | [선택] | 공통 |

### 2.3 신규 — 보안 완성 (ISMS-P·보안성 심의 대비, 공통 필수)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ framework-security (확장) | 비번 **만료·변경주기·이력(직전 N개 재사용 금지)** | `framework.security.password.{expiry,history}.enabled` | [코어] | 공통 |
| ✅ framework-security (확장) | **동시(중복) 로그인 제어** | `framework.security.concurrent-session.enabled` | [코어] | 공통 |
| ✅ framework-audit | 접속/감사 로그 **DB 적재·조회** 표준(현 AOP 영속화) + **kafka 싱크**(messaging Outbox 발행) | `framework.audit.enabled` + `store.type=logging\|jdbc\|kafka` | [선택] | 공통 |
| ✅ framework-secure-web | 보안헤더·경로조작·인젝션 스크리닝·CSRF 더블서브밋(SQLi 등, XSS는 core) | `framework.secure-web.enabled` (+`headers`/`path-traversal`/`injection`/`csrf`) | [선택] | 공통 |

### 2.4 신규 — 업무 생산성 (업무개발자 직접 사용)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ framework-excel | POI 업/다운로드: **다운로드 SXSSF 스트리밍**(대용량 일정메모리)·**업로드 양식검증**(헤더/타입/필수/길이·패턴, 행별 오류수집). POI 타입 비노출(implementation) | `framework.excel.enabled` (+`export.window-size`/`import.max-rows`) | [선택] | 공통 |
| ✅ framework-batch | **Spring Batch 6 실행**(JobLaunchSupport: JobOperator 래핑+run.id 재실행보장)·**표준 로깅 리스너**·**Quartz cron 스케줄**(yaml 선언만으로 Job 기동) | `framework.batch.enabled`,`framework.scheduler.enabled` | [선택] | 공통 |
| ✅ framework-notification | **메일/SMS/알림톡 채널 추상화** — NotificationService 가 ChannelType 라우팅. 메일=JavaMailSender, SMS·알림톡=벤더 SPI(SmsClient/AlimtalkClient)+기본 로깅구현(@ConditionalOnMissingBean 교체) | `framework.notification.enabled` + `channels.{mail,sms,alimtalk}.enabled` | [선택] | 공통 |

### 2.5 신규 — 데이터/연계 (금융 핵심 ★)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ **framework-idempotency** | **정확히-한번/멱등키**(중복요청·중복결제 차단) | `framework.idempotency.enabled` + `store.type=redis\|jdbc` | [선택] | 금 ★ |
| ✅ framework-messaging | Kafka + **Outbox**(발행: 유실/중복 방지) **+ 소비자측 멱등 소비**(`IdempotentEventProcessor`: `x-event-id` 헤더로 중복 배달 1회 처리, 실패 시 키 해제→재배달 재처리). 멱등 저장소는 framework-idempotency(redis 권장) | `framework.messaging.enabled`(+`outbox.relay.enabled`)·`framework.messaging.consumer.enabled` | [선택] | 금 ★ |
| ✅ framework-datasource | **읽기/쓰기 분리 라우팅**(primary/replica). *독립 다중 DB(별도 SqlSessionFactory/tx매니저)는 미구현 — 추후* | `framework.datasource.routing.enabled` | [선택] | 금/공 |
| framework-saga | 분산 트랜잭션/보상(Saga) | `framework.saga.enabled` | [선택] | 금 |

### 2.6 신규 — 규제 특화 (해당 사업만 켬)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-pki | GPKI/NPKI·전자서명·부인방지 | `framework.pki.enabled` | [선택] | 공/금 |
| ✅ framework-mfa | **2단계 인증(MFA)** — TOTP(RFC 6238)·OTP(SMS/메일/알림톡 `OtpSender` SPI)·**ISMS-P 일회용 복구코드**(SHA-256). security 로그인이 `MfaGate` SPI 로 2단계 분기(미사용 시 단일단계 그대로). 챌린지 저장소 memory\|redis(멀티 인스턴스는 redis 필수)·등록 저장소 memory\|jdbc. **외부 의존성 0개**(Base32/HOTP/TOTP/복구코드 전부 JDK) | `framework.mfa.enabled` + `policy=ENROLLED\|OFF`, `totp/otp.enabled`, `challenge/enrollment.store.type` | [선택] | 금/공 |
| framework-crypto-hsm | HSM 키관리(PKCS#11) | `framework.crypto.provider=hsm` | [선택] | 금 |
| framework-recon | 대사/정산 배치 | `framework.recon.enabled` | [선택] | 금 |
| framework-egov-compat | 전자정부 표준프레임워크 호환 어댑터 | `framework.egov.enabled` | [선택] | 공 |

### 2.7 신규 — 운영/관측

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-observability | 구조화(JSON) 로그·Micrometer 메트릭·OTel 트레이스 익스포터 | `framework.observability.enabled` | [선택] | 공통 |

---

## 3. 의존 관계 (요약)

```
core ──┬── mybatis ── (audit, datasource, commoncode, file)
       ├── security ──┬── redis(impl), audit(연동), mfa, pki
       │              └── idempotency(impl: redis/jdbc)
       ├── i18n            (모두가 메시지 사용)
       ├── idgen           (도메인 채번)
       ├── client ──── messaging/saga(연계)
       └── observability   (전 모듈 횡단)
```
원칙: 상위(토대)는 하위를 모른다. 순환 금지. impl 모듈(redis 등)이 추상(security/idempotency)을 의존.

---

## 4. 구축 순서 (금융 우선, 토대→심의→생산성→연계→규제→운영)

1. **토대** — framework-i18n, framework-idgen, framework-client
2. **보안 완성(심의)** — 비번 만료/이력, 동시로그인, framework-audit, framework-secure-web
3. **금융 핵심** — **framework-idempotency**, framework-messaging(+Outbox), framework-datasource
4. **업무 생산성** — framework-excel, framework-batch, framework-notification
5. **규제 특화** — framework-pki, framework-mfa, framework-crypto-hsm, framework-recon, (공공 시) framework-egov-compat
6. **운영/관측** — framework-observability
7. **그릇 정비** — 게이트웨이(폴백·CORS·rate-limit)·k8s(redis/secret/멀티서비스)·CI/CD 멀티서비스화

> 1·2단계 산출물은 3단계 이후 모든 모듈이 재사용한다(메시지·채번·연계·감사). 토대를 건너뛰면 각 모듈이 재발명한다.

---

## 5. 프로파일 프리셋 (사업유형별 일괄 on/off)

같은 코어 + yml 프리셋으로 사업유형을 전환한다.

```yaml
# application-finance.yml  (금융 풀세트)
framework:
  idempotency: { enabled: true,  store: { type: redis } }
  messaging:   { enabled: true, outbox: { relay: { enabled: true } } }
  datasource:  { routing: { enabled: true } }   # primary/replica 읽기·쓰기 분리
  audit:       { enabled: true,  store: { type: jdbc } }
  mfa:         { enabled: true }
  pki:         { enabled: true }
  security:    { password: { expiry: { enabled: true }, history: { enabled: true } },
                 concurrent-session: { enabled: true } }
```
```yaml
# application-public.yml  (공공)
framework:
  egov:  { enabled: true }
  pki:   { enabled: true }
  audit: { enabled: true, store: { type: jdbc } }
  secure-web: { enabled: true }
```
```yaml
# application-enterprise.yml  (일반 기업 라이트 — 규제 특화 off)
framework:
  idempotency: { enabled: true, store: { type: redis } }   # 중복요청 방지는 보편적
  audit:       { enabled: true, store: { type: jdbc } }
  # egov/pki/mfa/hsm/recon/saga: 미설정 → 기본 false → 꺼짐
```

레퍼런스 구현은 `framework/framework-idempotency/` (전 3단 토글 적용 예) 참고.
