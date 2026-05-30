package com.company.framework.security.password;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 비밀번호 정책 + BCrypt 강제 설정.
 * framework:
 *   security:
 *     password:
 *       policy-enabled: true        # 신규 비밀번호 강도 검증 on/off
 *       min-length: 9               # 최소 길이
 *       min-character-types: 3      # 영대/영소/숫자/특수 중 최소 종류 수
 *       allow-noop: true            # {noop} 평문 비밀번호 허용(로컬). 운영은 false 권장(BCrypt 강제)
 */
@ConfigurationProperties(prefix = "framework.security.password")
public class PasswordProperties {
    private boolean policyEnabled = true;
    private int minLength = 9;
    private int minCharacterTypes = 3;
    private boolean allowNoop = true;

    public boolean isPolicyEnabled() {
        return policyEnabled;
    }

    public void setPolicyEnabled(boolean policyEnabled) {
        this.policyEnabled = policyEnabled;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public int getMinCharacterTypes() {
        return minCharacterTypes;
    }

    public void setMinCharacterTypes(int v) {
        this.minCharacterTypes = v;
    }

    public boolean isAllowNoop() {
        return allowNoop;
    }

    public void setAllowNoop(boolean allowNoop) {
        this.allowNoop = allowNoop;
    }
}
