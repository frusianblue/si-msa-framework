package com.company.framework.image;

/**
 * 리사이즈/인코딩 명세(불변). 비율은 항상 유지하며, {@code maxWidth/maxHeight} 박스 안에 들어가도록 축소한다.
 *
 * <ul>
 *   <li>{@code maxWidth/maxHeight} — 출력 상한(둘 다 만족하도록 비율 유지 축소).
 *   <li>{@code allowUpscale} — 원본보다 키울지(기본 false: 작은 원본은 그대로).
 *   <li>{@code format} — 출력 포맷(화이트리스트).
 *   <li>{@code quality} — JPEG 품질 0.0~1.0(PNG 등 무손실엔 무시).
 *   <li>{@code correctOrientation} — EXIF orientation 보정 적용 여부.
 * </ul>
 *
 * <p>리인코딩 자체가 메타데이터(EXIF/GPS 포함)를 제거하므로 별도 strip 플래그는 두지 않는다 — "보정 후 항상 깨끗".
 */
public record ResizeSpec(
        int maxWidth,
        int maxHeight,
        boolean allowUpscale,
        ImageFormat format,
        float quality,
        boolean correctOrientation) {

    public ResizeSpec {
        if (maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("maxWidth/maxHeight 는 양수여야 합니다: " + maxWidth + "x" + maxHeight);
        }
        if (format == null) {
            throw new IllegalArgumentException("format 은 필수입니다.");
        }
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("quality 는 0.0~1.0 이어야 합니다: " + quality);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 정사각 박스(maxEdge x maxEdge) 썸네일 명세 — JPEG/지정품질/오리엔테이션 보정. */
    public static ResizeSpec thumbnail(int maxEdge, ImageFormat format, float quality) {
        return new ResizeSpec(maxEdge, maxEdge, false, format, quality, true);
    }

    /** 가독성용 빌더(기본: 업스케일 금지, JPEG, 품질 0.85, 오리엔테이션 보정 on). */
    public static final class Builder {
        private int maxWidth = 1920;
        private int maxHeight = 1920;
        private boolean allowUpscale = false;
        private ImageFormat format = ImageFormat.JPEG;
        private float quality = 0.85f;
        private boolean correctOrientation = true;

        public Builder maxWidth(int v) {
            this.maxWidth = v;
            return this;
        }

        public Builder maxHeight(int v) {
            this.maxHeight = v;
            return this;
        }

        public Builder maxEdge(int v) {
            this.maxWidth = v;
            this.maxHeight = v;
            return this;
        }

        public Builder allowUpscale(boolean v) {
            this.allowUpscale = v;
            return this;
        }

        public Builder format(ImageFormat v) {
            this.format = v;
            return this;
        }

        public Builder quality(float v) {
            this.quality = v;
            return this;
        }

        public Builder correctOrientation(boolean v) {
            this.correctOrientation = v;
            return this;
        }

        public ResizeSpec build() {
            return new ResizeSpec(maxWidth, maxHeight, allowUpscale, format, quality, correctOrientation);
        }
    }
}
