package com.company.framework.excel.importer;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.excel.model.ExcelColumnSpec;
import com.company.framework.excel.model.ExcelImportResult;
import com.company.framework.excel.model.ExcelRow;
import com.company.framework.excel.model.ExcelTemplate;
import com.company.framework.excel.model.ExcelValidationError;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * 업로드 Excel 을 {@link ExcelTemplate} 양식에 맞춰 읽고 검증한다(양식검증).
 *
 * <h3>판정 기준</h3>
 * <ul>
 *   <li><b>양식 자체가 틀린 경우(하드 실패 → 예외)</b>: 시트 없음/헤더행 없음/필수 컬럼 헤더 누락/암호화/형식 오류/행수 초과.
 *       → {@link BusinessException}({@code INVALID_INPUT}). 사용자가 올린 파일이 "이 양식이 아닌" 상황.
 *   <li><b>양식은 맞으나 값이 틀린 경우(소프트 실패 → 수집)</b>: 필수 누락/타입 불일치/길이·패턴 위반.
 *       → {@link ExcelValidationError} 로 모아 {@link ExcelImportResult#errors()} 에 담는다(행/컬럼 단위).
 * </ul>
 *
 * <p>컬럼은 헤더 <b>텍스트</b>로 위치를 찾으므로 실제 열 순서와 무관하다(헤더만 맞으면 됨).
 * 읽기는 POI usermodel(in-memory) 기반이라 폼 형태의 업로드에 적합하다. 메모리 보호용으로 {@code maxRows} 가드를 둔다.
 * (수십만 행 이상 대용량 "다운로드"는 {@link com.company.framework.excel.exporter.ExcelExporter} 가 스트리밍으로 처리.)
 */
public class ExcelImporter {

    private final int maxRows;
    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * @param maxRows 데이터 행 수 상한(헤더 제외). 초과 업로드는 INVALID_INPUT 으로 거부. 1 이상.
     */
    public ExcelImporter(int maxRows) {
        if (maxRows < 1) {
            throw new IllegalArgumentException("maxRows 는 1 이상이어야 합니다.");
        }
        this.maxRows = maxRows;
    }

    public ExcelImportResult readAndValidate(InputStream in, ExcelTemplate template) {
        Workbook workbook = openWorkbook(in);
        try (workbook) {
            Sheet sheet = selectSheet(workbook, template);
            Map<String, Integer> headerIndex = readHeaderIndex(sheet, template);
            Map<ExcelColumnSpec, Integer> columnIndex = mapColumns(template, headerIndex);

            int firstDataRow = template.headerRowIndex() + 1;
            int lastRow = sheet.getLastRowNum();
            if (lastRow - template.headerRowIndex() > maxRows) {
                throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "허용 행 수(" + maxRows + ")를 초과했습니다.");
            }

            List<ExcelRow> rows = new ArrayList<>();
            List<ExcelValidationError> errors = new ArrayList<>();

            for (int r = firstDataRow; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (isRowEmpty(row, columnIndex)) {
                    continue;
                }
                int excelRowNumber = r + 1; // 화면 기준 1-based
                Map<String, Object> values = new LinkedHashMap<>();
                boolean rowHasError = false;

                for (Map.Entry<ExcelColumnSpec, Integer> e : columnIndex.entrySet()) {
                    ExcelColumnSpec spec = e.getKey();
                    int col = e.getValue();
                    Cell cell = (row == null || col < 0) ? null : row.getCell(col);
                    String raw = displayValue(cell);
                    try {
                        values.put(spec.key(), convert(cell, spec));
                    } catch (CellConvertException ex) {
                        rowHasError = true;
                        errors.add(new ExcelValidationError(
                                excelRowNumber, spec.header(), spec.key(), raw, ex.getMessage()));
                        if (template.failFast()) {
                            return new ExcelImportResult(rows, errors);
                        }
                    }
                }

                if (!rowHasError) {
                    rows.add(new ExcelRow(excelRowNumber, values));
                }
            }

            return new ExcelImportResult(rows, errors);
        } catch (IOException e) {
            throw new UncheckedIOException("Excel 읽기 실패", e);
        }
    }

    // ===== 양식(하드) 검증 =====

    private Workbook openWorkbook(InputStream in) {
        try {
            return WorkbookFactory.create(in);
        } catch (EncryptedDocumentException e) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "암호화된 엑셀 파일은 처리할 수 없습니다.");
        } catch (IOException | RuntimeException e) {
            // POI 의 형식 오류(NotOLE2FileException 등)는 RuntimeException 으로 던져진다.
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "엑셀 파일 형식이 올바르지 않습니다.");
        }
    }

    private Sheet selectSheet(Workbook workbook, ExcelTemplate template) {
        Sheet sheet;
        if (template.sheetName() == null) {
            sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
        } else {
            sheet = workbook.getSheet(template.sheetName());
        }
        if (sheet == null) {
            throw new BusinessException(
                    ErrorCode.Common.INVALID_INPUT,
                    "시트를 찾을 수 없습니다" + (template.sheetName() == null ? "." : ": " + template.sheetName()));
        }
        return sheet;
    }

    /** 헤더행을 읽어 (헤더 텍스트 → 컬럼 인덱스) 맵을 만든다. */
    private Map<String, Integer> readHeaderIndex(Sheet sheet, ExcelTemplate template) {
        Row header = sheet.getRow(template.headerRowIndex());
        if (header == null) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "헤더 행이 없습니다.");
        }
        Map<String, Integer> map = new HashMap<>();
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            String text = displayValue(cell);
            if (text != null && !text.isBlank()) {
                map.putIfAbsent(text.trim(), c);
            }
        }
        return map;
    }

    /** 각 컬럼 스펙을 실제 컬럼 인덱스에 매핑. 필수 헤더 누락 또는 strictHeader 위반은 하드 실패. */
    private Map<ExcelColumnSpec, Integer> mapColumns(ExcelTemplate template, Map<String, Integer> headerIndex) {
        Map<ExcelColumnSpec, Integer> result = new LinkedHashMap<>();
        for (ExcelColumnSpec spec : template.columns()) {
            Integer idx = headerIndex.get(spec.header().trim());
            if (idx == null) {
                if (template.strictHeader() || spec.required()) {
                    throw new BusinessException(
                            ErrorCode.Common.INVALID_INPUT, "양식 헤더 불일치: '" + spec.header() + "' 컬럼이 없습니다.");
                }
                result.put(spec, -1); // 선택 컬럼 부재 → 값은 항상 null
            } else {
                result.put(spec, idx);
            }
        }
        return result;
    }

    // ===== 값(소프트) 검증/변환 =====

    private Object convert(Cell cell, ExcelColumnSpec spec) throws CellConvertException {
        if (isBlank(cell)) {
            if (spec.required()) {
                throw new CellConvertException("필수 값이 비어 있습니다.");
            }
            return null;
        }
        return switch (spec.type()) {
            case STRING -> convertString(cell, spec);
            case LONG -> convertLong(cell);
            case DOUBLE -> convertDouble(cell);
            case BIG_DECIMAL -> convertBigDecimal(cell);
            case BOOLEAN -> convertBoolean(cell);
            case LOCAL_DATE -> convertLocalDate(cell);
            case LOCAL_DATE_TIME -> convertLocalDateTime(cell);
        };
    }

    private String convertString(Cell cell, ExcelColumnSpec spec) throws CellConvertException {
        String value = displayValue(cell);
        value = value == null ? "" : value.trim();
        if (spec.maxLength() != null && value.length() > spec.maxLength()) {
            throw new CellConvertException("최대 길이(" + spec.maxLength() + ")를 초과했습니다.");
        }
        if (spec.pattern() != null && !Pattern.matches(spec.pattern(), value)) {
            throw new CellConvertException("형식이 올바르지 않습니다.");
        }
        return value;
    }

    private Long convertLong(Cell cell) throws CellConvertException {
        if (effectiveType(cell) == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (d != Math.floor(d) || Double.isInfinite(d)) {
                throw new CellConvertException("정수가 아닙니다.");
            }
            return (long) d;
        }
        try {
            return Long.parseLong(displayValue(cell).trim());
        } catch (NumberFormatException e) {
            throw new CellConvertException("정수 형식이 아닙니다.");
        }
    }

    private Double convertDouble(Cell cell) throws CellConvertException {
        if (effectiveType(cell) == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        try {
            return Double.parseDouble(displayValue(cell).trim());
        } catch (NumberFormatException e) {
            throw new CellConvertException("숫자 형식이 아닙니다.");
        }
    }

    private BigDecimal convertBigDecimal(Cell cell) throws CellConvertException {
        if (effectiveType(cell) == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        try {
            return new BigDecimal(displayValue(cell).trim());
        } catch (NumberFormatException e) {
            throw new CellConvertException("숫자 형식이 아닙니다.");
        }
    }

    private Boolean convertBoolean(Cell cell) throws CellConvertException {
        CellType type = effectiveType(cell);
        if (type == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }
        if (type == CellType.NUMERIC) {
            return cell.getNumericCellValue() != 0d;
        }
        String v = displayValue(cell).trim().toLowerCase();
        return switch (v) {
            case "true", "y", "yes", "1", "o" -> true;
            case "false", "n", "no", "0", "x" -> false;
            default -> throw new CellConvertException("불리언(예/아니오) 형식이 아닙니다.");
        };
    }

    private LocalDate convertLocalDate(Cell cell) throws CellConvertException {
        if (effectiveType(cell) == CellType.NUMERIC) {
            if (!DateUtil.isCellDateFormatted(cell)) {
                throw new CellConvertException("날짜 형식이 아닙니다.");
            }
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        try {
            return LocalDate.parse(displayValue(cell).trim());
        } catch (DateTimeParseException e) {
            throw new CellConvertException("날짜 형식(YYYY-MM-DD)이 아닙니다.");
        }
    }

    private LocalDateTime convertLocalDateTime(Cell cell) throws CellConvertException {
        if (effectiveType(cell) == CellType.NUMERIC) {
            if (!DateUtil.isCellDateFormatted(cell)) {
                throw new CellConvertException("일시 형식이 아닙니다.");
            }
            return cell.getLocalDateTimeCellValue();
        }
        try {
            return LocalDateTime.parse(displayValue(cell).trim());
        } catch (DateTimeParseException e) {
            throw new CellConvertException("일시 형식(YYYY-MM-DDTHH:MM:SS)이 아닙니다.");
        }
    }

    // ===== 셀 유틸 =====

    private boolean isRowEmpty(Row row, Map<ExcelColumnSpec, Integer> columnIndex) {
        if (row == null) {
            return true;
        }
        for (Integer col : columnIndex.values()) {
            if (col >= 0 && !isBlank(row.getCell(col))) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlank(Cell cell) {
        if (cell == null) {
            return true;
        }
        CellType type = effectiveType(cell);
        if (type == CellType.BLANK || type == CellType.ERROR) {
            return true;
        }
        if (type == CellType.STRING) {
            return cell.getStringCellValue().isBlank();
        }
        return false;
    }

    /** FORMULA 셀은 캐시된 결과 타입으로 환산. */
    private CellType effectiveType(Cell cell) {
        CellType type = cell.getCellType();
        return type == CellType.FORMULA ? cell.getCachedFormulaResultType() : type;
    }

    /** 사용자 안내/문자열 변환용 표시값(셀 서식 반영). 절대 예외를 던지지 않는다. */
    private String displayValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return dataFormatter.formatCellValue(cell);
    }

    /** 값 변환 실패 신호(내부 제어 흐름 전용). 스택트레이스 불필요. */
    private static final class CellConvertException extends Exception {
        private static final long serialVersionUID = 1L;

        CellConvertException(String message) {
            super(message, null, false, false);
        }
    }
}
