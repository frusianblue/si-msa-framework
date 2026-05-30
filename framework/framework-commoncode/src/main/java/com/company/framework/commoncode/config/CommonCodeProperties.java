package com.company.framework.commoncode.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   commoncode:
 *     enabled: true
 */
@ConfigurationProperties(prefix = "framework.commoncode")
public class CommonCodeProperties {
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
