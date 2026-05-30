package com.company.framework.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   security:
 *     enabled: true                # 보안 자동설정 전체 on/off
 *     dynamic-authorization: true  # DB기반 동적 인가(false면 '인증만 되면 통과')
 *     menu: true                   # 메뉴 API 활성화
 */
@ConfigurationProperties(prefix = "framework.security")
public class FrameworkSecurityProperties {
    private boolean enabled = true;
    private boolean dynamicAuthorization = true;
    private boolean menu = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isDynamicAuthorization() { return dynamicAuthorization; }
    public void setDynamicAuthorization(boolean v) { this.dynamicAuthorization = v; }
    public boolean isMenu() { return menu; }
    public void setMenu(boolean menu) { this.menu = menu; }
}
