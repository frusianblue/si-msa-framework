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
 *       type: logging             # 3단 토글: logging(인프라0) | jdbc(영속·조회API) | kafka(messaging Outbox 발행)
 *     kafka:
 *       topic: audit-events       # store.type=kafka 일 때 발행 토픽 (framework-messaging 의존 필요)
 */
@ConfigurationProperties(prefix = "framework.audit")
public class AuditProperties {

    private boolean enabled = false;
    private boolean methodAudit = true;
    private boolean loginAudit = true;
    private final Store store = new Store();
    private final Kafka kafka = new Kafka();

    public static class Store {
        private String type = "logging";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /** store.type=kafka 일 때 발행 설정. (framework-messaging 의존 필요) */
    public static class Kafka {
        private String topic = "audit-events";

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
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

    public Kafka getKafka() {
        return kafka;
    }
}
