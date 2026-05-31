package com.company.framework.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 감사/접속 로그 적재 모듈 토글.
 * framework:
 *   audit:
 *     enabled: true               # 2단 토글(선택형 → 명시적 on 필요)
 *     method-audit: true          # @AuditLog 메서드 적재 on/off
 *     login-audit: true           # 로그인 성공/실패/로그아웃 적재 on/off
 *     store:
 *       type: logging             # 3단 토글: logging(인프라0) | jdbc(영속·조회API) [kafka 예정]
 */
@ConfigurationProperties(prefix = "framework.audit")
public class AuditProperties {

    private boolean enabled = false;
    private boolean methodAudit = true;
    private boolean loginAudit = true;
    private final Store store = new Store();

    public static class Store {
        private String type = "logging";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMethodAudit() {
        return methodAudit;
    }

    public void setMethodAudit(boolean methodAudit) {
        this.methodAudit = methodAudit;
    }

    public boolean isLoginAudit() {
        return loginAudit;
    }

    public void setLoginAudit(boolean loginAudit) {
        this.loginAudit = loginAudit;
    }

    public Store getStore() {
        return store;
    }
}
