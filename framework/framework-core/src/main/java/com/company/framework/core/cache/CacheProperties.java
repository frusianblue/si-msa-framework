package com.company.framework.core.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   cache:
 *     enabled: true
 *     spec: "maximumSize=10000,expireAfterWrite=10m"   # Caffeine 스펙
 */
@ConfigurationProperties(prefix = "framework.cache")
public class CacheProperties {
    private boolean enabled = true;
    private String spec = "maximumSize=10000,expireAfterWrite=10m";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }
}
