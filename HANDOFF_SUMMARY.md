# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**기본기능 카탈로그 #8+#9+#10 "파일 하드닝 묶음" 완료** (`framework-file*` 확장, 새 모듈 아님). 기존 `FileStorage`(store/load/delete/type)는 **불변**으로 두고 선택 기능을 **capability 인터페이스(ISP)** 로 추가 — **#8** HTTP Range 206 스트리밍 다운로드 + S3 presigned PUT/GET(대용량 클라 직행), **#9** 확장자↔검출 MIME **계열 정합**(Tika 확장), **#10** ClamAV INSTREAM 안티바이러스 게이트(**순수 JDK 소켓**). **신규 외부 의존성 0**. 직전 작업 image deprecation(`PAYLOAD_TOO_LARGE`→`CONTENT_TOO_LARGE`) 수정 동봉. 순수 JDK 코어 javac+하니스 35/35 통과.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### A. 파일 하드닝 묶음 (framework-file / -s3 확장)
1. **#10 AV 훅(`scan/`)**: `FileScanner` SPI + `ScanResult`(record, clean/infected+signature+scannerType) + `NoOpFileScanner`(기본 통과) + `ClamavFileScanner`(ClamAV INSTREAM, **순수 `java.net.Socket`**: `zINSTREAM\0`→[len(4B BE)][chunk]…[0]→`stream: OK`/`<sig> FOUND` 파싱, 청크 스트리밍, fail-closed 기본/fail-open 옵션, maxChunk 상한). `FileService.upload` 가 콘텐츠검증 다음·저장 전 별도 스트림으로 스캔 게이트(감염→`BusinessException`).
2. **#9 확장자↔MIME 정합(`validator/`)**: `ExtensionContentTypePolicy`(확장자→허용 MIME 계열 집합, 순수 JDK; 이미지/PDF/텍스트/OOXML(zip)/OLE2/hwp 카테고리, 정확 MIME 또는 `prefix/` 접두 매칭, 규칙없음=통과) + `TikaFileContentTypeValidator` 오버로드(정책 주입). `enforce-extension-match=true` 면 검출 MIME 이 선언 확장자 계열과 어긋날 때 거부.
3. **#8 Range/presigned(`storage/`)**: `ByteRange`(RFC7233 단일범위 파서: `bytes=S-E`/`S-`/`-suffix`, 다중·만족불가→empty, `contentRangeHeader()`) · `RangeReadableStorage`(`loadRange`/`contentLength`) · `PresignedUrl`(record) · `PresignedUrlStorage`(`presignGet`/`presignPut`). `FileSystemFileStorage` 가 Range 구현(`SeekableByteChannel.position`+내부 `BoundedInputStream`). `S3FileStorage` 가 Range(GetObject `range("bytes=..")`)+presigned(`S3Presigner`, GetObject/PutObjectPresignRequest) 구현, 생성자 2개(presigner 유/무).
4. **프로퍼티/배선**: `FileStorageProperties` 에 `validation.enforce-extension-match`·`storage.presigned-get-ttl(5m)`/`presigned-put-ttl(10m)`·신규 `scan`(enabled/type/host/port/timeouts/maxChunk/fail-open). `FileStorageAutoConfiguration` 에 `fileScanner` 빈(clamav/NoOp 선택, `@ConditionalOnMissingBean`)·validator 에 정책 주입·`fileService`(scanner 5인자)·`fileController`(props 주입). `S3FileStorageAutoConfiguration` 에 `S3Presigner` 빈 추가 + `s3FileStorage` 가 presigner 주입.
5. **컨트롤러**: `FileController` GET `/{id}` 에 `Range` 헤더 처리(206 Partial/Content-Range, 만족불가 416, 미지원 저장소·암호화는 전체 200 폴백) + `GET /{id}/presigned`·`POST /presigned-upload`·`POST /presigned-complete`(+`PresignedCompleteRequest` dto, `registerExternalUpload`).

### B. image deprecation 수정 + 문서
6. **image 수정**: `ImageErrorCode.java` `HttpStatus.PAYLOAD_TOO_LARGE`→`CONTENT_TOO_LARGE`(Spring 6.1+ RFC9110, 413 동일). 이 묶음 zip 에 동봉.
7. **테스트 6종 신규**: `ByteRangeTest`·`ExtensionContentTypePolicyTest`·`ClamavFileScannerTest`(mock `ServerSocket` 으로 INSTREAM 왕복·OK/FOUND/ERROR·fail-closed/open)·`FileSystemRangeTest`·`FileScannerFeaturesTest`(빈 선택)·`FileServiceScanGateTest`(Mockito 감염 거부/정상 진행).
8. **문서 동기화 7종**: 루트+docs `BASELINE_FEATURES.md`(#8·#9·#10 ✅ 승격·우선순위·완료로그) · `docs/FRAMEWORK_MODULES.md`(file 책임 확장) · `HANDOFF.md`(구조·보안골격·함정·현재상태·우선순위) · `README.md`(파일 하드닝 사용 섹션) · `STACK.md`(presigner=s3 포함·ClamAV 순수소켓) · 이 `HANDOFF_SUMMARY.md`.

## 현재 상태 (적용/검증)
- ✅ **순수 JDK 코어 javac+하니스 35/35 통과**(실제 core `ErrorCode`/`BusinessException` + slf4j/HttpStatus stub): ByteRange 파서 전 케이스·ExtensionContentTypePolicy 정합/위장거부/계열통과·ClamAV INSTREAM **mock 소켓 왕복**(명령+청크 프레이밍 정확성·OK/FOUND/ERROR·fail-closed/open)·FileSystem loadRange 오프셋/길이/꼬리.
- ⚠️ **Spring/Tika/AWS 배선은 작성 환경 Gradle 미검증**(Maven Central 차단) — 기존 file/s3·context·pdf 패턴 미러. 받는 쪽에서 `:framework:framework-file:test :framework:framework-file-s3:test` 로 확인.
- 설계상 arch 통과 예상: 새 AutoConfig 없음(기존에 빈만 추가) → `.imports` 무변경 · top-level `*Properties` 기존 · 필드주입 0 · Jackson 미사용 · 슬라이스 file→core/mybatis·s3→file 단방향.
- **신규 외부 의존성 0**: Range=JDK NIO, ClamAV=순수 소켓, S3Presigner=awssdk:s3 포함 → build.gradle 무변경.

## 켜는 법
```yaml
framework:
  file:
    enabled: true
    storage:
      type: s3                      # local | nas | s3
      presigned-get-ttl: 5m
      presigned-put-ttl: 10m
    validation:
      content-type-detection: true  # Tika 매직넘버(확장자 정합의 전제)
      enforce-extension-match: true # .png 인데 PDF/zip/실행파일이면 거부
    scan:
      enabled: true                 # 안티바이러스 게이트
      type: clamav                  # none | clamav
      host: localhost
      port: 3310
      fail-open: false              # 기본 fail-closed(데몬 장애 시 거부)
```
- 다운로드 `GET /api/v1/files/{id}` + `Range: bytes=0-1023` → 206 Partial. 암호화 저장소는 자동 전체 다운로드.
- S3: `GET /{id}/presigned`(다운로드 URL) · `POST /presigned-upload?filename=&contentType=`(업로드 URL) · `POST /presigned-complete`(메타 등록). 대용량은 클라가 S3 직행(서버 비경유).

## 바로 다음 할 일 (Next)
1. **사용자 환경 빌드 검증**: `./gradlew :framework:framework-file:test :framework:framework-file-s3:test :framework:framework-archtest:test spotlessApply`(+ image deprecation 사라졌는지).
2. (devops) **CI 게이트**: `:framework-archtest:test` + 전 모듈 `:test` PR 차단 + 멀티모듈 jacoco 집계.
3. **카탈로그 §6 대기열 승격**: 아카이빙/압축 · 일괄 처리(framework-batch 경계 정리). 또는 **서버측 S3 멀티파트 병렬 업로드**(TransferManager, 이번 백로그).
4. (선택) presigned 업로드 비동기 후처리 스캔(신뢰경계 밖 본문 검증), 암호화 파일 키 회전.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **capability 인터페이스(ISP) 패턴**: 기존 `FileStorage` 불변, 선택기능은 `RangeReadableStorage`/`PresignedUrlStorage` 별도 인터페이스 + `instanceof` 분기·자동 폴백. 새 백엔드는 필요한 capability 만 선택 구현.
- **암호화(AES-CBC) 저장소는 Range 미지원**: 임의오프셋 복호화 불가 → `EncryptingFileStorage` 가 `RangeReadableStorage` 미구현 → 컨트롤러가 전체 다운로드로 폴백(올바름). Range 와 at-rest 암호화는 양립 불가.
- **presigned PUT 업로드는 본문 비경유 → 콘텐츠/AV 검증 불가**: `registerExternalUpload` 는 파일명 확장자 화이트리스트만. 신뢰경계 밖이면 비동기 후처리 스캔 별도.
- **AV fail-closed 기본**: ClamAV 데몬 장애 시 거부(`BusinessException`, ISMS-P 보안 우선). 가용성 우선이면 `scan.fail-open=true`. INSTREAM 은 순수 소켓 → 의존성 0, mock `ServerSocket` 으로 청크 프레이밍 검증.
- **확장자↔MIME 은 "계열" 정합만**: tika-core 매직넘버는 컨테이너(zip/OLE2)까지만 정확 → docx↔xlsx 미구분, 명백한 위장만 차단(오탐 방지). 규칙 없는 확장자는 통과.
- **AWS SDK v2 presigner=`awssdk:s3` 포함**(`...services.s3.presigner.S3Presigner`) → s3-presigner 별도 추가 **금지**. S3 AutoConfig 에 `S3Presigner` 빈 추가 + `S3FileStorage(s3, presigner, bucket)`.
- (지난·유효) 토글3단 기본 off / Jackson3(`tools.jackson.*`) / compileOnly 타입 test 재선언(introspection) / `.imports` 가드 / 슬라이스 단방향 / 필드주입 금지 / Spring `HttpStatus.PAYLOAD_TOO_LARGE`→`CONTENT_TOO_LARGE`(6.1+) / 필터는 GlobalExceptionHandler 밖 / Boot4 패키지·외부 라이브러리 리네임 추측 금지 / JUnit launcher·starter-test 모듈마다 / 콘솔 UTF-8(보류).

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증(이번: ByteRange/ExtensionContentTypePolicy/ClamavFileScanner/FileSystem Range 를 javac+하니스 35/35; core 실타입 + slf4j/HttpStatus stub).
2. **기존 인터페이스는 건드리지 말고 capability 인터페이스로 확장**(ISP) — 기존 구현/테스트 보존, 신기능은 `instanceof` 분기. 생성자 변경 시 기존 생성자 오버로드 유지(이번: Tika validator·S3FileStorage).
3. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+test 재선언), BOM 밖=`implementation`. 이번은 의존성 0(전부 JDK+기존, S3Presigner=s3 포함) → 변경 없음.
4. 새 오토컨피그면 `.imports`+가드 테스트. **기존 오토컨피그에 빈만 추가면 `.imports` 무변경**(이번 케이스). 빈 추가 시 기존 컨텍스트 러너 테스트가 새 빈 주입으로 안 깨지는지 확인.
5. 오토컨피그 토글 + 빈 `@ConditionalOnMissingBean`. 선택 백엔드(clamav 등)는 설정 분기로 NoOp/실구현 선택.
6. **테스트**: 핵심 알고리즘 단위(JDK, mock 소켓 등 합성) + 오토컨피그 빈 선택 + 게이트 동작(Mockito). 생성자 시그니처 바뀐 기존 테스트 회귀 확인.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 7종 동기화. 사용자 환경 `./gradlew :...:test :framework-archtest:test spotlessApply` 검증.

<!-- 갱신 끝 -->
