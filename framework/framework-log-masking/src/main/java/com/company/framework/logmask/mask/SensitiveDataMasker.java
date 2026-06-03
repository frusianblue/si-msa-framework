package com.company.framework.logmask.mask;

import java.util.List;

/**
 * 자유 형식 텍스트(로그 라인·감사 메시지·응답 등)에서 개인정보를 탐지·마스킹하는 핵심 엔진(Spring 무의존).
 * 규칙 목록을 순서대로 적용한다. 불변·스레드 안전.
 *
 * <p>옵션
 *
 * <ul>
 *   <li><b>stripNewlines</b>: CR/LF 를 공백으로 치환(로그 인젝션/위조 방지 — secure-web 의 CRLF-safe 로깅 원칙과 동일).
 *       기본 on. 의도적 멀티라인 메시지를 살려야 하면 off.
 *   <li><b>maxLength</b>: 양수면 그 길이로 잘라 {@code …(truncated)} 표시(과대 로그 방지). 0 이하면 무제한.
 * </ul>
 */
public final class SensitiveDataMasker {

    private final List<MaskingRule> rules;
    private final boolean stripNewlines;
    private final int maxLength;

    public SensitiveDataMasker(List<MaskingRule> rules, boolean stripNewlines, int maxLength) {
        this.rules = List.copyOf(rules);
        this.stripNewlines = stripNewlines;
        this.maxLength = maxLength;
    }

    /** 내장 한국 PII 기본 규칙(카드/주민번호/휴대폰/이메일) + CRLF 제거 + 무제한 길이. */
    public static SensitiveDataMasker withDefaults() {
        return new SensitiveDataMasker(KoreanPiiRules.defaults(), true, 0);
    }

    /** 입력 텍스트를 마스킹해 반환. null 은 null 그대로. */
    public String mask(String input) {
        if (input == null) {
            return null;
        }
        String s = input;
        if (stripNewlines) {
            s = s.replace('\r', ' ').replace('\n', ' ');
        }
        if (maxLength > 0 && s.length() > maxLength) {
            s = s.substring(0, maxLength) + "...(truncated)";
        }
        for (MaskingRule rule : rules) {
            s = rule.apply(s);
        }
        return s;
    }

    /** 진단/테스트용: 적용 순서대로의 규칙 이름. */
    public List<String> ruleNames() {
        return rules.stream().map(MaskingRule::name).toList();
    }
}
