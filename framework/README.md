# framework/ — 모듈 색인 (그룹별 한눈에)

> 이 폴더의 `framework-*` 모듈은 모두 **옵트인(필요한 것만 의존)** 이다. 개수가 많은 건 의도된 세분화 — 각 모듈 = 독립적으로 켜고 끄는 하나의 기능 단위(=하나의 선택적 의존성)다.
>
> - **빠르게 찾기**: 아래 그룹 표에서 모듈명을 누르면 해당 모듈 README(켜는 법/쓰는 법/실전 사용 예/끄는 법)로 간다.
> - **설계 원본·의존관계·구축순서**: [`../docs/FRAMEWORK_MODULES.md`](../docs/FRAMEWORK_MODULES.md)
> - **내 프로젝트에 뭘 켤지(조합)**: [`../docs/guide/MODULE_COMPOSITION.md`](../docs/guide/MODULE_COMPOSITION.md)
> - **문서 전체 진입점**: [`../docs/00_INDEX.md`](../docs/00_INDEX.md)
>
> ℹ️ 폴더는 평면(flat) 구조를 유지한다(`framework/framework-*`). 물리적 그룹 폴더 대신 **이 색인으로 그룹을 표현**한다 — Gradle 좌표(`:framework:framework-*`)·배선을 건드리지 않기 위함.

---

## 🧱 기반 (Foundation) — 거의 항상 까는 토대
| 모듈 | 한 줄 |
|---|---|
| [framework-core](framework-core/README.md) | 표준 응답·예외·암호화·XSS/HTTP 로깅·공통 유틸. **모든 모듈의 토대(사실상 항상 의존).** |
| [framework-context](framework-context/README.md) | 요청 컨텍스트/멀티테넌시(tenantId·userId·locale) 바인딩 + `@Async`·아웃바운드 전파 |
| [framework-i18n](framework-i18n/README.md) | 메시지 외부화/다국어(`messages*.properties`), 예외 메시지 자동 변환 |
| [framework-idgen](framework-idgen/README.md) | 공통 채번 — Snowflake ID · 업무코드(`ORD-yyyyMMdd-000123`) |
| [framework-commoncode](framework-commoncode/README.md) | 공통코드 관리(그룹·코드 CRUD + 조회 API) |
| [framework-openapi](framework-openapi/README.md) | API 문서 자동화(springdoc / Swagger UI) |

## 🔐 인증·보안 (Security & Auth)
| 모듈 | 한 줄 |
|---|---|
| [framework-security](framework-security/README.md) | 로그인·JWT/세션·RBAC·계정잠금·비번정책. **인증의 핵심(`Authenticator` 1개만 구현).** |
| [framework-session](framework-session/README.md) | 서버 세션을 Redis 로 외부화(클러스터 세션, `mode=session`) |
| [framework-mfa](framework-mfa/README.md) | 2단계 인증(TOTP/OTP + ISMS-P 복구코드, 외부 의존성 0) |
| [framework-oauth-client](framework-oauth-client/README.md) | 외부 IdP 소셜 로그인(OAuth2/OIDC **RP**) → 자체 JWT 발급 |
| [framework-saml-sp](framework-saml-sp/README.md) | SAML 2.0 **SP**(외부 SAML IdP) → 자체 JWT. ⚠️ OpenSAML 전이(Shibboleth repo) |
| [framework-secure-web](framework-secure-web/README.md) | 웹 보안 필터(보안헤더·경로조작·인젝션·CSRF·레이트리밋) |

## 🗄️ 데이터·영속 (Data & Persistence)
| 모듈 | 한 줄 |
|---|---|
| [framework-mybatis](framework-mybatis/README.md) | MyBatis 표준 — `BaseEntity` 감사필드 자동·암호화 타입핸들러·예외변환 |
| [framework-datasource](framework-datasource/README.md) | 읽기/쓰기 분리 라우팅 + 독립 다중 DB |
| [framework-redis](framework-redis/README.md) | security 백엔드(TokenStore·로그인실패 카운트)의 Redis 구현(투명) |
| [framework-cache-redis](framework-cache-redis/README.md) | 분산 캐시 — core 로컬 Caffeine 을 Redis 공유 캐시로 대체 |
| [framework-idempotency](framework-idempotency/README.md) | 멱등성(정확히-한번) — `@Idempotent` + `Idempotency-Key`(memory/jdbc/redis) |

## 🔗 이벤트·연계 (Messaging & Integration)
| 모듈 | 한 줄 |
|---|---|
| [framework-messaging](framework-messaging/README.md) | 신뢰성 발행 — Transactional Outbox + Kafka 릴레이 + 멱등 소비 |
| [framework-saga](framework-saga/README.md) | 분산 트랜잭션 오케스트레이션(Outbox 기반 커맨드/역순 보상) |
| [framework-client](framework-client/README.md) | 외부 API 표준 호출 — 타임아웃/재시도/서킷브레이커/연계로그/트레이스 전파 |
| [framework-notification](framework-notification/README.md) | 알림 채널 추상화(메일·SMS·알림톡) |

## ⏱️ 작업·스케줄 (Jobs & Scheduling)
| 모듈 | 한 줄 |
|---|---|
| [framework-batch](framework-batch/README.md) | 배치 — Spring Batch 실행/리스너 + Quartz cron 스케줄 |
| [framework-lock](framework-lock/README.md) | 분산 락 / 리더 선출 — `@SchedulerLock`(다중 파드 `@Scheduled` 중복 방지) |

## 📄 문서·파일·미디어 (Files & Documents)
| 모듈 | 한 줄 |
|---|---|
| [framework-file](framework-file/README.md) | 파일 업/다운로드(로컬·NAS, Range, 스캔·타입검증·암호화) — 스토리지 SPI 진입점 |
| [framework-file-s3](framework-file-s3/README.md) | S3 저장소 백엔드(+ presigned URL 직접 업/다운) |
| [framework-file-sftp](framework-file-sftp/README.md) | SFTP 원격 저장소(MINA SSHD, Range, 연결풀·키회전) |
| [framework-file-batch](framework-file-batch/README.md) | 파일 일괄처리(여러 파일 동일작업 + 부분실패 격리 + 가상스레드 병렬) |
| [framework-excel](framework-excel/README.md) | Excel 업/다운로드(POI 스트리밍 + 양식검증) |
| [framework-pdf](framework-pdf/README.md) | PDF 산출물(OpenPDF, 한글 TTF 임베딩, 표 기반 통지서/내역서) |
| [framework-image](framework-image/README.md) | 이미지 처리(리사이즈/썸네일 + EXIF 보정·민감 메타 제거) |
| [framework-qr](framework-qr/README.md) | QR 코드 생성(ZXing 인코딩 + JDK ImageIO 렌더) |
| [framework-archive](framework-archive/README.md) | 아카이빙/압축(ZIP 다중엔트리 + GZIP, zip-slip·압축폭탄 가드) |

## 📈 관측·운영 (Observability & Ops)
| 모듈 | 한 줄 |
|---|---|
| [framework-observability](framework-observability/README.md) | 공통 메트릭 태그 + 구조화(JSON) 로그 + OTel 익스포터(전부 토글) |
| [framework-audit](framework-audit/README.md) | 감사/접속 로그 표준 적재·조회(`logging`/`jdbc`/`kafka`) |
| [framework-log-masking](framework-log-masking/README.md) | 개인정보 로그 마스킹(자유텍스트 PII 정규식 → core 형식 위임) |

## 🧪 테스트·검증 (Build & Test)
| 모듈 | 한 줄 |
|---|---|
| [framework-archtest](framework-archtest/README.md) | ArchUnit 아키텍처/레이어/네이밍/순환 규칙 강제 **(테스트 전용·비배포 산출물)** |

---

## 새 모듈은 어디에? (그룹은 폴더가 아니라 이 색인의 행)

새 `framework-*` 모듈을 추가할 때:
1. `settings.gradle` 에 `include 'framework:framework-<name>'` 추가(한 줄 설명 주석 포함).
2. 루트 `build.gradle` 의 `jacocoAggregation` 목록에도 한 줄 추가(통합 커버리지 누락 방지 — [PITFALLS §1] 참고).
3. **이 파일(`framework/README.md`)의 알맞은 그룹 표에 한 행 추가** — 새 그룹이 정말 필요할 때만 섹션을 만든다(그룹 인플레이션 주의).
4. 모듈 `README.md` 는 표준 양식(켜는 법 → 쓰는 법 → 실전 사용 예 → 끄는 법 → 덮어쓰기 → 버전 관리).
5. 새 토글/엔드포인트면 [`../docs/FRAMEWORK_MODULES.md`](../docs/FRAMEWORK_MODULES.md) · [`../docs/guide/MODULE_COMPOSITION.md`](../docs/guide/MODULE_COMPOSITION.md) 동기화(문서 동반 규칙).

> 그룹 경계가 애매한 모듈(예: `openapi` = 기반/관측, `messaging`·`saga` = 데이터/연계)은 **하나의 그룹에만** 두고 가장 자주 떠올리는 쪽에 배치한다 — 중복 등재 금지(색인이 다시 비대해진다).
