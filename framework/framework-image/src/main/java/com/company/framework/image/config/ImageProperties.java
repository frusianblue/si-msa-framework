package com.company.framework.image.config;

import com.company.framework.image.ImageFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이미지 모듈 설정. 접두사 {@code framework.image}. 선택형 모듈 컨벤션대로 기본 비활성.
 *
 * <pre>
 * framework:
 *   image:
 *     enabled: true                 # 모듈 전체 토글(기본 false)
 *     default-format: JPEG          # 썸네일 기본 출력 포맷(JPEG/PNG)
 *     thumbnail-max-edge: 320       # thumbnail(maxEdge) 기본 변 길이(px)
 *     jpeg-quality: 0.85            # JPEG 품질 0.0~1.0
 *     max-source-pixels: 40000000   # 디코드 허용 최대 픽셀 수(디컴프레션 폭탄 방지)
 * </pre>
 */
@ConfigurationProperties(prefix = "framework.image")
public class ImageProperties {

    /** 모듈 전체 활성 여부(기본 false). */
    private boolean enabled = false;

    /** 썸네일 기본 출력 포맷(기본 JPEG). */
    private ImageFormat defaultFormat = ImageFormat.JPEG;

    /** thumbnail(maxEdge) 호출 시 기본 변 길이 px(기본 320). */
    private int thumbnailMaxEdge = 320;

    /** JPEG 품질 0.0~1.0(기본 0.85). */
    private float jpegQuality = 0.85f;

    /** 디코드 허용 최대 픽셀 수 — 폭탄 방지(기본 40,000,000). */
    private long maxSourcePixels = 40_000_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ImageFormat getDefaultFormat() {
        return defaultFormat;
    }

    public void setDefaultFormat(ImageFormat defaultFormat) {
        this.defaultFormat = defaultFormat;
    }

    public int getThumbnailMaxEdge() {
        return thumbnailMaxEdge;
    }

    public void setThumbnailMaxEdge(int thumbnailMaxEdge) {
        this.thumbnailMaxEdge = thumbnailMaxEdge;
    }

    public float getJpegQuality() {
        return jpegQuality;
    }

    public void setJpegQuality(float jpegQuality) {
        this.jpegQuality = jpegQuality;
    }

    public long getMaxSourcePixels() {
        return maxSourcePixels;
    }

    public void setMaxSourcePixels(long maxSourcePixels) {
        this.maxSourcePixels = maxSourcePixels;
    }
}
