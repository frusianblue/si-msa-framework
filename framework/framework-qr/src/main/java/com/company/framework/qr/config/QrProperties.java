package com.company.framework.qr.config;

import com.company.framework.qr.QrEccLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QR 모듈 설정. 접두사 {@code framework.qr}. 선택형 모듈 컨벤션대로 기본 비활성.
 *
 * <pre>
 * framework:
 *   qr:
 *     enabled: true               # 모듈 전체 토글(기본 false)
 *     default-size-px: 256        # toPng(content) 기본 한 변(px)
 *     default-margin: 4           # 기본 조용한 영역(모듈 수)
 *     default-ecc-level: M        # 기본 오류정정(L/M/Q/H)
 *     default-charset: UTF-8      # 기본 바이트 인코딩 문자셋
 *     max-content-length: 1024    # 인코딩 전 차단할 내용 길이 상한(문자 수, 0 이하면 검사 생략)
 * </pre>
 */
@ConfigurationProperties(prefix = "framework.qr")
public class QrProperties {

    /** 모듈 전체 활성 여부(기본 false). */
    private boolean enabled = false;

    /** toPng(content) 기본 한 변 px(기본 256). */
    private int defaultSizePx = 256;

    /** 기본 조용한 영역 모듈 수(기본 4). */
    private int defaultMargin = 4;

    /** 기본 오류정정 레벨(기본 M). */
    private QrEccLevel defaultEccLevel = QrEccLevel.M;

    /** 기본 바이트 인코딩 문자셋(기본 UTF-8). */
    private String defaultCharset = "UTF-8";

    /** 인코딩 전 차단할 내용 길이 상한(문자 수, 기본 1024). 0 이하면 길이 검사 생략. */
    private int maxContentLength = 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultSizePx() {
        return defaultSizePx;
    }

    public void setDefaultSizePx(int defaultSizePx) {
        this.defaultSizePx = defaultSizePx;
    }

    public int getDefaultMargin() {
        return defaultMargin;
    }

    public void setDefaultMargin(int defaultMargin) {
        this.defaultMargin = defaultMargin;
    }

    public QrEccLevel getDefaultEccLevel() {
        return defaultEccLevel;
    }

    public void setDefaultEccLevel(QrEccLevel defaultEccLevel) {
        this.defaultEccLevel = defaultEccLevel;
    }

    public String getDefaultCharset() {
        return defaultCharset;
    }

    public void setDefaultCharset(String defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }
}
