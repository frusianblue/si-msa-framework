# 모듈 조합 가이드 (Module Composition)

> **목적**: "내 프로젝트에 어떤 모듈을 켜고, 무엇이 무엇을 전제하며, 어떻게 추가하는가"를 한 장에서 결정한다.
> 모듈 **내부 설계**는 각 `framework/framework-*/README.md`, 전체 카탈로그 원본은 [`../FRAMEWORK_MODULES.md`](../FRAMEWORK_MODULES.md).

---

## 0. 공통 규약 — 모듈을 켜는 3단계

모든 선택 모듈은 **클래스패스 → 기능 플래그 → 구현 선택**의 3단 토글을 따른다.

1. **의존성 추가** (`build.gradle`): `implementation project(':framework:framework-<name>')`
   → 클래스패스에 들어오면 `@ConditionalOnClass` 가 깨어난다.
2. **기능 플래그** (`application.yml`): `framework.<name>.enabled=true`
   → `@ConditionalOnProperty` 로 실제 활성.
3. **구현 선택** (필요 시): `...store.type=redis|jdbc|memory` 등
   → `@ConditionalOnMissingBean` 으로 앱이 직접 구현하면 그게 우선.

`framework-core / -mybatis / -security` 3종은 **[코어]** 라 항상 탑재되고, 세부 기능만 플래그로 끈다.

표기 — **[코어]** 항상 / **[선택]** 의존성 추가형 / **[테스트]** 빌드 산출물 아님
규제 — 공통(전부) · 공(공공) · 금(금융)

---

## 1. 토대 — 항상 먼저 (다른 모듈이 전제로 깖)

| 모듈 | 핵심 기능 | 전제(의존) | 함께 쓰는 것 | 핵심 토글 |
|---|---|---|---|---|
| **framework-core** [코어] | 표준 응답`ApiResponse`/예외`GlobalExceptionHandler`·페이징·AOP·로깅·traceId·XSS·로컬캐시(Caffeine)·AES·**설정 암호화 `ENC(...)`**·**SI 공통 util**(검증/마스킹/날짜·영업일/금액/한글/해시/JSON/CSV/고정폭전문/CP949) | — (모든 것의 뿌리) | 전부 | `framework.core.{trace,httpLogging,xss,auditAspect}`, `framework.crypto.{enabled,aes-secret}` |
| **framework-mybatis** [코어] | 감사필드 자동주입·암호화 타입핸들러·`CurrentUser`·카멜케이스 | core | commoncode·file·audit·datasource | (core 연동, 토글 없음) |
| **framework-i18n** [선택·승격권장] | MessageSource·에러메시지 외부화·다국어 | core | 메시지 쓰는 전부 | `framework.i18n.enabled` |
| **framework-idgen** [선택] | 채번(Sequence/Table/Snowflake) | core | 도메인 PK/업무코드 | `framework.idgen.enabled` + `type` |
| **framework-client** [선택] | 외부 API 표준 클라(타임아웃·재시도·서킷·연계로그) | core | messaging·saga·oauth-client | `framework.client.enabled` |

> **왜 토대를 먼저**: 2단계 이후 모든 모듈이 i18n 메시지·idgen 채번·client 연계·core util 을 재사용한다. 건너뛰면 각 모듈이 재발명한다.

---

## 2. 보안 완성 — ISMS-P·보안성 심의 (공통 필수급)

| 모듈 | 핵심 기능 | 전제 | 함께 | 핵심 토글 |
|---|---|---|---|---|
| **framework-security** [코어] | JWT 무상태 인증·TokenStore·**DB기반 RBAC(동적 인가)**·메뉴관리·비번정책(강도/**만료/이력**)·로그인잠금·**동시세션 제어** | core | redis·audit·mfa·oauth-client·saml-sp·idempotency | `framework.security.*`, `...password.{expiry,history}.enabled`, `...concurrent-session.enabled` |
| **framework-redis** [선택] | Redis 기반 TokenStore / LoginAttempt | security | 멀티 인스턴스 인증 | `framework.security.token-store.type=redis` 등 |
| **framework-session** [선택] | 서버 세션 모드의 **Redis 세션 클러스터링**(Spring Session). 세션 모드 전환 자체는 코어(`security.session.mode=session`); 이 모듈은 멀티 인스턴스에서 세션 공유만 담당 | security(+spring-session-data-redis) | 세션 모드 멀티 인스턴스 | `framework.security.session.mode=session` + 모듈 추가(`framework.session.enabled` 기본 on) |
| **framework-audit** [선택] | 접속/감사 로그 표준 **DB 적재·조회** + Kafka 싱크 | core(+mybatis) | messaging(kafka 싱크 시) | `framework.audit.enabled` + `store.type=logging\|jdbc\|kafka` |
| **framework-secure-web** [선택] | 보안헤더·경로조작·인젝션 스크리닝·CSRF 더블서브밋(XSS는 core) | core | — | `framework.secure-web.enabled` |
| **framework-log-masking** [선택] | 로그 PII(주민/카드/휴대폰/이메일) 정규식 마스킹 → core `MaskingUtils` 위임, Logback `%mmsg` | core | observability(로그) | `framework.log-masking.enabled` |
| **framework-context** [선택·횡단] | 요청 컨텍스트/멀티테넌시(tenantId/userId/locale → ThreadLocal+MDC), @Async·아웃바운드 전파 | core | 전부(횡단) | `framework.context.enabled` |

---

## 3. 데이터·연계 — 금융 핵심 ★

| 모듈 | 핵심 기능 | 전제 | 함께 | 핵심 토글 |
|---|---|---|---|---|
| **framework-idempotency** ★ | 정확히-한번/멱등키(중복요청·결제 차단) + 응답 재생 | core | messaging·saga(멱등저장소 공유) | `framework.idempotency.enabled` + `store.type=memory\|redis\|jdbc` |
| **framework-messaging** ★ | Kafka + **Transactional Outbox**(유실/중복 방지 발행) + 멱등 소비 | core, **idempotency**(멱등저장소) | saga·audit(kafka 싱크) | `framework.messaging.enabled`(+`outbox.relay.enabled`), `...consumer.enabled` |
| **framework-datasource** | 읽기/쓰기 분리 라우팅(primary/replica) · 독립 다중 DB | core·mybatis | — | `framework.datasource.routing.enabled` **또는** `...multi.enabled` (상호배타) |
| **framework-saga** ★ | 경량 오케스트레이션 Saga(중앙상태 + 실패 시 역순 보상) | core, **messaging**(Outbox 재사용) | idempotency | `framework.saga.enabled`(+`recovery.enabled`) |

> ★ = 금융 우선. **연쇄 의존 주의**: saga → messaging → idempotency. saga 만 켜면 안 되고 셋을 함께 배선한다(messaging 은 compileOnly 비전이라 의존 서비스가 명시 추가).

---

## 4. 업무 생산성 — 업무개발자가 직접 호출

| 모듈 | 핵심 기능 | 전제 | 함께 | 핵심 토글 |
|---|---|---|---|---|
| **framework-excel** | POI 업/다운(다운=SXSSF 스트리밍·업=양식검증·행별 오류수집) | core | — | `framework.excel.enabled` |
| **framework-batch** | Spring Batch 6 실행 + 표준 리스너 + Quartz cron | core | lock(다중 파드 @Scheduled 시)·**task(실행이력)** | `framework.batch.enabled`, `framework.scheduler.enabled` |
| **framework-task** | Spring Cloud Task(Boot4) 실행이력 — run-once 작업 시작·종료·종료코드·파라미터 영속(`TASK_EXECUTION`) | core | **batch**(결합 시 Job↔Task 자동연결) | `framework.task.enabled` |
| **framework-notification** | 메일/SMS/알림톡 채널 추상화(벤더 SPI 교체) | core | mfa(OTP 발송) | `framework.notification.enabled` + `channels.{mail,sms,alimtalk}.enabled` |
| **framework-pdf** | PDF 산출물(거래내역서/통지서, 한글 TTF 임베딩) | core | — | `framework.pdf.enabled` |
| **framework-image** | 이미지 리사이즈/썸네일·EXIF 보정·GPS 제거(JDK ImageIO, 의존성 0) | core | file-batch(위임 대상) | `framework.image.enabled` |
| **framework-archive** | ZIP/GZIP 압축·zip-slip·압축폭탄 가드(java.util.zip, 의존성 0) | core | file-batch(위임 대상) | `framework.archive.enabled` |
| **framework-file-batch** | 여러 파일 동일작업 일괄(이름변경/변환/압축)·부분실패 격리·가상스레드 병렬 | core | **image·archive**(있으면 해당 op 활성, 없으면 백오프) | `framework.file-batch.enabled` |
| **framework-qr** | QR 생성(ZXing core 인코딩 + JDK 렌더링) | core | mfa(otpauth URI → QR) | `framework.qr.enabled` |
| **framework-commoncode** | 공통코드 CRUD + 캐시 무효화 | core·mybatis | — | `framework.commoncode.enabled` |
| **framework-file / -s3 / -sftp** | 파일 저장(로컬/NAS · S3 · SFTP) + 콘텐츠검증·at-rest 암호화·Range 스트리밍·AV스캔 | core | — | `framework.file.enabled`, `storage.type=local\|s3\|sftp` |
| **framework-openapi** | API 문서(springdoc) | core | — | `framework.openapi.enabled` |

### 4-1. 배치 실행 모델 두 가지 — 무엇을 켤까

| | **모델 A: framework-task (+batch)** | **모델 B: framework-batch (Quartz)** |
|---|---|---|
| 실행 형태 | **한 번 실행 후 종료**(run-once) | **상주 프로세스 안에서 cron 반복** |
| 누가 깨우나 | **외부 스케줄러**(k8s CronJob·Argo·Airflow) | **프로세스 내장 Quartz** cron |
| 핵심 가치 | 실행이력·종료코드·파라미터를 `TASK_EXECUTION` 에 영속 | yaml cron 선언만으로 Job 기동 |
| 종료코드 | **있음**(실패→exit≠0, CronJob `backoffLimit` 재시도) | 없음(프로세스 계속 떠 있음) |
| 모니터링 UI | (외부 스케줄러 UI / TASK_EXECUTION 조회) | **Quartz UI 연동 가능**(아래) |
| 토글 | `framework.task.enabled` (+ `framework.batch.enabled` 결합) | `framework.batch.enabled`, `framework.scheduler.enabled` |

> 결정 트리: **k8s/배치플랫폼이 깨우는가? → 모델 A**(컨테이너 종료코드로 성공·실패 판정, 클라우드 네이티브). **앱이 떠 있으면서 스스로 cron? → 모델 B**(레거시 스케줄 이식·단순 상주배치). 두 모델은 **배타가 아님** — A 의 작업 본문이 Batch6 Job 이면 task 가 그 Job 의 실행이력까지 함께 남긴다(`TASK_TASK_BATCH`). 실투입 예제는 `examples/batch-task-reference/`.

### 4-2. Quartz 모니터링 UI 정확 매핑

UI 는 **Quartz 스케줄러**를 들여다보는 도구라서 **모델 B(framework-batch)에만** 붙는다(모델 A 의 run-once 태스크엔 해당 없음). 전제: RAM JobStore 가 아니라 **JDBC JobStore + 클러스터**(`spring.quartz.job-store-type=jdbc`, `org.quartz.jobStore.isClustered=true`, PostgreSQL delegate, QRTZ_* 11테이블 — DDL `deploy/db/quartz/`).

| 도구 | 라이선스 | 본 스택(Boot4/Java21) | 비고 |
|---|---|---|---|
| **QuartzDesk** | **상용**(무료 *Lite* 만 기능제한) | 코드무변경 에이전트형 | 사용자 제안 — OSS 아님(정정) |
| **`fabioformosa/quartz-manager`** | **OSS(Apache)** | **호환**(Boot 3.5/4.0, REST+임베드 UI) | **OSS 우선 후보** |
| Quartzmin / CrystalQuartz / quartznet-admin | OSS | ✗ **Quartz.NET 전용** | 본 스택 부적합 |

> 상세(좌표·연동 절차·SCDF EOL 배경)는 **`docs/guide/BATCH_SCHEDULING_AND_UI.md`**.

---

## 5. 운영·관측

| 모듈 | 핵심 기능 | 전제 | 함께 | 핵심 토글 |
|---|---|---|---|---|
| **framework-observability** | 구조화(JSON) 로그·Micrometer 공통태그·OTel OTLP 익스포터 | core | log-masking·전 모듈(횡단) | `framework.observability.enabled` |
| **framework-lock** | 분산 락(memory/redis/jdbc) + `@SchedulerLock`(다중 파드 @Scheduled 중복방지) | core | batch·scheduler | `framework.lock.enabled` + `type` (+`scheduler.enabled`) |
| **framework-cache-redis** | 분산 캐시(core Caffeine 로컬캐시를 파드 간 Redis 로 대체) | core | core(@Cacheable) | `framework.cache.redis.enabled` |

---

## 6. 인증 확장 / 규제 특화 — 해당 사업만 켬

| 모듈 | 핵심 기능 | 전제 | 함께 | 토글·문서 |
|---|---|---|---|---|
| **framework-mfa** | 2단계 인증 TOTP·OTP(SMS/메일/알림톡)·ISMS-P 복구코드. security 로그인이 `MfaGate` 로 2단계 분기 | core·**security** | notification(OTP)·qr(otpauth) | `framework.mfa.enabled` + `policy`, `totp/otp.enabled` |
| **framework-oauth-client** | 소셜 로그인(google/kakao/naver) + OIDC RP → 자체 JWT 발급 | core·**security** | client·redis(state) | `framework.oauth-client.enabled` · [`OAUTH_CLIENT`](../modules/OAUTH_CLIENT.md)·[`OIDC_HARDENING`](../modules/OIDC_HARDENING.md) |
| **framework-saml-sp** | SAML 2.0 SP(외부 IdP 신원확인) → 자체 JWT. ⚠️ OpenSAML 전이(Shibboleth repo 필요) | core·**security** | redis(멀티파드 AuthnRequest) | `framework.saml-sp.enabled` · [`SAML_SP`](../modules/SAML_SP.md) |
| framework-pki *(예정)* | GPKI/NPKI·전자서명·부인방지 | security | — | `framework.pki.enabled` |
| framework-crypto-hsm *(예정)* | HSM 키관리(PKCS#11) | core | — | `framework.crypto.provider=hsm` |
| framework-recon *(예정)* | 대사/정산 배치 | batch | — | `framework.recon.enabled` |
| framework-egov-compat *(예정)* | 전자정부 표준프레임워크 호환 어댑터 | core | — | `framework.egov.enabled` |

> *(예정)* 4종은 카탈로그에만 있고 아직 모듈 디렉터리는 없음.

---

## 7. 테스트 전용 (배포 산출물 아님)

| 모듈 | 역할 |
|---|---|
| **framework-archtest** | ArchUnit — 모듈 순환금지·Jackson3 규약(`tools.jackson.*`만)·레이어 격리·네이밍·생성자주입 강제. **새 라이브러리 모듈 추가 시 `framework-archtest/build.gradle` 에 `testImplementation project(...)` 한 줄 추가 필수** (누락 시 검사 사각지대) |

---

## 8. 의존 관계 (한눈에)

```
core ──┬── mybatis ──── (audit, datasource, commoncode, file/-s3/-sftp)
       ├── security ──┬── redis(impl) · audit(연동)
       │              ├── mfa · oauth-client · saml-sp   (인증 확장)
       │              └── idempotency(impl: redis/jdbc)
       ├── i18n / idgen / client                          (토대)
       ├── idempotency ── messaging ── saga               (금융 연계 ★, 연쇄)
       ├── observability / log-masking                    (횡단)
       ├── lock ─── batch·scheduler                       (다중 파드 @Scheduled)
       ├── batch ─── task (결합 시 Job↔Task 실행이력)        (run-once 외부 스케줄)
       ├── cache-redis (core Caffeine 대체)
       └── excel / pdf / image / archive / file-batch / qr / notification (생산성)
```
원칙: 상위(토대)는 하위를 모른다 · 순환 금지 · impl 모듈(redis 등)이 추상(security)을 의존.

---

## 9. 내 프레임워크 조립 레시피 (3단계)

1. **항상 포함**: core · mybatis · security · i18n · idgen · secure-web · observability
   → SI 프로젝트 공통 바닥. 여기에 기능 플래그로 미세조정.
2. **사업 성격으로 가산**:
   - 금융이면 → idempotency + messaging + saga + audit(jdbc) + mfa (+ datasource 읽기/쓰기 분리)
   - 공공이면 → audit + mfa + saml-sp (+ egov-compat 예정)
   - 일반/B2C 이면 → oauth-client(소셜) + cache-redis + notification
3. **필요 기능만 추가**: excel/pdf/file/file-batch/qr/image/lock/commoncode 등.

> 사업유형 일괄 프리셋(`application-finance/public/enterprise.yml`)은 [`USAGE_BY_PROJECT_TYPE.md`](USAGE_BY_PROJECT_TYPE.md) 참조. 한 코어 + yml 프리셋 교체만으로 사업유형을 전환한다.

---

## 10. 자주 틀리는 연결 (함정)

- **saga 만 켜기** → 동작 안 함. messaging(+idempotency)을 함께 배선해야 한다.
- **멀티 인스턴스인데 `store.type=memory`** → 파드 간 공유 안 됨. idempotency/lock/mfa/oauth state 는 운영 시 `redis` 필수.
- **mfa/oauth-client/saml-sp 를 security 없이** → 셋 다 security 의 JWT 발급에 얹히는 구조. security 가 전제.
- **file-batch 의 변환/압축 op 가 안 보임** → image/archive 모듈을 같이 의존해야 해당 op 가 활성(없으면 rename 만 동작).
- **datasource routing 과 multi 동시 on** → 상호배타. 하나만.
- **task 만 켜고 실행이력이 안 남음** → `@EnableFrameworkTask`(=`@EnableTask`)가 부트클래스에 있어야 SCT 가 `TaskRepository`/리스너를 깬다(애너테이션 없으면 실행이력 자체가 시작 안 됨).
- **Quartz UI 가 안 붙음** → UI 는 **모델 B(framework-batch Quartz)** 전용이고 **JDBC JobStore + 클러스터**가 전제(기본 RAM JobStore 는 외부 도구가 볼 테이블이 없다). 모델 A(task run-once)엔 Quartz UI 개념이 없다.
- **QuartzDesk 를 OSS 로 오해** → 상용(무료 Lite 만 제한). 본 스택 OSS 후보는 `fabioformosa/quartz-manager`. (`docs/guide/BATCH_SCHEDULING_AND_UI.md`)
