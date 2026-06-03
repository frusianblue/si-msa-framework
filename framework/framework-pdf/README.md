# framework-pdf

PDF 산출물 생성. 전자정부/금융 산출물(거래내역서·통지서 등)을 **표 기반 스펙**(`PdfReport`)으로 받아 스트리밍 생성한다. **한글 TTF 임베딩**으로 어느 뷰어/OS 에서도 동일 렌더. 엔진은 **OpenPDF**(iText 4 의 LGPL/MPL fork — iText 5+ 의 AGPL 회피, SI/공공 납품 안전).

## 켜는 법

**1단 · 모듈 등록** — `settings.gradle`
```gradle
include 'framework:framework-pdf'   // 선택형: PDF 산출물이 필요한 프로젝트만
```
프로젝트 `build.gradle`
```gradle
dependencies { implementation project(':framework:framework-pdf') }
```

**2·3단 · 기능/폰트** — `application.yml`
```yaml
framework:
  pdf:
    enabled: true                 # 끄면(또는 미설정) PdfExporter/PdfFontProvider 빈 미등록
    page-size: A4                 # A4 | A5 | LETTER | LEGAL
    landscape: false
    margin: 36                    # 네 변 공통 여백(pt; 72pt=1inch)
    title-font-size: 16
    header-font-size: 10
    body-font-size: 9
    page-number: true             # 하단 중앙 페이지 번호
    font:
      location: classpath:fonts/NanumGothic.ttf   # 한글 임베딩 폰트(file:/... 도 가능)
```
> **한글은 폰트 임베딩이 핵심.** `font.location` 을 비우면 내장 라틴 폰트로 폴백되어 **한글 글리프가 비어 보인다**(생성 자체는 실패하지 않음). 운영/한글 산출물은 NanumGothic 같은 **TTF**(OFL 라이선스 권장)를 클래스패스나 파일경로로 반드시 지정한다.

### 왜 OpenPDF 2.x 인가
- 라이선스 **LGPL-2.1 / MPL-2.0** — iText 5+/7 의 AGPL 함정을 피해 폐쇄 소스/납품에 안전.
- 패키지는 `com.lowagie.text` (2.x). **3.0+ 는 `org.openpdf` 로 리네임**되어, 본 모듈은 안정 API 인 2.x 로 고정한다. 업그레이드 시 import 경로가 바뀌는 점에 유의.
- Boot BOM 밖 의존성 → `libs.versions.toml`(openpdf) + 루트 ext(openpdfVersion) 로 버전 단일 고정. 모듈은 `implementation` 으로만 참조(소비 서비스에 OpenPDF 타입 비노출).

## 쓰는 법
```java
private final PdfExporter pdfExporter;   // 빈 주입

public void download(HttpServletResponse response, List<Tx> txList) throws IOException {
    PdfReport<Tx> report = PdfReport.<Tx>builder()
        .title("거래내역서")
        .metaLine("계좌: 123-456-7890")
        .metaLine("기간: 2026-01-01 ~ 2026-01-31")
        .column(PdfColumn.of("거래일시", t -> DateUtils.format(t.getDate())))
        .column(PdfColumn.of("적요", Tx::getDesc, 2f))                              // 상대 너비 2배
        .column(PdfColumn.of("금액", t -> MoneyUtils.format(t.getAmount()), 1f, PdfTextAlign.RIGHT))
        .rows(txList)
        .footerNote("본 내역서는 안내용이며 법적 효력이 없습니다.")
        .build();

    response.setContentType("application/pdf");
    response.setHeader("Content-Disposition", "attachment; filename=statement.pdf");
    pdfExporter.write(response.getOutputStream(), report);   // 스트리밍
}
```
- `PdfColumn.of(header, extract)` — 추출 함수는 **String 반환**(숫자/날짜 포맷은 `MoneyUtils`/`DateUtils` 로 호출측에서). 오버로드로 상대 너비·정렬 지정.
- 표 헤더는 페이지가 넘어가도 자동 반복(`setHeaderRows`). 페이지 번호는 하단 중앙.
- `null` 셀 값은 빈 문자열로 안전 처리.

## 끄는 법
- `framework.pdf.enabled: false` 또는 키 생략 → 빈 미등록, 런타임 비용 0.
- 의존성을 빼면 OpenPDF 클래스가 사라져 오토컨피그가 `@ConditionalOnClass(Document)` 에서 탈락.

## 덮어쓰기(프로젝트 커스텀)
프로젝트가 `PdfExporter` 또는 `PdfFontProvider` 빈을 직접 등록하면 `@ConditionalOnMissingBean` 으로 프레임워크 기본 구현이 양보한다(여러 폰트/레이아웃이 필요하면 직접 등록).

## 한계
- OpenPDF 는 표 전체를 메모리에 구성한다 → **초대용량(수십만 행)에는 부적합**(그 경우 분할 생성, 또는 표 데이터는 `framework-excel` 의 SXSSF 스트리밍 사용).
- 복잡한 디자인(워터마크/도장/서식 양식 채우기)은 본 모듈 범위 밖 — 필요 시 OpenPDF 저수준 API 로 별도 구현.

## 버전 관리
OpenPDF 는 Boot BOM 밖이므로 `gradle/libs.versions.toml`(openpdf) 단일 고정 + `STACK.md` 표 반영(HANDOFF 8절). 폰트 파일은 라이선스(NanumGothic=OFL 등) 확인 후 산출물에 임베딩한다.
