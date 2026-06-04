package com.company.framework.qr;

/**
 * QR 렌더링 명세(불변). 인코딩할 <b>내용은 인자로 분리</b>하고 이 스펙은 출력 형태만 담는다
 * ({@code QrGenerator.generate(content, spec)} — framework-image 의 {@code process(source, spec)} 와 같은 결).
 *
 * <ul>
 *   <li>{@code sizePx} — 출력 PNG 의 한 변(px, 정사각). ZXing 이 모듈 수에 맞춰 가장 가까운 배수로 맞춘다.
 *   <li>{@code margin} — 조용한 영역(quiet zone) 모듈 수. 스캐너 인식에 필요(권장 4 이상).
 *   <li>{@code eccLevel} — 오류정정 레벨(L/M/Q/H).
 *   <li>{@code charset} — 바이트 인코딩 문자셋(기본 UTF-8). 한글/이모지 포함 URL 도 안전.
 *   <li>{@code darkColor}/{@code lightColor} — 채움/배경 색(0xRRGGBB). 기본 검정/흰색(대비 최대 = 인식 최선).
 * </ul>
 *
 * <p>출력은 항상 PNG(무손실)다 — JPEG 같은 손실 포맷은 경계에 압축 잡음을 남겨 스캔 실패를 유발하므로 의도적으로 제외한다.
 */
public record QrSpec(int sizePx, int margin, QrEccLevel eccLevel, String charset, int darkColor, int lightColor) {

    /** 안전 상한: 한 변 8192px(= 약 67MP 버퍼) 초과는 디컴프레션/메모리 폭탄 방지로 거부. */
    public static final int MAX_SIZE_PX = 8192;

    /** 조용한 영역 상한(과도한 여백 방지). */
    public static final int MAX_MARGIN = 64;

    public QrSpec {
        if (sizePx <= 0 || sizePx > MAX_SIZE_PX) {
            throw new IllegalArgumentException("sizePx 는 1~" + MAX_SIZE_PX + " 여야 합니다: " + sizePx);
        }
        if (margin < 0 || margin > MAX_MARGIN) {
            throw new IllegalArgumentException("margin 은 0~" + MAX_MARGIN + " 여야 합니다: " + margin);
        }
        if (eccLevel == null) {
            throw new IllegalArgumentException("eccLevel 은 필수입니다.");
        }
        if (charset == null || charset.isBlank()) {
            throw new IllegalArgumentException("charset 은 필수입니다.");
        }
    }

    /** 기본 스펙: 256px · 여백 4 · ECC M · UTF-8 · 검정/흰색. */
    public static QrSpec defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 가독성용 빌더(기본: 256px, margin 4, ECC M, UTF-8, 검정/흰색). */
    public static final class Builder {
        private int sizePx = 256;
        private int margin = 4;
        private QrEccLevel eccLevel = QrEccLevel.M;
        private String charset = "UTF-8";
        private int darkColor = 0x000000;
        private int lightColor = 0xFFFFFF;

        public Builder sizePx(int v) {
            this.sizePx = v;
            return this;
        }

        public Builder margin(int v) {
            this.margin = v;
            return this;
        }

        public Builder eccLevel(QrEccLevel v) {
            this.eccLevel = v;
            return this;
        }

        public Builder charset(String v) {
            this.charset = v;
            return this;
        }

        public Builder darkColor(int rgb) {
            this.darkColor = rgb;
            return this;
        }

        public Builder lightColor(int rgb) {
            this.lightColor = rgb;
            return this;
        }

        public QrSpec build() {
            return new QrSpec(sizePx, margin, eccLevel, charset, darkColor, lightColor);
        }
    }
}
