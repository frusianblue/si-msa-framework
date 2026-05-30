package com.company.framework.security.password;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 위임 인코더를 감싸 BCrypt 를 강제한다.
 *  - encode(): 위임 인코더의 기본(BCrypt)으로 인코딩 → 신규 비밀번호는 항상 BCrypt.
 *  - matches(): allowNoop=false 이면 {noop} 으로 저장된 평문 비밀번호는 매칭 거부(인증 실패)
 *    → 운영에서 평문 비밀번호 사용을 막고 BCrypt 재설정을 강제. (예외를 던지지 않고 false 반환)
 */
public class BcryptEnforcingPasswordEncoder implements PasswordEncoder {

    private static final String NOOP_PREFIX = "{noop}";

    private final PasswordEncoder delegate; // PasswordEncoderFactories.createDelegatingPasswordEncoder()
    private final boolean allowNoop;

    public BcryptEnforcingPasswordEncoder(PasswordEncoder delegate, boolean allowNoop) {
        this.delegate = delegate;
        this.allowNoop = allowNoop;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword); // 기본 = {bcrypt}
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (!allowNoop && encodedPassword != null && encodedPassword.startsWith(NOOP_PREFIX)) {
            return false; // BCrypt 강제: 평문 저장 비밀번호 거부
        }
        return delegate.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
