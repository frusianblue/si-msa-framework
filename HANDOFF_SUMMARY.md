# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**파일 일괄처리 모듈 `framework-file-batch` 완료** — 기본기능 대기열 마지막 항목 소진. "같은 작업을 여러 파일에 한꺼번에"(이름변경/이미지변환/압축)를 감싸는 **얇은 오케스트레이션**: (1)부분실패 격리(continueOnError 기본·fail-fast 옵션) (2)Java21 가상스레드+Semaphore 동시도 상한 (3)드라이런(IO 0) (4)입력순서 보존. 교차검증(이름 충돌)은 `BatchPreflight` capability 로 IO 전 일괄. 작업 3종 `RenameOperation`(prefix/suffix/regex/sequence/template + 충돌 FAIL/SUFFIX)·`ImageTransformOperation`(framework-image 위임)·`CompressOperation`(framework-archive 파일별 gzip 위임). image/archive 는 **compileOnly + 중첩 @Configuration `@ConditionalOnClass`/`@ConditionalOnBean` 백오프** → **신규 외부 의존성 0**. 순수 로직(Processor/Rename/Safety) Spring 무의존 → JDK 단독 하니스 **27/27** + 위임(실 ZipArchiver gzip 라운드트립·모의 ImageProcessor) **10/10** 통과. 정식 JUnit 5종 추가, archtest 7규칙 정적 무충돌. **다음 세션 최우선 = (devops) CI 게이트 + 멀티모듈 jacoco 집계** 또는 그릇 정비(게이트웨이 런타임/k8s 멀티서비스).

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: 파일 일괄처리 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### framework-file-batch 신설 (의존성 0, image/archive 위임)
- **오케스트레이터 `FileBatchProcessor`(Spring 무의존)**: `run(items, op[, options])` → ①입력순서 0-기반 인덱스 부여 ②op 이 `BatchPreflight` 면 IO 전 1회 사전검증(충돌 등, 실패 시 전체 중단) ③dryRun 이면 `plan` 만 ④`try(var exec=newVirtualThreadPerTaskExecutor())`+`Semaphore(parallelism)`+`AtomicBoolean abort` 로 병렬 ⑤close()(=join) 후 future 수집·index 정렬. 실패는 `BusinessException` 의 `ErrorCode` 보존(일반예외→`FBAT0004`), fail-fast 시 이후 SKIPPED.
- **SPI/타입**: `BatchFileOperation`(`apply`+`plan`)·`BatchPreflight`(교차검증 capability)·`BatchItem`(record, 경로/본문 둘 다·`BatchContent` 지연스트림·index·meta)·`BatchOptions`(parallelism/continueOnError/dryRun)·`ItemOutcome`(OK/FAILED/SKIPPED)·`BatchResult`(집계·`hasFailures`/`failures`)·`BatchSafety`(단일 파일명 강제)·`FileBatchErrorCode`(`FBAT****`).
- **작업 3종**: `RenameOperation`(implements BatchFileOperation,BatchPreflight — prefix/suffix/regex/sequence/template, 충돌 FAIL→예외/SUFFIX→`name-1.ext` 연번, **인덱스 기반이라 병렬 결정적**, `Files.move` resolveSibling)·`ImageTransformOperation`(framework-image `ImageProcessor` 위임·확장자 retarget)·`CompressOperation`(framework-archive `Archiver` **파일별** gzip, 경로 있으면 스트리밍·없으면 바이트). 팩토리 `BatchImageOps`/`BatchArchiveOps`(명세가 호출마다 달라 op 자체는 빈 아님).
- **config**: `FileBatchProperties`(`framework.file-batch`, enabled=false·default-parallelism=16) + `FileBatchAutoConfiguration`(`@AutoConfiguration`+토글, `FileBatchProcessor` 빈, 중첩 `ImageOpsConfiguration`/`ArchiveOpsConfiguration` 에 `@ConditionalOnClass`+`@ConditionalOnBean` 백오프) + `.imports`.
- **와이어링**: settings.gradle include + archtest testImplementation. build.gradle = `api framework-core` + `compileOnly` image/archive(+test 재선언) + lombok/configuration-processor + starter-test.

## 현재 상태 (적용/검증)
- ✅ **순수 로직 javac 컴파일 OK**(HttpStatus 스텁) + 위임 4종이 **실제 image/archive 시그니처에 컴파일 OK**.
- ✅ **하니스 27/27**(순서/격리/fail-fast/드라이런 IO0/동시도 상한/에러코드 보존·매핑/충돌 FAIL·SUFFIX/unsafe 거부/5정책/실파일 이동/빈입력/parallelism floor) + **위임 10/10**(실 `ZipArchiver` gzip 라운드트립 경로·본문 / 모의 `ImageProcessor` retarget·기록 / 드라이런 IO0).
- ✅ **archtest 7규칙 정적 무충돌**(Jackson0·필드주입0·`@AutoConfiguration`/`@ConfigurationProperties` 네이밍·중첩은 `*Configuration`·순환 없음).
- ⚠️ 작성 환경 **Maven Central 차단** → Spring 부 gradle 컴파일/JUnit 실행은 미실행. 받는 쪽 검증 경로 아래.
- 신규 파일: 모듈 main 17(타입10+ops5+config2)·`.imports`·build.gradle / test 5(Rename·Processor·Compress·ImageTransform·AutoConfig) / settings+archtest 와이어링(직전 세션에 이미 추가됨).

## 켜는 법
```bash
# framework.file-batch.enabled: true  (+ default-parallelism: 16)
#   FileBatchProcessor fileBatch;
#   var rename = new RenameOperation(RenameOperation.sequence("img", 1, 3));
#   BatchResult r = fileBatch.run(items, rename);            // 부분실패 격리·병렬
#   fileBatch.run(items, batchImageOps.thumbnail(128, ImageFormat.JPEG, .8f)); // image 위임
#   fileBatch.run(items, batchArchiveOps.gzip());            // archive 위임(파일별 .gz)
#   fileBatch.run(items, rename, BatchOptions.defaults().withDryRun(true));    // 계획만
# 검증(받는 쪽)
./gradlew :framework:framework-file-batch:test :framework:framework-archtest:test spotlessApply
```

## 바로 다음 할 일 (Next)
1. **(devops) CI 게이트** — `:framework-archtest:test` + 전 모듈 `:test` PR 차단 + 멀티모듈 jacoco 집계(루트 aggregate).
2. **그릇 정비** — 게이트웨이 런타임 점검(CORS preflight·rate-limit 429)·k8s 멀티서비스(redis/secret/ServiceMonitor 실배포)·CI-CD 멀티서비스화.
3. (선택) **file-batch 후속** — 복사/이동 op·outputDir 분리 기록·진행률 콜백. 아카이빙 후속 tar/tar.gz(commons-compress 옵트인)·RetryUtils.
4. (선택) 규제특화 잔여(pki/hsm/recon/egov)·saga 단계별 타임아웃/보상 재시도·실DB 통합테스트.
   - 기본기능 카탈로그 §6 대기열 잔여 = 서버측 S3 멀티파트 병렬 업로드(백로그)뿐. 추적은 `docs/BASELINE_FEATURES.md`.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **가상스레드 executor 의 close()가 곧 join**: `try(var exec=newVirtualThreadPerTaskExecutor()){…submit…}` 의 `close()`(Java19+ AutoCloseable)가 제출 작업 전부 끝날 때까지 블록 → `future.get()` 수집은 **try 밖(close 이후)**. 동시도는 가상스레드 무제한이 아니라 `Semaphore` 로 제한(스토리지 보호). fail-fast=`AtomicBoolean abort`(set 후 후속은 SKIPPED).
- **교차 아이템 검증은 apply 가 아니라 BatchPreflight**: 개별 `apply` 는 다른 아이템을 못 봄 → 이름 충돌 등 전체 검증은 `BatchPreflight` capability(ISP)로 분리, IO 전 1회. `RenameOperation` 이 충돌을 미리 확정(`resolved` 맵)하고 apply 는 읽기만(병렬 안전). 연번은 공유 카운터 대신 **입력 index 기반**이라 결정적.
- **@ConditionalOnClass 백오프 + FilteredClassLoader 테스트 함정**: 위임 빈은 **중첩 static @Configuration** 에 `@ConditionalOnClass`/`@ConditionalOnBean`(ASM 평가, image 선례). 클래스 부재 백오프 테스트는 `FilteredClassLoader` 로 시뮬 — ⚠️ **필터한 타입을 참조하는 사용자 설정은 그 컨텍스트에서 로드 불가** → image 가린 테스트엔 Archiver 빈만, archive 가린 테스트엔 ImageProcessor 빈만 주는 **단일-위임 설정**으로 분리(공용 DelegateBeans 는 양쪽 참조라 깨짐).
- **얇은 위임 — 인코딩 직접 안 함**: 변환=image, 압축=archive 위임만(새 인코딩 0). `CompressOperation` 은 SPI per-item 이라 **파일별 gzip**(`<name>.gz`) — "여러 파일→1 zip" 집계는 일괄처리가 아니라 `Archiver.zip(entries,out)` 단일 호출(설계서 모호성 해소). 출력은 경로 있으면 sibling 스트리밍·없으면 본문 바이트(outputDir 파라미터 없음). 결과 이름은 `BatchSafety` 단일 파일명 강제.
- (지난·유효) Jackson3(`tools.jackson.*`, annotation만 예외) / compileOnly 타입 test 재선언(introspection) / 새 오토컨피그 `.imports`+등록가드 / EPP 는 spring.factories(Boot4 패키지) / spotless Palantir·`lineEndings=UNIX`·설정캐시 / 필터·EPP 는 GlobalExceptionHandler 밖 / prod 가드(JWT·DevAuth·Password·AES마스터키) / Boot4 패키지 리네임 추측 금지 / 설정값 암호화 토글만 예외적 기본 on / tar 는 JDK 미지원(commons-compress 옵트인) / 아카이브 해제는 zip-slip+압축폭탄 가드 필수.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증 가능하게(이번 `FileBatchProcessor`/`RenameOperation`/`BatchSafety` 가 그 예).
2. 기존 인터페이스는 capability 인터페이스로 확장(ISP — 이번 `BatchPreflight`). 생성자 변경 시 기존 오버로드 유지.
3. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+test 재선언), BOM 밖=`implementation`.
4. 새 오토컨피그면 `.imports`+등록가드 테스트. **EPP 는 `spring.factories`**(Boot4 패키지). 신규 모듈은 settings include + archtest testImplementation 도 추가.
5. 오토컨피그 토글 기본 off + `@ConditionalOnMissingBean`(암호화 설정복호화 토글만 예외적 on). 위임 백오프는 **중첩 @Configuration** 에 `@ConditionalOnClass`/`@ConditionalOnBean`.
6. 테스트: 핵심 알고리즘 단위(JDK) + 오토컨피그 토글/빈선택/등록가드 + 위임 백오프(`FilteredClassLoader`, 필터 타입 미참조 설정으로 분리).
7. 드롭인: 변경 파일 전부 한 zip, 루트 `unzip -o`. 문서 동기화. 사용자 환경 `./gradlew :...:test :framework-archtest:test spotlessApply` 검증.
<!-- 갱신 끝 -->
