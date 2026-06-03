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
| 8 | 대용량/스트리밍 · S3 presigned | 🔴 | (예정) `framework-file*` 확장 |
| 9 | 이미지/문서 파일 메타 정합성 | 🟡 | `framework-file` Tika 검출(부분) → 확장 필요 |
| 10 | 안티바이러스 훅 | 🔴 | (예정) `framework-file` AV 훅 |

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

### 8) 대용량/스트리밍 · S3 presigned 🔴 (예정: `framework-file*` 확장)
- 갭: `FileStorage` = `store/load/delete/type` 뿐. presigned URL · HTTP Range · 멀티파트 업/다운 없음.
- 인수 기준(초안): presigned PUT/GET 발급(만료·콘텐츠타입 제한) · HTTP Range 스트리밍 다운로드 · 대용량 멀티파트 업로드 · 직접 바이트 비경유(메모리 안전).

### 9) 이미지/문서 파일 메타 정합성 🟡 (보강: `framework-file`)
- 현황: Tika 매직넘버 검출 + 차단목록(위장 exe/jsp 방어) → 신뢰 contentType 반환.
- 갭: **선언 확장자↔실제 MIME 일치 강제(allowlist)**, 확장자 정규화, (이미지)EXIF 정합/제거, (문서)구조 검증은 없음.
- 인수 기준(초안): allowlist 미스매치 거부(.png 인데 실제 zip 등) · 확장자/콘텐츠타입/매직넘버 3자 교차검증 · 위반 시 표준 `BusinessException`.

### 10) 안티바이러스 훅 🔴 (예정: `framework-file` AV 훅)
- 갭: 업로드 본문 스캔 연동 포인트 없음.
- 인수 기준(초안): `FileScanner` SPI(no-op 기본) · ClamAV(INSTREAM) 어댑터(선택) · 저장 전 스캔 게이트 · 감염 판정 시 거부+감사로그. 새 외부 의존성은 어댑터를 별도 옵트인으로.

---

## 3. 우선순위(현재 합의)
1. ~~#5 요청 컨텍스트/멀티테넌시~~ — ✅ 완료(`framework-context`).
2. ~~#7 이미지 처리~~ — ✅ 완료(`framework-image`).
3. **#8+#9+#10 파일 하드닝 묶음** — `framework-file*` 표면 공유, 한 묶음 처리. ← 다음
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

## 6. 추가 요청 대기열 (여기에 먼저 적어주세요 — 착수 시 위 표로 승격)
> 형식: `- [ ] <기능명> — <왜 필요한지/기대 인수기준 한 줄>`

- [ ] 아카이빙(Archiving) 또는 압축(Archiving)
- [ ] 일괄 처리(Batch Processing) - 여러 개의 파일에 대해 똑같은 작업(이름 변경, 변환 등)을 한꺼번에 적용

- [ ] (예) 추가할 기본기능 1 — …
- [ ] (예) 추가할 기본기능 2 — …
