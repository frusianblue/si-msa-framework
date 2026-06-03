package com.company.framework.core.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   crypto:
 *     enabled: true
 *     aes-secret: "프로젝트별 비밀키(아무 문자열, SHA-256으로 256bit 키 파생)"
 *     config-decryption:
 *       enabled: true   # yaml 의 ENC(...) 설정값을 기동 시 자동 복호화(기본 on)
 */
@ConfigurationProperties(prefix = "framework.crypto")
public class CryptoProperties {
    private boolean enabled = true;
    private String aesSecret = "change-me-please-set-framework-crypto-aes-secret";
    private final ConfigDecryption configDecryption = new ConfigDecryption();

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

    public ConfigDecryption getConfigDecryption() {
        return configDecryption;
    }

    /**
     * {@code ENC(...)} 설정값 자동 복호화 토글.
     *
     * <p>실제 판정은 {@link EncryptedPropertyEnvironmentPostProcessor} 가 {@code Environment} 에서 직접 읽는다
     * (EPP 는 컨텍스트 이전이라 이 빈을 쓸 수 없음). 본 필드는 바인딩/메타데이터/문서 일관성을 위한 것이며
     * 기본값은 EPP 와 동일하게 {@code true} 이다.
     */
    public static class ConfigDecryption {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
