package com.company.framework.security.crypto;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 위임형 비밀번호 인코더. 저장값 접두사로 알고리즘 식별:
 *  - 운영: {bcrypt}$2a$...   - 개발/시드: {noop}평문 (BCrypt 미계산으로 빠른 개발)
 * 신규 인코딩 기본은 BCrypt.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
