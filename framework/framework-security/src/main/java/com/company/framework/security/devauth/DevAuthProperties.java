package com.company.framework.security.devauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 개발 편의용 인증 우회.
 * framework:
 *   security:
 *     dev-auth:
 *       enabled: true                 # 토큰 없이 통과 + 가짜 사용자 주입 (로컬 전용!)
 *       user-id: devadmin
 *       roles: [ROLE_ADMIN, ROLE_USER]
 *       allow-header-override: true   # 요청 헤더 X-Dev-Roles 로 권한 바꿔 테스트
 */
@ConfigurationProperties(prefix = "framework.security.dev-auth")
public class DevAuthProperties {
    private boolean enabled = false;
    private String userId = "devadmin";
    private List<String> roles = List.of("ROLE_ADMIN", "ROLE_USER");
    private boolean allowHeaderOverride = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public boolean isAllowHeaderOverride() { return allowHeaderOverride; }
    public void setAllowHeaderOverride(boolean v) { this.allowHeaderOverride = v; }
}
