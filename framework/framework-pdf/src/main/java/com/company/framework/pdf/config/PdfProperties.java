package com.company.framework.pdf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PDF 모듈 토글/레이아웃.
 *
 * <pre>{@code
 * framework:
 *   pdf:
 *     enabled: false                       # 선택형 → 명시적 on. 켜면 PdfExporter/PdfFontProvider 빈 제공.
 *     page-size: A4                        # A4 | A5 | LETTER | LEGAL
 *     landscape: false                     # 가로 방향
 *     margin: 36                           # 네 변 공통 여백(pt; 72pt=1inch)
 *     title-font-size: 16
 *     header-font-size: 10
 *     body-font-size: 9
 *     page-number: true                    # 하단 중앙 페이지 번호
 *     font:
 *       location: classpath:fonts/NanumGothic.ttf   # 한글 임베딩 폰트(파일/클래스패스). 비우면 라틴 폴백→한글 깨짐 주의
 * }</pre>
 *
 * <p>운영(특히 한글 산출물)은 {@code font.location} 에 한글 TTF(NanumGothic 등 OFL 라이선스 권장) 경로를 반드시
 * 지정한다. 미지정 시 내장 라틴 폰트로 폴백되어 한글 글리프가 비어 보일 수 있다.
 */
@ConfigurationProperties(prefix = "framework.pdf")
public class PdfProperties {

    /** 선택형 → 기본 off. */
    private boolean enabled = false;

    /** 페이지 크기: A4 | A5 | LETTER | LEGAL. */
    private String pageSize = "A4";

    /** 가로 방향 여부. */
    private boolean landscape = false;

    /** 네 변 공통 여백(pt). */
    private float margin = 36f;

    /** 제목 폰트 크기(pt). */
    private float titleFontSize = 16f;

    /** 표 헤더 폰트 크기(pt). */
    private float headerFontSize = 10f;

    /** 본문/셀 폰트 크기(pt). */
    private float bodyFontSize = 9f;

    /** 하단 중앙 페이지 번호 출력. */
    private boolean pageNumber = true;

    private final Font font = new Font();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPageSize() {
        return pageSize;
    }

    public void setPageSize(String pageSize) {
        this.pageSize = pageSize;
    }

    public boolean isLandscape() {
        return landscape;
    }

    public void setLandscape(boolean landscape) {
        this.landscape = landscape;
    }

    public float getMargin() {
        return margin;
    }

    public void setMargin(float margin) {
        this.margin = margin;
    }

    public float getTitleFontSize() {
        return titleFontSize;
    }

    public void setTitleFontSize(float titleFontSize) {
        this.titleFontSize = titleFontSize;
    }

    public float getHeaderFontSize() {
        return headerFontSize;
    }

    public void setHeaderFontSize(float headerFontSize) {
        this.headerFontSize = headerFontSize;
    }

    public float getBodyFontSize() {
        return bodyFontSize;
    }

    public void setBodyFontSize(float bodyFontSize) {
        this.bodyFontSize = bodyFontSize;
    }

    public boolean isPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(boolean pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Font getFont() {
        return font;
    }

    /** 한글 임베딩 폰트 설정. */
    public static class Font {
        /** TTF/OTF 폰트 위치(Spring Resource 표기: {@code classpath:...} 또는 {@code file:...}). 비우면 라틴 폴백. */
        private String location = "";

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }
}
