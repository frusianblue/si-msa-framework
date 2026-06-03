# BASELINE_FEATURES.md — 기본기능 카탈로그 / 갭 체크 (지속 갱신)

> SI/MSA 공통 프레임워크가 **갖춰야 할 기본기능**을 한 곳에 모은 체크리스트다.
> 상태(✅ 있음 / 🟡 부분 / 🔴 없음)와 **실제 구현 위치**를 함께 적어, 갭을 추적하고 추가 항목을 쌓아간다.
> 항목 추가는 §6 "추가 요청 대기열" 에 먼저 적고, 착수 시 본문 표로 승격한다.
> 최종 점검: 2026-06-03 (실측 — 레포 코드 대조)

---

## 1. 상태 요약 (한눈에)

| # | 기본기능 | 상태 | 모듈/위치 |
|---|----------|------|-----------|
| 1 | 회복탄력성(서킷/재시도/타임아웃) | ✅ | `framework-client` |
| 2 | 표준 페이징/정렬 응답 | ✅ | `framework-core` `core/page` |
| 3 | PDF 산출물 | ✅ | `framework-pdf` |
| 4 | 리포트 생성 | 🟡 | `framework-pdf` + `framework-excel` (통합 추상화는 없음 — 분리 보류 권장) |
| 5 | 멀티테넌시 / 요청 컨텍스트 | ✅ | `framework-context` |
| 6 | 분산 캐시 추상화 | ✅ | `framework-cache-redis` |
| 7 | 이미지 처리(썸네일/리사이즈/EXIF) | ✅ | `framework-image` |
| 8 | 대용량/스트리밍 · S3 presigned | ✅ | `framework-file*` 확장 (Range·presigned) |
| 9 | 이미지/문서 파일 메타 정합성 | ✅ | `framework-file` Tika 검출 + 확장자↔MIME 정합 |
| 10 | 안티바이러스 훅 | ✅ | `framework-file` `scan/` (ClamAV INSTREAM) |

> 범례 — ✅ 있음 · 🟡 부분(보강 필요) · 🔴 없음 · 🔧 작업 중

---

## 2. 항목별 상세 + 인수 기준(Acceptance)

### 1) 회복탄력성 ✅
- 위치: `framework-client` — `CircuitBreaker`, `RetryInterceptor`, `CircuitBreakerInterceptor`, 타임아웃(`ClientProperties`), 연계로그/트레이스 전파 인터셉터.
- 범위: **아웃바운드 HTTP 호출** 한정. 인바운드 bulkhead/동시성 제한은 미포함.
- 남은 보강(선택): 게이트웨이 rate-limit(429) 런타임 검증 · 서버측 동시성/격벽.

### 2) 표준 페이징/정렬 응답 ✅
- 위치: `core/page` — `PageRequest`, `PageResponse`, `SearchCondition`.
- 응답 봉투는 `ApiResponse<PageResponse<T>>` 로 일관.

### 3) PDF ✅
- 위치: `framework-pdf` — OpenPDF, 한글 TTF 임베딩, `PdfReport` 모델(표 기반 거래내역서/통지서).

### 4) 리포트 생성 🟡
- 현황: PDF(`PdfReport`) + Excel(`framework-excel`)로 산출은 가능하나, "데이터→템플릿→다중포맷(PDF/Excel/CSV)" 단일 추상화는 없음.
- 판단: **별도 모듈 분리 보류 권장.** 멀티포맷 리포트 오케스트레이션이 실제로 필요할 때만 신설.
- 인수 기준(필요 시): 동일 데이터모델 1개 → PDF/Excel/CSV 셋 다 같은 진입점으로 산출.

### 5) 멀티테넌시 / 요청 컨텍스트 ✅ (`framework-context`, 완료 — 사용자 환경 빌드 통과)
- 위치: `framework-context` — `RequestContext`(불변·빌더: tenantId/userId/locale + 확장 attributes) · `ContextHolder`(정적 `ThreadLocal`, **상속형 아님**, get 은 null 아님=EMPTY) · `ContextResolver`(SPI)+`HeaderContextResolver`(기본) · `ContextBindingFilter`(바인딩+MDC, `@Order(HIGHEST_PRECEDENCE+10)`) · `ContextTaskDecorator`(@Async/풀 전파) · `ContextPropagationInterceptor`(아웃바운드 헤더 전파).
- 충족: 요청별 바인딩/정리(예외 포함 clear) · `@Async` 컨텍스트+MDC 전파 · 아웃바운드 헤더 전파 · 해소 전략 교체 가능(`@ConditionalOnMissingBean`) · 3단 토글 기본 off · 신규 외부 의존성 0.
- 비고: 테넌트 **필수** 검증은 서비스 레이어에서 `ContextHolder.get().hasTenant()`(필터는 `GlobalExceptionHandler` 밖). 헤더 위조 방지는 게이트웨이/인증 책임.

### 6) 분산 캐시 추상화 ✅
- 위치: `framework-cache-redis` — core Caffeine 로컬 캐시를 파드 간 Redis 공유 캐시로 대체(`@AutoConfiguration(before=CacheAutoConfiguration)`).

### 7) 이미지 처리 ✅ (`framework-image`, 완료)
- 위치: `framework-image` — `ImageProcessor`(SPI: `process`/`thumbnail`/`probe`) + `DefaultImageProcessor`(JDK `javax.imageio`+`java.awt`). `ResizeSpec`(record, builder/`thumbnail` 팩토리) · `ImageFormat`(JPEG/PNG 화이트리스트) · `ExifOrientation`(순수 JDK JPEG APP1/TIFF 파서) · `ImageInfo` · `ImageErrorCode`.
- 충족: 비율유지 리사이즈/썸네일(상한 박스, 업스케일 옵트인, 2배 초과는 단계적 절반축소) · EXIF orientation 보정(1~8, AffineTransform, 5~8 가로세로 스왑) · **민감 EXIF(GPS) 제거**(디코드→리인코딩 부수효과로 메타 미보존) · 출력 포맷 화이트리스트(JPEG/PNG) · 디컴프레션 폭탄 방지(디코드 전 헤더 픽셀수 검사, 기본 40MP) · JPEG 알파 흰배경 평탄화 · 헤드리스 동작 · 3단 토글 기본 off · **신규 외부 의존성 0**.
- 비고: 메타 제거는 별도 strip 단계가 아니라 리인코딩의 결과 → `process()` 출력은 항상 메타 없음(원본 보존 경로는 별도). PNG 등 orientation 없는 포맷/APP1 부재·파싱 실패 시 NORMAL(원본 유지) best-effort. 웹 비의존(배치 사용 가능).

### 8) 대용량/스트리밍 · S3 presigned ✅ (`framework-file*` 확장, 완료)
- 위치: `framework-file` `storage/` — capability 인터페이스 `RangeReadableStorage`(`loadRange`/`contentLength`) · `PresignedUrlStorage`(`presignGet`/`presignPut`) · `ByteRange`(RFC 7233 단일범위 파서) · `PresignedUrl`(record). `FileSystemFileStorage` 가 Range 구현(`SeekableByteChannel`+길이 제한 스트림), `S3FileStorage` 가 Range(GetObject range)+presigned(`S3Presigner`) 구현. `FileController` GET `/{id}` 가 Range 206/416 처리, `/{id}/presigned`·`/presigned-upload`·`/presigned-complete` 추가.
- 충족: HTTP Range 스트리밍 다운로드(206 Partial·Content-Range, 만족불가 416) · presigned PUT/GET 발급(만료·PUT Content-Type 강제) · **대용량은 presigned PUT 으로 클라이언트가 S3 직행**(서버 바이트 비경유) · 저장은 스트리밍 복사(메모리 비적재). 기존 `FileStorage` 불변(ISP capability 추가) · 신규 외부 의존성 0(`S3Presigner` 는 awssdk:s3 포함).
- 비고: **암호화(AES-CBC) 저장소는 Range 미지원**(임의오프셋 복호화 불가) → `EncryptingFileStorage` 가 capability 미구현 → 컨트롤러가 전체 다운로드로 자동 폴백. 서버측 S3 멀티파트 병렬 업로드(TransferManager)는 백로그(presigned 로 인수기준 충족).

### 9) 이미지/문서 파일 메타 정합성 ✅ (`framework-file`, 완료)
- 위치: `framework-file` `validator/` — `ExtensionContentTypePolicy`(확장자→허용 MIME 계열 집합, 순수 JDK) + `TikaFileContentTypeValidator` 확장(정책 주입 오버로드). 토글 `framework.file.validation.enforce-extension-match=true`(기본 false, content-type-detection 필요).
- 충족: 선언 확장자↔검출 MIME **계열 정합** 강제 → .png 인데 PDF/zip/실행파일이면 거부 · 매직넘버 차단목록(기존, 위장 exe/jsp) 유지 · 위반 시 표준 `BusinessException(INVALID_INPUT)`.
- 비고: tika-core 매직넘버는 컨테이너(zip/OLE2)까지만 정확 → docx↔xlsx 미세구분은 통과(계열 단위, 오탐 방지). 규칙 없는 확장자는 1차 화이트리스트가 거른 뒤이므로 통과. (EXIF 정합/제거는 #7 `framework-image` 가 담당.)

### 10) 안티바이러스 훅 ✅ (`framework-file` `scan/`, 완료)
- 위치: `framework-file` `scan/` — `FileScanner` SPI + `ScanResult`(record) + `NoOpFileScanner`(기본) + `ClamavFileScanner`(ClamAV INSTREAM, **순수 JDK 소켓**). `FileService.upload` 가 저장 전 스캔 게이트. 토글 `framework.file.scan.enabled=true`·`type=clamav`.
- 충족: `FileScanner` SPI(no-op 기본) · ClamAV INSTREAM 어댑터(청크 프레이밍, 외부 의존성 0) · 저장 전 스캔 게이트 · 감염 시 거부(`BusinessException`)+감사로그(`@AuditLog FILE_UPLOAD`) · **fail-closed 기본**(데몬 장애 시 거부), `fail-open` 옵션.
- 비고: presigned PUT 업로드는 서버 본문 비경유 → 콘텐츠/AV 검증 미적용(파일명 확장자만). 신뢰경계 밖이면 비동기 후처리 스캔 별도.

---

## 3. 우선순위(현재 합의)
1. ~~#5 요청 컨텍스트/멀티테넌시~~ — ✅ 완료(`framework-context`).
2. ~~#7 이미지 처리~~ — ✅ 완료(`framework-image`).
3. ~~#8+#9+#10 파일 하드닝 묶음~~ — ✅ 완료(`framework-file*` 확장).
4. #4 리포트는 보류(분리 필요성 낮음).

## 4. 설계 원칙(이 카탈로그 항목 모두 공통)
- 선택형 모듈 = **3단 토글, 기본 off**(`framework.<module>.enabled=false`).
- 능력전이=`api`, 호스트/선택 라이브러리=`compileOnly`(+테스트 재선언), BOM 밖=`implementation` 비노출.
- **Jackson 3(`tools.jackson.*`)** 만. `com.fasterxml.jackson.*` 금지(annotation 만 예외).
- 새 오토컨피그는 `.imports` 등록 + **등록 가드 테스트**.
- 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증.
- 필터/컨버터 등 컨테이너 밖 컴포넌트는 `GlobalExceptionHandler`/DI 밖 — 직접 처리 또는 정적 다리.

## 5. 완료/적용 로그
- 2026-06-03: 카탈로그 신설 + 10항목 실측 체크. #5 `framework-context` 착수.
- 2026-06-03: #5 `framework-context` 완료(사용자 환경 빌드 통과).
- 2026-06-03: **#7 `framework-image` 완료** — ImageIO 기반 리사이즈/썸네일·EXIF orientation 보정·메타(GPS) 제거·디컴프레션 폭탄 방지, 신규 외부 의존성 0. 엔진 javac 단독 + 기능 하니스 26/26 통과, config/배선은 context·pdf 패턴 미러(사용자 Gradle 검증 예정).
- 2026-06-03: **#8+#9+#10 파일 하드닝 묶음 완료**(`framework-file*` 확장) — Range 206 스트리밍 다운로드 + S3 presigned PUT/GET(대용량 직행) · 확장자↔MIME 계열 정합(Tika 확장) · ClamAV INSTREAM AV 게이트(순수 소켓, 외부 의존성 0). 기존 `FileStorage` 불변(ISP capability 추가). 순수 JDK 코어 javac+하니스 35/35 통과(ByteRange 파서·확장자 정책·ClamAV mock 소켓 왕복·FileSystem Range), 정식 JUnit 6종 추가. **사용자 환경 빌드/테스트 통과 확인** — `:framework:framework-file:test :framework:framework-file-s3:test :framework:framework-archtest:test spotlessApply` 그린(S3 오토컨피그 테스트는 신규 `S3Presigner` 빈에 맞춰 mock 추가로 수정). image deprecation(PAYLOAD_TOO_LARGE→CONTENT_TOO_LARGE) 동봉.
- 2026-06-03: **환경정비 + 보안·검증 + spotless 확장** — 프로파일 local/dev/prod 통일 + `local-xx` 오버레이, 감사 로그 DB 적재 활성화(`audit_log` 마이그레이션 추가), JWT 시크릿 prod 가드, 요청 검증 빈틈 보강(Spring7 `HandlerMethodValidationException`·로그인 `@Valid`), spotless 다소스 확장 + 설정 캐시 충돌 해결(`lineEndings=UNIX`). 문서: `LOCAL_SETUP`/`CHANGES_AND_DEPRECATIONS`/`SECURITY_VALIDATION_ADDITIONS`/`SPOTLESS_NOTES`. 신규 의존성 0(감사/Redis 만 모듈 의존 1줄 추가 필요).
- ✅ 2026-06-03: **설정값(YAML) 패스워드 암호화** 완료. `framework-core` 의 커스텀 Boot4 `EncryptedPropertyEnvironmentPostProcessor` 가 `ENC(...)` 프로퍼티를 기존 `AesCryptoService`(AES-GCM)로 **지연 복호화**(마스터키 `framework.crypto.aes-secret`/`AES_SECRET`). 토글 `framework.crypto.config-decryption.enabled`(기본 on, ENC 없으면 무동작). 토큰 생성 CLI `CryptoCli`. prod 마스터키 가드 `AesMasterKeySafetyGuard`. **Jasypt 미도입·신규 의존성 0·Jackson 무관**. 설계서 `docs/NEXT_YAML_PASSWORD_ENCRYPTION.md`.

## 6. 추가 요청 대기열 (여기에 먼저 적어주세요 — 착수 시 위 표로 승격)
- [ ] 아카이빙(Archiving) 또는 압축(Archiving)
- [ ] 일괄 처리(Batch Processing) - 여러 개의 파일에 대해 똑같은 작업(이름 변경, 변환 등)을 한꺼번에 적용
