package com.company.framework.security.crypto;

import com.company.framework.security.password.BcryptEnforcingPasswordEncoder;
import com.company.framework.security.password.PasswordProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 위임형 비밀번호 인코더. 저장값 접두사로 알고리즘 식별:
 *  - 운영: {bcrypt}$2a$...   - 개발/시드: {noop}평문 (allow-noop=true 일 때만 매칭 허용)
 * 신규 인코딩 기본 = BCrypt. allow-noop=false 면 {noop} 저장 비밀번호는 매칭 거부(BCrypt 강제).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder(PasswordProperties props) {
        PasswordEncoder delegate = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new BcryptEnforcingPasswordEncoder(delegate, props.isAllowNoop());
    }
}
