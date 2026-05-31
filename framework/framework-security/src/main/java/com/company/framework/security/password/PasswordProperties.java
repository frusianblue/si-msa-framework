package com.company.framework.security.password;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 비밀번호 정책 + BCrypt 강제 + 수명주기(만료/이력) 설정.
 * framework:
 *   security:
 *     password:
 *       policy-enabled: true        # 신규 비밀번호 강도 검증 on/off
 *       min-length: 9               # 최소 길이
 *       min-character-types: 3      # 영대/영소/숫자/특수 중 최소 종류 수
 *       allow-noop: true            # {noop} 평문 비밀번호 허용(로컬). 운영은 false 권장(BCrypt 강제)
 *       expiry:                     # 비밀번호 만료(변경주기 강제) — ISMS-P
 *         enabled: false            # 2단 토글(선택적 보안기능, 기본 off)
 *         max-age: 90d              # 마지막 변경 후 이 기간 경과 시 만료(변경 요구)
 *         warn-before: 14d          # 만료 임박 경고 시작 시점(남은 기간 안내용)
 *       history:                    # 직전 N개 재사용 금지 — ISMS-P
 *         enabled: false            # 2단 토글
 *         count: 3                  # 보관/비교할 직전 비밀번호 개수
 *         store:
 *           type: memory            # 3단 토글: memory | jdbc (상호배제)
 */
@ConfigurationProperties(prefix = "framework.security.password")
public class PasswordProperties {
    private boolean policyEnabled = true;
    private int minLength = 9;
    private int minCharacterTypes = 3;
    private boolean allowNoop = true;

    private final Expiry expiry = new Expiry();
    private final History history = new History();

    /** 비밀번호 만료(변경주기) 정책. */
    public static class Expiry {
        private boolean enabled = false;
        private Duration maxAge = Duration.ofDays(90);
        private Duration warnBefore = Duration.ofDays(14);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(Duration maxAge) {
            this.maxAge = maxAge;
        }

        public Duration getWarnBefore() {
            return warnBefore;
        }

        public void setWarnBefore(Duration warnBefore) {
            this.warnBefore = warnBefore;
        }
    }

    /** 비밀번호 이력(직전 N개 재사용 금지) 정책. */
    public static class History {
        private boolean enabled = false;
        private int count = 3;
        private final Store store = new Store();

        public static class Store {
            /** memory(기본·단일인스턴스) | jdbc(영속·다중인스턴스). */
            private String type = "memory";

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

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public Store getStore() {
            return store;
        }
    }

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

    public Expiry getExpiry() {
        return expiry;
    }

    public History getHistory() {
        return history;
    }
}
