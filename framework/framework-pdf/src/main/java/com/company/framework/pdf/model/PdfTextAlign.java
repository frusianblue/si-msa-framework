package com.company.framework.pdf.model;

/**
 * 표 셀 가로 정렬. 프레임워크 자체 enum 으로, OpenPDF 의 {@code Element.ALIGN_*} 상수는 {@code PdfExporter} 내부에서만
 * 매핑한다(소비 서비스가 OpenPDF 타입을 알 필요 없음).
 */
public enum PdfTextAlign {
    LEFT,
    CENTER,
    RIGHT
}
