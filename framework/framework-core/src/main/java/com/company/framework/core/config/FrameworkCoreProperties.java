package com.company.framework.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 코어 기능 on/off 토글. 프로젝트별로 필요한 것만 켜서 사용.
 * application.yml:
 *   framework:
 *     core:
 *       trace: true
 *       http-logging: true
 *       xss: true
 *       execution-time-aspect: true
 *       audit-aspect: true
 */
@ConfigurationProperties(prefix = "framework.core")
public class FrameworkCoreProperties {
    private boolean trace = true;
    private boolean httpLogging = true;
    private boolean xss = true;
    private boolean executionTimeAspect = true;
    private boolean auditAspect = true;

    public boolean isTrace() { return trace; }
    public void setTrace(boolean v) { this.trace = v; }
    public boolean isHttpLogging() { return httpLogging; }
    public void setHttpLogging(boolean v) { this.httpLogging = v; }
    public boolean isXss() { return xss; }
    public void setXss(boolean v) { this.xss = v; }
    public boolean isExecutionTimeAspect() { return executionTimeAspect; }
    public void setExecutionTimeAspect(boolean v) { this.executionTimeAspect = v; }
    public boolean isAuditAspect() { return auditAspect; }
    public void setAuditAspect(boolean v) { this.auditAspect = v; }
}
