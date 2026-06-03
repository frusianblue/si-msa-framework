# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**PDF 산출물 생성 모듈 `framework-pdf` 신설** — "기본기능 갭"의 PDF 후보 처리. 표 기반 `PdfReport`/`PdfColumn` 스펙을 `PdfExporter` 가 OutputStream 으로 스트리밍(거래내역서/통지서). 한글은 **TTF IDENTITY_H 임베딩**(`PdfFontProvider`). 엔진 **OpenPDF 2.0.2**(iText4 LGPL/MPL fork=AGPL 회피, SI/공공 안전). 3단 토글·기본 off, BOM 밖 의존성 1종(implementation 비노출). 옵트인·하위호환, 미사용 시 런타임 0.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
1. **모델 스펙**(`com.company.framework.pdf.model`): `PdfColumn<T>`(header + `Function<T,String>` extract + 상대너비 + `PdfTextAlign`; 엑셀 `ExcelColumn` 동형이나 추출이 **String** 반환) · `PdfReport<T>`(빌더: title 필수·metaLines·columns 필수≥1·rows·footerNote, ctor 검증) · `PdfTextAlign`(LEFT/CENTER/RIGHT, OpenPDF `Element.ALIGN_*` 매핑은 익스포터 내부) · `PdfLayout`(record: pageSize/landscape/margin/폰트크기/pageNumber + `defaults()`).
2. **폰트**(`font/PdfFontProvider`): `BaseFont.createFont(name, IDENTITY_H, EMBEDDED, true, ttfBytes, null)` 로 한글 TTF 임베딩·캐시. 바이트 null/깨짐 → `DocumentException|IOException` catch → **라틴(Helvetica) 폴백**(절대 throw 안 함, 생성은 성공). `hasEmbeddedFont()`/`body(size)`/`bold(size)` 제공(반환 `com.lowagie.text.Font` = 내부 타입, 소비자 비노출).
3. **익스포터**(`exporter/PdfExporter`): `write(OutputStream, PdfReport<T>)`. try-with-resources `Document`(AutoCloseable, close 가 trailer/xref flush) → `PdfWriter.getInstance` → (옵션)페이지번호 이벤트 → 제목(중앙)·메타줄·`PdfPTable`(width 100%·상대너비·`setHeaderRows(1)` 페이지 반복·헤더 배경 `java.awt.Color(235)`)·하단 고지문. `PageNumberEvent extends PdfPageEventHelper.onEndPage` 가 하단중앙 "- N -"(`ColumnText.showTextAligned`). 페이지크기 A4/A5/LETTER/LEGAL `new Rectangle(PageSize.*)`+`rotate()`(landscape). `DocumentException`→`BusinessException(INTERNAL_ERROR)`, null 인자→`IllegalArgumentException`.
4. **오토컨피그/프로퍼티**: `PdfAutoConfiguration`(`@ConditionalOnClass(Document)`+`@ConditionalOnProperty(framework.pdf.enabled=true)`+`@EnableConfigurationProperties`). 빈 `pdfFontProvider`(ResourceLoader 로 `font.location` 읽어 bytes→Provider, 미설정/부재/IOException→경고+라틴 폴백) · `pdfExporter`(PdfProperties→PdfLayout 조립). 둘 다 `@ConditionalOnMissingBean`. `PdfProperties`(enabled=false·pageSize=A4·landscape·margin=36·title16/header10/body9·pageNumber=true·nested `font.location`).
5. **등록/배선**: `settings.gradle` include · `META-INF/spring/...AutoConfiguration.imports` · `framework-archtest/build.gradle` project 의존 · **레지스트레이션 가드 테스트**(클래스패스 `.imports` union 읽어 FQCN 단언). build.gradle = `api framework-core` + `implementation openpdf` + config-processor + test(no lombok).
6. **버전 단일화**: `libs.versions.toml`(`openpdf=2.0.2` + 라이브러리 alias) · 루트 `build.gradle` ext(`openpdfVersion`) · `STACK.md` 의존성 표 행.
7. **테스트 3종**: `PdfExporterTest`(%PDF- 헤더 + %%EOF·A5가로+페이지번호off·null인자 IAE·빌더 title/columns 누락 거부, 라틴 폴백으로 폰트파일 불요) · `PdfFontProviderTest`(null/깨진바이트 graceful 폴백) · `PdfAutoConfigurationTest`(토글 off 기본·on→2빈·존재안하는 font location graceful·가드).
8. **문서 5종 동기화**: `framework/framework-pdf/README.md` 신설 · 루트 `README.md`(의존성·요약줄·카탈로그) · `HANDOFF.md`(1·6·7절) · `docs/FRAMEWORK_MODULES.md`(0·2.4·3·4절) · `STACK.md`(OpenPDF 행).

## 현재 상태 (적용/검증)
- ✅ **사용자 환경 컴파일 BUILD 통과 확인(2026-06-03)**. OpenPDF API 는 `com.lowagie` **2.x javadoc 경로로 교차확인**(2.0.2 javadoc `.../com.github.librepdf.openpdf/com/lowagie/text/pdf/...` → 모듈명은 librepdf, **실제 패키지는 com.lowagie.text**)했고 실제 빌드로 검증됨.
- 검증 완료: `./gradlew :framework:framework-pdf:test :framework:framework-archtest:test spotlessApply` (사용자 환경 정상)
- 신규 의존성 **1종(OpenPDF 2.0.2)**: `implementation`(소비자 비노출), Boot BOM 밖→카탈로그+ext 고정. 그 외 0(config-processor=annotationProcessor, starter-test=test). `@ConditionalOnClass(Document)` 는 implementation 이 런타임 클래스패스에 있어 동작(excel 의 `@ConditionalOnClass(Workbook)`+POI 와 동일 패턴).

## 켜는 법
```yaml
framework:
  pdf:
    enabled: true
    page-size: A4              # A4 | A5 | LETTER | LEGAL
    page-number: true
    font:
      location: classpath:fonts/NanumGothic.ttf   # 한글 임베딩(비우면 라틴 폴백→한글 깨짐)
```
```java
PdfReport<Tx> report = PdfReport.<Tx>builder()
    .title("거래내역서").metaLine("기간: 2026-01-01 ~ 2026-01-31")
    .column(PdfColumn.of("거래일시", t -> DateUtils.format(t.getDate())))
    .column(PdfColumn.of("금액", t -> MoneyUtils.format(t.getAmount()), 1f, PdfTextAlign.RIGHT))
    .rows(txList).build();
pdfExporter.write(response.getOutputStream(), report);   // 스트리밍 다운로드
```
- 한글 산출물은 `font.location` 에 TTF(NanumGothic=OFL 권장) 필수. 대용량(수십만 행)은 표 전체 메모리 구성이라 부적합(분할/Excel).

## 바로 다음 할 일 (Next)
1. (devops) **CI 게이트**: `:framework-archtest:test` + 전 모듈 `:test` PR 차단 + **멀티모듈 jacoco 집계 리포트**(루트 aggregate).
2. **그릇 정비**: 게이트웨이 런타임 점검(CORS preflight·rate-limit 429) · k8s 멀티서비스/CI-CD(redis/secret/observability ServiceMonitor 실배포).
3. **기본기능 갭 잔여**: 개인정보 로그 마스킹 · 분산 캐시(분산 락=framework-lock, PDF 산출물=framework-pdf 로 완료).
4. 파일 후속(선택): 이미지 처리(썸네일/EXIF) · 대용량 스트리밍(HTTP Range/S3 presigned) · 안티바이러스 훅.
5. (선택) 규제특화 잔여(pki/hsm/recon/egov) · saga 단계별 타임아웃/보상 재시도 · 멱등 재생 페이로드 지문.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **OpenPDF 패키지는 버전에 묶임**: **3.0.0 부터 `com.lowagie.text` → `org.openpdf` 로 리네임**. 그래서 import 가 확정된 **2.0.2 로 의도적 고정**(올리려면 전 import 경로 교체). 2.x javadoc URL 의 `com.github.librepdf.openpdf` 는 **Java 모듈명**이고 실제 패키지는 `com.lowagie.text`(헷갈리지 말 것).
- **OpenPDF 색상은 `java.awt.Color`**(iText 5+ 의 `BaseColor` 아님). `Font(Font.HELVETICA, size, style)` / `Font(BaseFont, size, style)`, `PdfPCell.setBackgroundColor(java.awt.Color)`.
- **한글 = TTF IDENTITY_H 임베딩**: `BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, ttfBytes, null)`(파일 없이 메모리 바이트로). 폰트 미설정/파싱실패는 **던지지 말고 라틴 폴백**(생성 자체는 성공해야 — 운영 경고만). name 은 `.ttf/.otf` 힌트.
- **OpenPDF Document 는 AutoCloseable**: try-with-resources 의 `close()` 가 PDF 마무리(trailer/xref)+flush. `PdfWriter.getInstance`/`PdfPTable.setWidths`/`document.add` 모두 checked `DocumentException` → 한 try 로 묶어 `BusinessException` 변환. 표 헤더 반복은 `setHeaderRows(1)`.
- **CGLIB 프록시 필드는 null(직전 lock 세션 교훈)**: 인터페이스 없는 빈을 `getBean` 하면 CGLIB 프록시(Objenesis 생성)라 **필드 초기화자 미실행** → 프록시의 필드 직접 접근은 null. 테스트는 메서드(예: `ranCount()`)로 위임 접근.
- (지난·유효) BOM 밖 의존(POI/OpenPDF)= `implementation` 비노출 + 카탈로그/ext/STACK 고정 / 레지스트레이션 가드(`.imports` union 직접 단언) / introspection=compileOnly 타입 test 재선언 / 토글 3단 기본 off(commoncode·file·openapi 만 matchIfMissing) / Jackson3(`tools.jackson.*`, `.annotation` 만 예외) / 필터예외는 GlobalExceptionHandler 밖 / Boot4 패키지·스타터 개명 추측 금지(aop→aspectj 등 공식 확인) / JUnit launcher·starter-test 모듈마다 / 콘솔 UTF-8(클라이언트 JVM 포함; 미해결 보류).

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증.
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`, BOM 밖 내부 라이브러리=`implementation`(비노출). **테스트가 그 compileOnly 클래스(또는 그게 붙은 컨트롤러/빈)를 참조하면 재선언.** 선택 의존(Tika 류)은 `compileOnly`+가드된 인스턴스화.
3. `settings.gradle`/`imports` 등록. 신규 모듈이면 `framework-archtest/build.gradle` 에 project 의존 추가. **새 오토컨피그는 `.imports` 등록 + 등록 가드 테스트 확인**(미등록=죽은 코드). BOM 밖 의존은 `libs.versions.toml`+root ext+`STACK.md`.
4. Boot4/Spring7/Jackson3 + 통합 대상 실제 시그니처를 레포 내 동일 사용처/공식 소스로 교차확인(Boot4 패키지 이동·외부 라이브러리 패키지 리네임 추측 금지 — 예: OpenPDF 3.0 org.openpdf).
5. 오토컨피그 3단 토글 + 빈 `@ConditionalOnMissingBean`. 런타임 개수 가변 빈은 `ImportBeanDefinitionRegistrar`, 기존 빈 래핑은 `BeanPostProcessor`.
6. **테스트**: 핵심 알고리즘 단위 + 오토컨피그 로딩(enabled/disabled). MapperScan+MyBatis 결합은 임베디드 H2 슬라이스로 enabled 까지. AOP 는 실 프록시(`@EnableAspectJAutoProxy`). 외부연동 WireMock(standalone). 검증 `./gradlew :…:test (+:framework-archtest:test) (+spotlessApply)`.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 5종 동기화.

<!-- 갱신 끝 -->
