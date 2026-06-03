package com.company.framework.logmask.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그 마스킹 설정. 접두사 {@code framework.log-masking}. 모든 선택형 모듈 컨벤션대로 기본 비활성.
 *
 * <pre>
 * framework:
 *   log-masking:
 *     enabled: true            # 모듈 전체 토글(기본 false)
 *     strip-newlines: true     # CR/LF→공백(로그 인젝션 방지, 기본 true)
 *     max-length: 0            # 0 이하=무제한, 양수면 그 길이로 절단
 *     install-converter: true  # Logback %mmsg 컨버터 자동 설치(기본 true)
 *     rules:
 *       rrn: true              # 주민/외국인등록번호
 *       card: true             # 카드번호
 *       phone: true            # 휴대폰
 *       email: true            # 이메일
 *       account: false         # 계좌(오탐 위험 → 기본 off)
 *     custom-patterns:         # 사내 식별자 등 추가 정규식(매칭 전체를 별표 마스킹)
 *       employeeId: "EMP\\d{6}"
 * </pre>
 */
@ConfigurationProperties(prefix = "framework.log-masking")
public class LogMaskingProperties {

    /** 모듈 전체 활성 여부(기본 false). */
    private boolean enabled = false;

    /** CR/LF 를 공백으로 치환해 로그 인젝션/위조를 막을지(기본 true). */
    private boolean stripNewlines = true;

    /** 양수면 마스킹 전 그 길이로 메시지를 절단(과대 로그 방지). 0 이하면 무제한. */
    private int maxLength = 0;

    /** Logback {@code %mmsg} 컨버터를 자동 설치할지(기본 true). false 면 SensitiveDataMasker 빈만 노출. */
    private boolean installConverter = true;

    /** 내장 PII 규칙 on/off. */
    private Rules rules = new Rules();

    /** 추가 커스텀 정규식: 규칙명 → 정규식. 매칭 전체를 별표로 마스킹. */
    private Map<String, String> customPatterns = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStripNewlines() {
        return stripNewlines;
    }

    public void setStripNewlines(boolean stripNewlines) {
        this.stripNewlines = stripNewlines;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public boolean isInstallConverter() {
        return installConverter;
    }

    public void setInstallConverter(boolean installConverter) {
        this.installConverter = installConverter;
    }

    public Rules getRules() {
        return rules;
    }

    public void setRules(Rules rules) {
        this.rules = rules;
    }

    public Map<String, String> getCustomPatterns() {
        return customPatterns;
    }

    public void setCustomPatterns(Map<String, String> customPatterns) {
        this.customPatterns = customPatterns;
    }

    /** 내장 한국 PII 규칙 토글. 계좌만 오탐 위험으로 기본 off, 나머지 기본 on. */
    public static class Rules {
        private boolean rrn = true;
        private boolean card = true;
        private boolean phone = true;
        private boolean email = true;
        private boolean account = false;

        public boolean isRrn() {
            return rrn;
        }

        public void setRrn(boolean rrn) {
            this.rrn = rrn;
        }

        public boolean isCard() {
            return card;
        }

        public void setCard(boolean card) {
            this.card = card;
        }

        public boolean isPhone() {
            return phone;
        }

        public void setPhone(boolean phone) {
            this.phone = phone;
        }

        public boolean isEmail() {
            return email;
        }

        public void setEmail(boolean email) {
            this.email = email;
        }

        public boolean isAccount() {
            return account;
        }

        public void setAccount(boolean account) {
            this.account = account;
        }
    }
}
