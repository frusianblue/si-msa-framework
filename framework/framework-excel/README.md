# framework-excel

Apache POI 기반 Excel 업/다운로드. 다운로드는 **SXSSF 스트리밍**(대용량 일정 메모리), 업로드는 **양식 검증**(헤더/타입/필수/길이·패턴, 행별 오류 수집). POI 타입은 비노출(implementation).

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-excel') }   // framework-core 전제
```
```yaml
framework:
  excel:
    enabled: true            # 기본 false
    export: { window-size: 100 }     # SXSSF 메모리 윈도우(행)
    import: { max-rows: 100000 }     # 업로드 상한
```

## 쓰는 법
**다운로드(스트리밍)**
```java
private final ExcelExporter exporter;

List<ExcelColumn<UserDto>> cols = List.of(
    ExcelColumn.of("이름", UserDto::getName),
    ExcelColumn.of("이메일", UserDto::getEmail));
exporter.write(response.getOutputStream(), "사용자", cols, rows);   // Iterable 또는 Stream
```
**업로드(검증)** — `ExcelImporter` 가 행별로 타입/필수/패턴을 검증하고 `ExcelValidationError` 목록을 모아 반환(부분 오류 식별 가능). `ExcelCellType` 으로 컬럼 타입 선언.


## 실전 사용 예 (코드)

**내보내기** — 컬럼을 `ExcelColumn.of(헤더, 추출함수)` 로 선언하고 스트리밍으로 쓴다(대용량 안전, SXSSF).
```java
// com.company.framework.excel.{exporter.ExcelExporter, model.ExcelColumn}
private final ExcelExporter exporter;   // 빈 주입

public void export(HttpServletResponse res, List<Member> members) throws IOException {
    res.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    res.setHeader("Content-Disposition", "attachment; filename=members.xlsx");
    List<ExcelColumn<Member>> cols = List.of(
        ExcelColumn.of("이름", Member::getName),
        ExcelColumn.of("나이", Member::getAge),
        ExcelColumn.of("가입일", Member::getJoinedAt, 18));
    exporter.write(res.getOutputStream(), "회원", cols, members);
}
```
**가져오기** — 템플릿 검증과 함께 읽기:
```java
ExcelImportResult result = importer.readAndValidate(uploaded.getInputStream(), memberTemplate);
if (result.hasErrors()) { /* 행/열 단위 오류 반환 */ }
```

## 끄는 법
`framework.excel.enabled: false` 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
얇은 유틸 모듈 — 보통 그대로 쓰되, 컬럼/검증 정의를 호출부에서 구성한다.

## 버전 관리
POI(`poiVersion`)는 `implementation` 으로 캡슐화(전이 노출 없음). 변경 시 `STACK.md` 갱신.
