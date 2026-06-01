package com.company.framework.excel.exporter;

import com.company.framework.excel.model.ExcelColumn;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * 대용량 안전 Excel(xlsx) 다운로드. POI {@link SXSSFWorkbook} 의 슬라이딩 윈도(rowAccessWindowSize)로
 * 메모리에 올리는 행 수를 제한하므로, 수십만 행도 일정 메모리로 스트리밍 생성한다.
 *
 * <p>주의: SXSSF 는 윈도 밖으로 밀려난 행을 디스크 임시파일로 내린다. {@code write} 메서드는 finally 에서
 * {@code dispose()} 로 임시파일을 반드시 제거한다(누수 방지). 컨트롤러에서 {@code HttpServletResponse.getOutputStream()}
 * 에 바로 흘려보내면 클라이언트로 곧장 스트리밍된다.
 *
 * <p>날짜/일시 값은 셀 서식(yyyy-mm-dd, yyyy-mm-dd hh:mm:ss)을 적용해 숫자(시리얼)로 보이지 않게 한다.
 */
public class ExcelExporter {

    private final int windowSize;

    /**
     * @param windowSize SXSSF 가 메모리에 유지할 행 수(나머지는 디스크로). 보통 100~1000. 1 이상.
     */
    public ExcelExporter(int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("windowSize 는 1 이상이어야 합니다.");
        }
        this.windowSize = windowSize;
    }

    /** Iterable 행 소스로 시트 1개를 작성해 출력 스트림으로 흘려보낸다. */
    public <T> void write(OutputStream out, String sheetName, List<ExcelColumn<T>> columns, Iterable<T> rows) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns 가 비어 있습니다.");
        }
        SXSSFWorkbook workbook = new SXSSFWorkbook(windowSize);
        try {
            CreationHelper helper = workbook.getCreationHelper();
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook, helper, "yyyy-mm-dd");
            CellStyle dateTimeStyle = dateStyle(workbook, helper, "yyyy-mm-dd hh:mm:ss");

            Sheet sheet = workbook.createSheet(sheetName == null || sheetName.isBlank() ? "Sheet1" : sheetName);

            // 헤더행
            Row header = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(columns.get(c).header());
                cell.setCellStyle(headerStyle);
            }

            // 데이터행
            int rowIdx = 1;
            for (T item : rows) {
                Row row = sheet.createRow(rowIdx++);
                for (int c = 0; c < columns.size(); c++) {
                    Cell cell = row.createCell(c);
                    setCellValue(cell, columns.get(c).extract(item), dateStyle, dateTimeStyle);
                }
            }

            // 열 너비: SXSSF 는 autoSizeColumn 에 트래킹이 필요해 비용이 크므로, 지정 너비만 반영.
            for (int c = 0; c < columns.size(); c++) {
                Integer w = columns.get(c).width();
                if (w != null) {
                    sheet.setColumnWidth(c, Math.min(255, w) * 256);
                }
            }

            workbook.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException("Excel 다운로드 작성 실패", e);
        } finally {
            // 메모리 정리 + 디스크 임시파일 삭제. (close 가 아니라 dispose 가 임시파일까지 제거)
            workbook.dispose();
        }
    }

    /** Stream 오버로드(지연 평가 소스). 내부적으로 순차 소비한다. */
    public <T> void write(OutputStream out, String sheetName, List<ExcelColumn<T>> columns, Stream<T> rows) {
        write(out, sheetName, columns, (Iterable<T>) rows::iterator);
    }

    private static CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static CellStyle dateStyle(SXSSFWorkbook workbook, CreationHelper helper, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(helper.createDataFormat().getFormat(format));
        return style;
    }

    /** 값의 자바 타입에 맞춰 셀에 기록. 날짜/일시는 서식 스타일을 적용한다. */
    private static void setCellValue(Cell cell, Object value, CellStyle dateStyle, CellStyle dateTimeStyle) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        switch (value) {
            case String s -> cell.setCellValue(s);
            case Boolean b -> cell.setCellValue(b);
            case BigDecimal d -> cell.setCellValue(d.doubleValue());
            case Number n -> cell.setCellValue(n.doubleValue());
            case LocalDate d -> {
                cell.setCellValue(d);
                cell.setCellStyle(dateStyle);
            }
            case LocalDateTime dt -> {
                cell.setCellValue(dt);
                cell.setCellStyle(dateTimeStyle);
            }
            case Date d -> {
                cell.setCellValue(d);
                cell.setCellStyle(dateTimeStyle);
            }
            default -> cell.setCellValue(value.toString());
        }
    }
}
