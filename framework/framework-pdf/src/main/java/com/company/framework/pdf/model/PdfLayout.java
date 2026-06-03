package com.company.framework.pdf.model;

/**
 * PDF 레이아웃(페이지/여백/폰트크기/페이지번호). {@code PdfProperties} 에서 만들어 {@code PdfExporter} 에 주입하는
 * 불변 값객체 — 익스포터를 Spring 비의존으로 유지(엑셀 {@code ExcelExporter} 가 windowSize 만 받는 것과 같은 결).
 *
 * @param pageSize 페이지 크기 이름: {@code A4|A5|LETTER|LEGAL}(대소문자 무시, 미인식 시 A4)
 * @param landscape 가로 방향 여부
 * @param margin 네 변 공통 여백(pt; 72pt = 1inch). 보통 36~54
 * @param titleFontSize 제목 폰트 크기(pt)
 * @param headerFontSize 표 헤더 폰트 크기(pt)
 * @param bodyFontSize 본문/셀 폰트 크기(pt)
 * @param pageNumber 하단 중앙 페이지 번호 출력 여부
 */
public record PdfLayout(
        String pageSize,
        boolean landscape,
        float margin,
        float titleFontSize,
        float headerFontSize,
        float bodyFontSize,
        boolean pageNumber) {

    /** 표준 기본값(A4 세로, 여백 36pt, 제목16/헤더10/본문9, 페이지번호 on). */
    public static PdfLayout defaults() {
        return new PdfLayout("A4", false, 36f, 16f, 10f, 9f, true);
    }
}
