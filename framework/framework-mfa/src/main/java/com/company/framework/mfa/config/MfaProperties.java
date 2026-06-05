package com.company.framework.mfa.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * MFA 설정. 모듈은 기본 비활성(framework.mfa.enabled=false)이며, 하위 방식(totp/otp)도 같은 토글 규약을 따른다.
 *
 * <pre>
 * framework:
 *   mfa:
 *     enabled: false
 *     issuer: "si-msa"        # otpauth 라벨(인증기 앱 표시명)
 *     policy: enrolled        # enrolled(확정 등록 있으면 요구) | off(요구 안 함, 등록/검증 API 만 제공)
 *     challenge:
 *       ttl: PT5M
 *       max-attempts: 5
 *       store: { type: memory, key-prefix: "mfa:chal:" }   # memory | redis
 *     totp:
 *       enabled: true
 *       algorithm: SHA1       # SHA1 | SHA256 | SHA512
 *       digits: 6
 *       period-seconds: 30
 *       window: 1             # 시계오차 허용 스텝(±)
 *       secret-length-bytes: 20
 *       recovery-codes: 10
 *     otp:
 *       enabled: false
 *       length: 6
 *       auto-send: true
 *     enrollment:
 *       store: { type: memory }   # memory | jdbc
 * </pre>
 */
@ConfigurationProperties(prefix = "framework.mfa")
public class MfaProperties {

    /** 모듈 전체 on/off. */
    private boolean enabled = false;

    /** otpauth URI 의 issuer 라벨(인증기 앱에 표시). */
    private String issuer = "si-msa";

    /** 로그인 시 2차 인증 요구 정책. enrolled=확정 등록 보유 사용자에게만 요구. */
    private Policy policy = Policy.ENROLLED;

    @NestedConfigurationProperty
    private Challenge challenge = new Challenge();

    @NestedConfigurationProperty
    private Totp totp = new Totp();

    @NestedConfigurationProperty
    private Otp otp = new Otp();

    @NestedConfigurationProperty
    private Enrollment enrollment = new Enrollment();

    @NestedConfigurationProperty
    private Webauthn webauthn = new Webauthn();

    public enum Policy {
        ENROLLED,
        OFF
    }

    public static class Challenge {
        private Duration ttl = Duration.ofMinutes(5);
        private int maxAttempts = 5;

        @NestedConfigurationProperty
        private Store store = new Store("memory");

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Store getStore() {
            return store;
        }

        public void setStore(Store store) {
            this.store = store;
        }
    }

    public static class Store {
        private String type;
        private String keyPrefix = "mfa:chal:";

        public Store() {}

        public Store(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    public static class Totp {
        private boolean enabled = true;
        private String algorithm = "SHA1";
        private int digits = 6;
        private int periodSeconds = 30;
        private int window = 1;
        private int secretLengthBytes = 20;
        private int recoveryCodes = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public int getDigits() {
            return digits;
        }

        public void setDigits(int digits) {
            this.digits = digits;
        }

        public int getPeriodSeconds() {
            return periodSeconds;
        }

        public void setPeriodSeconds(int periodSeconds) {
            this.periodSeconds = periodSeconds;
        }

        public int getWindow() {
            return window;
        }

        public void setWindow(int window) {
            this.window = window;
        }

        public int getSecretLengthBytes() {
            return secretLengthBytes;
        }

        public void setSecretLengthBytes(int secretLengthBytes) {
            this.secretLengthBytes = secretLengthBytes;
        }

        public int getRecoveryCodes() {
            return recoveryCodes;
        }

        public void setRecoveryCodes(int recoveryCodes) {
            this.recoveryCodes = recoveryCodes;
        }
    }

    public static class Otp {
        private boolean enabled = false;
        private int length = 6;
        private boolean autoSend = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public boolean isAutoSend() {
            return autoSend;
        }

        public void setAutoSend(boolean autoSend) {
            this.autoSend = autoSend;
        }
    }

    public static class Enrollment {
        @NestedConfigurationProperty
        private Store store = new Store("memory");

        public Store getStore() {
            return store;
        }

        public void setStore(Store store) {
            this.store = store;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    public void setChallenge(Challenge challenge) {
        this.challenge = challenge;
    }

    public Totp getTotp() {
        return totp;
    }

    public void setTotp(Totp totp) {
        this.totp = totp;
    }

    public Otp getOtp() {
        return otp;
    }

    public void setOtp(Otp otp) {
        this.otp = otp;
    }

    public Enrollment getEnrollment() {
        return enrollment;
    }

    public void setEnrollment(Enrollment enrollment) {
        this.enrollment = enrollment;
    }

    public Webauthn getWebauthn() {
        return webauthn;
    }

    public void setWebauthn(Webauthn webauthn) {
        this.webauthn = webauthn;
    }

    /**
     * WebAuthn 2차 인증 방식 설정. 등록/검증 ceremony 는 framework-webauthn 의 RP 연산/저장소를 재사용하므로,
     * 이 방식이 실제로 활성화되려면 {@code enabled=true} <b>이면서</b> framework-webauthn 이 활성(RP 빈 존재)이어야 한다.
     * rpId/origin·자격증명 저장소는 framework-webauthn 설정({@code framework.webauthn.*})을 따른다(중복 설정 없음).
     */
    public static class Webauthn {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
