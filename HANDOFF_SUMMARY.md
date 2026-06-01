# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**업무 생산성 첫 모듈 framework-excel** 완성 — **다운로드(SXSSF 스트리밍)** + **업로드(양식검증: 헤더/타입/필수/길이·패턴, 행별 오류수집)**. POI 는 BOM 밖이라 카탈로그에 버전 고정(5.5.1)했고, POI 타입은 모듈 내부에만 두어(implementation) 소비 서비스엔 비노출.

## 최종 갱신
- 일자: 2026-05-31 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **신규 `framework/framework-excel/`** ([선택], `framework.excel.enabled=false` 기본). **새 외부 의존성 1: Apache POI 5.5.1**(BOM 밖 → 카탈로그 고정).
  - **다운로드 `ExcelExporter`** — `SXSSFWorkbook(windowSize)` 스트리밍. `write(out, sheetName, List<ExcelColumn<T>>, Iterable/Stream<T>)`. 헤더 볼드, 날짜/일시는 셀 서식(yyyy-mm-dd / yyyy-mm-dd hh:mm:ss) 적용, **finally 에서 `dispose()`**(디스크 임시파일 정리). 값 타입 분기(String/Boolean/BigDecimal/Number/LocalDate/LocalDateTime/Date). autoSize 는 SXSSF 비용커서 미사용 — 지정 너비만 반영.
  - **업로드 `ExcelImporter`** — `WorkbookFactory.create`(usermodel, xls+xlsx). **양식검증 2단**: ① 하드 실패(예외 `BusinessException(INVALID_INPUT)`) = 시트없음/헤더없음/필수컬럼헤더 누락/암호화/형식오류/maxRows 초과, ② 소프트 실패(수집) = 필수누락/타입불일치/길이·패턴위반 → `ExcelValidationError`(엑셀 행번호 1-based + 헤더). 컬럼은 **헤더 텍스트로 위치 매핑**(열 순서 무관). `failFast` 토글. 결과 `ExcelImportResult(rows, errors)`(검증통과 행만 rows).
  - 공개 모델: `ExcelColumn<T>`(export) · `ExcelColumnSpec`/`ExcelTemplate`/`ExcelCellType`/`ExcelRow`/`ExcelValidationError`/`ExcelImportResult`(import). 빌더 제공.
  - 오토컨피그 `ExcelAutoConfiguration` — `@ConditionalOnClass(Workbook)` + `@ConditionalOnProperty(framework.excel.enabled=true)` + `@ConditionalOnMissingBean` 으로 `ExcelExporter`/`ExcelImporter` 빈. 인프라 의존 0 → 켜는 즉시 사용.
- **등록/문서**: `settings.gradle`(excel include) · `libs.versions.toml`(`poi=5.5.1` + `poi-ooxml` alias) · 루트 `build.gradle` ext `poiVersion` 브리지 · `STACK.md` 3.1(POI 행) · `docs/FRAMEWORK_MODULES.md`(2.4 ✅ + 진행현황) 갱신.

## 현재 상태 (적용/검증)
- 신규/변경 파일 모두 repo 반영. 정적 점검 통과(중괄호/괄호/대괄호 균형, 패키지=디렉터리 일치, **Jackson2(databind/core) import 0**, 내부 심볼·core 참조 전부 존재, getter 사용 일치).
- **POI API 시그니처는 apache/poi GitHub 소스(REL_5_4_0)로 직접 확인**: `setBlank()`·`getCachedFormulaResultType()`·`setCellValue(LocalDate/LocalDateTime/Date/String/double/boolean)`·`getLocalDateTimeCellValue()`·`getNumericCellValue()`·`getBooleanCellValue()`·`getStringCellValue()` 모두 존재(5.5.1 상위호환).
- ⚠️ **실제 gradle 컴파일 미검증**(작성 환경: services.gradle.org / Maven Central 차단). 받는 쪽에서:
  - `./gradlew :framework:framework-excel:compileJava`
  - 권장: `./gradlew spotlessApply` (removeUnusedImports/Palantir 정렬).

## 켜는 법 (application.yml)
```yaml
framework:
  excel:
    enabled: true
    export: { window-size: 100 }     # SXSSF 메모리 윈도(행 수). 클수록 빠르고 메모리↑
    import: { max-rows: 100000 }      # 업로드 데이터 행 상한(헤더 제외). 초과 시 INVALID_INPUT
```
사용: 컨트롤러에서 `ExcelExporter` 주입 → `response.getOutputStream()` 으로 스트리밍 다운로드. `ExcelImporter` 주입 → `MultipartFile.getInputStream()` + `ExcelTemplate` 로 `readAndValidate()` → `result.hasErrors()` 분기.

## 바로 다음 할 일 (Next)
1. 받는 쪽에서 **excel 모듈 컴파일 확인** + `spotlessApply`.
2. **업무 생산성 잔여** — framework-batch(Spring Batch+스케줄러 Quartz) · framework-notification(mail/sms/알림톡 채널 추상화).
   - 또는 **messaging 소비자측**: 멱등 소비(컨슈머) — Kafka 헤더 `x-event-id` 키로 기존 **framework-idempotency** 연계.
3. 이후: 규제특화(pki/mfa/hsm/recon/egov) → observability → 게이트웨이/k8s/CI-CD 멀티서비스화. (상세 순서 `docs/FRAMEWORK_MODULES.md` 4절)

### (참고) excel 스코프 결정
- **읽기는 usermodel(in-memory) + maxRows 가드**로 한정. 폼/대장 업로드(수천~수만 행)에 적합. 진짜 대용량 "읽기"(수십만+행 SAX 스트리밍)는 미구현 — 필요해지면 `XSSFReader`+SAX 이벤트 모델로 별도 리더 추가(API 복잡도↑). 대용량 **쓰기**는 이미 SXSSF 로 커버.
- POI 타입은 **implementation**(전이 금지). 소비 서비스는 프레임워크 공개 타입만 의존 → POI 버전 업그레이드가 서비스로 새지 않음.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **POI 는 Boot BOM 미관리** → `libs.versions.toml [versions] poi` 로 버전 고정 필수(spring-kafka 와 반대 케이스). 루트 ext `poiVersion` 브리지로 모듈 build.gradle 의 `${poiVersion}` 동작(springdoc/awsSdk 패턴 동일).
- **POI 타입 비노출 원칙**: `poi-ooxml` 은 `implementation`(api 아님). 모듈 공개 API 는 자체 타입(ExcelExporter/ExcelTemplate/...)만. → 소비 서비스 build.gradle 에 POI 직접 추가 불필요.
- **SXSSF 종료는 `close()`**(try-with-resources). POI 5.5+ 에서 `dispose()` 는 **deprecated** — `close()` 가 시트 writer flush + 디스크 임시파일 삭제를 함께 수행한다(예전엔 dispose 가 필요했으나 역전됨). exporter 는 try-with-resources 로 close.
- **SXSSF autoSizeColumn 금지**(트래킹 켜야 동작 + 비용 큼) → 너비는 컬럼 정의의 명시값만 반영.
- **읽기 셀 타입은 effectiveType 로**: FORMULA 셀은 `getCachedFormulaResultType()` 로 환산 후 분기. 날짜는 `DateUtil.isCellDateFormatted` 확인 후 `getLocalDateTimeCellValue()`(숫자 시리얼 오인 방지).
- **양식 불일치(헤더 누락 등)는 행별 오류가 아니라 하드 실패**(BusinessException). 값 오류만 수집. 둘을 섞지 말 것(사용자 안내 메시지가 달라짐).
- **`import` 은 Java 예약어 → 패키지명 불가**. 그래서 `excel.importer`/`excel.exporter` 로 명명. (Properties 의 중첩클래스 `Import`/getter `getImport()` 는 식별자로 합법 → 프로퍼티명은 `framework.excel.import.*` 로 정상 바인딩.)
- (기존) **새 모듈은 `settings.gradle` 등록 필수** · Boot4/Jackson3 API 변경 확인(추측 금지, 컴파일 미검증 환경) · "core 가 노출하니 다 된다" 가정 주의(web/jdbc 직접 사용은 `compileOnly` 명시) · 트랜잭션매니저 새로 정의 금지 · bash `{a,b}` 확장 미동작.

## 모듈 추가 레시피 (검증된 반복 절차)
1. `framework/framework-<X>/` 생성: `config`(Properties+AutoConfiguration) · 도메인 패키지 · `resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`(FQCN 등록).
2. `build.gradle`: `api project(':framework:framework-core')`(+필요 시 다른 framework 모듈) + starter 는 `compileOnly`(이미 core 가 노출하면 생략). **BOM 밖 새 라이브러리는 `libs.versions.toml`+루트 ext 브리지로 버전 고정**(POI 사례). 내부 구현 라이브러리는 `implementation`(전이 금지), 능력을 전이해야 하면 `api`(kafka 사례).
3. **`settings.gradle` 에 include 추가**(잊지 말 것).
4. 코드 작성 전 **Boot 4/Spring 7/Jackson 3 + 외부 라이브러리(POI 등) API 확인**(`...jdbc.autoconfigure.DataSourceAutoConfiguration`, `tools.jackson.databind.json.JsonMapper`). 모르면 추측 말고 공식 API/소스(GitHub raw)로 확인.
5. 오토컨피그: `@AutoConfiguration` + `@ConditionalOnClass(모듈/라이브러리 마커)` + `@ConditionalOnProperty(framework.<x>.enabled=true)` + 빈은 `@ConditionalOnMissingBean`. 다른 모듈 빈 의존은 `@AutoConfiguration(afterName=...)` + `@ConditionalOnBean`.
6. 검증: `./gradlew :framework:framework-<X>:compileJava` (Configuration Cache 꼬이면 `--no-configuration-cache` 또는 `clean`).
7. 드롭인 배포: 모듈 폴더 + 변경 파일 + **완성 `settings.gradle`/`libs.versions.toml`/루트 `build.gradle`** 을 한 zip 에 담아 루트에서 `unzip -o`.


<!-- 갱신 끝 -->
