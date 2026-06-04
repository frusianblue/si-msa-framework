> 🗄️ **보관용(ARCHIVED) — 이 기능은 완료됨.** 이 문서는 설계 근거를 위한 히스토리 기록이며 현행 작업 대상이 아니다. 현재 카탈로그는 `docs/FRAMEWORK_MODULES.md`, 다음 작업은 `docs/NEXT_SSO.md`.

# NEXT_FILE_BATCH_PROCESSING.md — 다음 세션 작업 설계서 (파일 일괄처리)

> 목적: 다음 세션 최우선 작업인 **파일 일괄처리(Batch File Processing)** 를 막힘 없이 진행하기 위한 결정·설계·함정·체크리스트.
> 결론 한 줄: **얇은 오케스트레이션 모듈 `framework-file-batch`** 를 신설해, "같은 작업을 여러 파일에 한꺼번에" 적용한다.
> 실제 작업(이미지 변환·압축)은 기존 모듈(`framework-image`/`framework-archive`)에 위임 → **신규 외부 의존성 0**, Java 21 가상스레드로 병렬.

---

## 1. 무엇을 만들 것인가
여러 파일에 동일 작업을 한 번에:
- **이름 변경**(패턴/연번/접두·접미·정규식 치환)
- **변환**(이미지 리사이즈/썸네일/포맷 — `framework-image` 위임)
- **압축/해제**(여러 파일 → 1 zip, 또는 zip → 다수 — `framework-archive` 위임)
- **복사/이동**(스토리지 간)

핵심 가치 = (1) **부분 실패 격리**(한 파일 실패가 전체를 멈추지 않음, 결과 수집), (2) **병렬**(IO 바운드 → 가상스레드), (3) **드라이런**(미리보기), (4) **스트리밍**(파일별 처리, 전체 메모리 적재 금지).

---

## 2. 왜 이 방식인가 (결정 근거)
| 결정 | 근거 |
|---|---|
| **신규 얇은 모듈 `framework-file-batch`** | 일괄처리는 횡단 오케스트레이션 — 기존 image/archive/file 의 단건 기능을 "다건+병렬+결과수집"으로 감싼다. 기존 모듈 불변(ISP). |
| **작업은 SPI(`BatchFileOperation`)** | rename/transform/compress 를 교체·확장 가능하게. image/archive 는 **compileOnly**(없으면 해당 작업만 비활성, 모듈은 동작). |
| **Java 21 가상스레드 병렬** | 파일 IO 바운드 → `Executors.newVirtualThreadPerTaskExecutor()` 가 적합. 동시도 상한은 `Semaphore` 로(스토리지/디스크 보호). 새 의존성 0. |
| **부분 실패 = 수집 후 계속(기본)** | 배치 1건 실패로 전체 롤백은 보통 비현실적. `BatchResult` 에 성공/실패/스킵 + 실패 원인 수집. fail-fast 는 옵션. |
| commons-io / Spring Batch 재사용? | Spring Batch(`framework-batch`)는 "잡 실행/청크/재시작"용 — 파일 단위 변환 오케스트레이션과 결이 다름(과함). commons-io 는 새 의존성. **순수 JDK NIO + 기존 모듈 위임**으로 충분. |

---

## 3. 설계 (구현 가이드)

### 3.1 핵심 타입 (Spring 무의존 코어 → JDK 단독 검증)
```
com.company.framework.filebatch
├─ BatchFileOperation        // SPI: BatchItem apply(BatchItem) throws Exception
├─ BatchItem                 // record(sourcePath/targetName/스트림 supplier/메타)
├─ BatchResult               // 집계: total/succeeded/failed/skipped + List<ItemOutcome>
├─ ItemOutcome               // record(item, status=OK|FAILED|SKIPPED, errorCode?, message?)
├─ FileBatchProcessor        // 오케스트레이터: run(items, op, options) -> BatchResult
├─ BatchOptions              // record(parallelism, continueOnError(기본 true), dryRun)
├─ ops/
│   ├─ RenameOperation       // 패턴/연번/정규식 치환 (순수 JDK) — 충돌 검출
│   ├─ ImageTransformOperation// framework-image 위임 (compileOnly)
│   └─ CompressOperation     // framework-archive 위임 (compileOnly) — 다수→1 zip
├─ FileBatchErrorCode        // FBAT****
└─ config/
    ├─ FileBatchProperties   // framework.file-batch.enabled(기본 false) + default-parallelism 등
    └─ FileBatchAutoConfiguration
```

### 3.2 오케스트레이터
- `FileBatchProcessor.run(List<BatchItem> items, BatchFileOperation op, BatchOptions opts)`:
  1. `dryRun` 이면 각 item 에 대해 op 의 **계획만**(예: 바뀔 이름) 산출하고 실제 IO 없이 `BatchResult` 반환.
  2. 병렬: `try (var exec = Executors.newVirtualThreadPerTaskExecutor())` + `Semaphore(parallelism)` 로 동시도 제한. 각 작업을 submit.
  3. 각 item: `op.apply(item)` 성공→OK, 예외→(continueOnError? FAILED 수집하고 계속 : 즉시 중단). `BusinessException` 의 ErrorCode 보존.
  4. 결과 집계해 `BatchResult` 반환. 순서 보존(결과는 입력 순서로 정렬).
- **메모리 안전**: BatchItem 본문은 스트림 supplier(아카이브 `ArchiveEntry.ContentSupplier` 와 같은 결) — 파일별로 열고 닫는다.

### 3.3 작업(operation) 위임
- `ImageTransformOperation` 은 `com.company.framework.image.ImageProcessor` 를 주입받아 변환(모듈이 클래스패스에 있을 때만 빈 등록 — `@ConditionalOnClass(ImageProcessor.class)`).
- `CompressOperation` 은 `com.company.framework.archive.Archiver` 위임(`@ConditionalOnClass(Archiver.class)`).
- 둘 다 **compileOnly** → 의존 서비스가 image/archive 를 안 켜면 해당 op 만 빠지고 rename/copy 는 그대로.

### 3.4 안전
- **대상 경로**: rename/이동의 targetName 은 `ArchiveSafety.sanitizeEntryName`(또는 secure-web `PathSupport`) 재사용 — 경로조작 방지.
- **이름 충돌**: rename 시 결과 이름 중복이면 충돌 검출(FAILED 또는 연번 부여 정책). dryRun 으로 미리 확인 권장.
- **동시도 상한**: 무제한 가상스레드가 디스크/스토리지를 폭주시키지 않게 `Semaphore` 필수.

---

## 4. 함정 (미리 박아둠)
- **가상스레드 + try-with-resources**: `newVirtualThreadPerTaskExecutor()` 는 `close()` 가 **모든 작업 완료까지 블록**(AutoCloseable) → try-with-resources 로 감싸면 join 이 자연스럽다. 단 `Future` 결과는 close 전에 수집.
- **부분 실패 의미**: 기본 continueOnError=true. 호출자가 `BatchResult.failed()` 를 반드시 확인하도록 — 조용히 성공으로 오인 금지(`hasFailures()` 제공).
- **순서**: 병렬이라 완료 순서 ≠ 입력 순서 → 결과는 입력 인덱스로 재정렬해 반환.
- **이미지/아카이브 미존재**: image/archive op 빈은 `@ConditionalOnClass` 로 백오프 — 미존재 시 그 op 만 빠짐(모듈/rename 은 동작). 오토컨피그 introspection 함정 주의 → 테스트에 image/archive 를 `testImplementation` 재선언.
- **스트림 소유권**: op 는 받은 스트림을 닫고, 오케스트레이터는 supplier 로 매번 새로 연다(archive 패턴 일치).
- **dryRun 은 IO 0**: 파일 읽기/쓰기 금지, 계획만. 테스트로 못박기.
- **새 의존성 0 유지**: commons-io/Spring Batch 끌어오지 말 것. NIO(`Files`)+기존 모듈 위임만.
- (지난·유효) 3단 토글 기본 off · `.imports`+등록가드 · 새 모듈은 settings include + archtest testImplementation · Jackson 무관.

## 5. 테스트 계획
- `RenameOperation` 순수 JDK 단위(패턴/연번/정규식/충돌).
- `FileBatchProcessor` — 가짜 op 로: 전부 성공 / 일부 실패 + continueOnError / fail-fast / dryRun(IO 0) / 순서 보존 / 병렬 동시도 상한.
- `ImageTransformOperation`·`CompressOperation` — 실제 ImageProcessor/Archiver(또는 mock) 위임 1케이스씩.
- `FileBatchAutoConfigurationTest` — 토글 on/off + 등록가드 + image/archive 미존재 백오프.
- 가능하면 `@TempDir` 로 실제 파일 일괄 rename/compress 통합 1건.

## 6. 착수 체크리스트 (다음 세션 그대로 실행)
1. `framework-file-batch` 모듈 신설(build.gradle: `api framework-core`, `compileOnly framework-image`/`framework-archive`, +test 재선언).
2. 코어 타입(BatchItem/BatchResult/ItemOutcome/BatchOptions/BatchFileOperation) — Spring 무의존.
3. `FileBatchProcessor`(가상스레드+Semaphore+결과수집+dryRun+순서보존).
4. ops: Rename(JDK) / ImageTransform(@ConditionalOnClass) / Compress(@ConditionalOnClass).
5. `FileBatchErrorCode`(FBAT****) + config(Properties/AutoConfiguration) + `.imports`.
6. settings include + archtest testImplementation 추가.
7. 테스트(코어 단위 + 오케스트레이터 + 오토컨피그 토글/백오프/등록가드).
8. 문서 동기화: BASELINE(#? 또는 §6 승격)·FRAMEWORK_MODULES·STACK·README·HANDOFF(§6/§7)·HANDOFF_SUMMARY.
9. 검증: `./gradlew :framework:framework-file-batch:test :framework:framework-archtest:test spotlessApply` + (가능하면) `@TempDir` 실파일 일괄 처리.
