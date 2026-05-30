package com.company.framework.security.password;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;

/**
 * 신규/변경 비밀번호 강도 검증. (행안부/KISA 가이드: 길이 + 문자종류 조합)
 * 서비스의 회원가입/비밀번호변경 흐름에서 encode 직전에 validate() 호출.
 *   policy.validate(raw);
 *   user.setPassword(passwordEncoder.encode(raw));   // 항상 BCrypt 로 저장
 */
public class PasswordPolicy {

    private final PasswordProperties props;

    public PasswordPolicy(PasswordProperties props) {
        this.props = props;
    }

    /** 위반 시 BusinessException(INVALID_INPUT) 을 던진다. */
    public void validate(String rawPassword) {
        if (!props.isPolicyEnabled()) return;
        if (rawPassword == null || rawPassword.length() < props.getMinLength()) {
            throw new BusinessException(
                    ErrorCode.Common.INVALID_INPUT, "비밀번호는 최소 " + props.getMinLength() + "자 이상이어야 합니다.");
        }
        if (countCharacterTypes(rawPassword) < props.getMinCharacterTypes()) {
            throw new BusinessException(
                    ErrorCode.Common.INVALID_INPUT,
                    "비밀번호는 영문 대/소문자, 숫자, 특수문자 중 최소 " + props.getMinCharacterTypes() + "종류를 포함해야 합니다.");
        }
    }

    private int countCharacterTypes(String s) {
        boolean upper = false, lower = false, digit = false, special = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else special = true;
        }
        int types = 0;
        if (upper) types++;
        if (lower) types++;
        if (digit) types++;
        if (special) types++;
        return types;
    }
}
