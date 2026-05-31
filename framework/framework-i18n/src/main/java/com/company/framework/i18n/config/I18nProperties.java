package com.company.framework.i18n.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * i18n 토글/설정.
 * application.yml:
 *   framework:
 *     i18n:
 *       enabled: true                              # 2단 토글(선택형이라 기본 false)
 *       basenames: [classpath:messages, classpath:messages-common]
 *       default-locale: ko
 *       encoding: UTF-8
 *       cache-seconds: -1                          # 운영 -1(영구), 개발 5(리로드)
 *       error-localization: true                   # ErrorCode 메시지를 로케일별로 해석
 */
@ConfigurationProperties(prefix = "framework.i18n")
public class I18nProperties {

    /** 모듈 활성 여부(선택형 기본값 = false). */
    private boolean enabled = false;

    /** 메시지 번들 basename. 앞쪽이 우선(프로젝트 messages 가 framework 기본을 override). */
    private List<String> basenames = List.of("classpath:messages", "classpath:messages-common");

    /** Accept-Language 가 없을 때 기본 로케일. */
    private String defaultLocale = "ko";

    /** 번들 인코딩. */
    private String encoding = "UTF-8";

    /** 번들 캐시 시간(초). -1=영구, 0=매번, n=초. */
    private int cacheSeconds = -1;

    /** BusinessException 메시지를 로케일별로 해석할지. */
    private boolean errorLocalization = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBasenames() {
        return basenames;
    }

    public void setBasenames(List<String> basenames) {
        this.basenames = basenames;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public int getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(int cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }

    public boolean isErrorLocalization() {
        return errorLocalization;
    }

    public void setErrorLocalization(boolean errorLocalization) {
        this.errorLocalization = errorLocalization;
    }
}
