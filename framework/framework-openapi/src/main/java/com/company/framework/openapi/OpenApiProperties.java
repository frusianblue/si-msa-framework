package com.company.framework.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   openapi:
 *     enabled: true
 *     title: "사용자 서비스 API"
 *     version: "v1"
 *     description: "..."
 */
@ConfigurationProperties(prefix = "framework.openapi")
public class OpenApiProperties {
    private boolean enabled = true;
    private String title = "API Documentation";
    private String version = "v1";
    private String description = "SI MSA Framework 기반 서비스 API";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
