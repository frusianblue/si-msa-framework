package com.company.framework.core.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   crypto:
 *     enabled: true
 *     aes-secret: "프로젝트별 비밀키(아무 문자열, SHA-256으로 256bit 키 파생)"
 */
@ConfigurationProperties(prefix = "framework.crypto")
public class CryptoProperties {
    private boolean enabled = true;
    private String aesSecret = "change-me-please-set-framework-crypto-aes-secret";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAesSecret() {
        return aesSecret;
    }

    public void setAesSecret(String aesSecret) {
        this.aesSecret = aesSecret;
    }
}
