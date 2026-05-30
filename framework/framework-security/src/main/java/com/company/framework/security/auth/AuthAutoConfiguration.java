package com.company.framework.security.auth;

import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.loginattempt.LoginAttemptService;
import com.company.framework.security.token.TokenStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 프로젝트가 Authenticator 빈을 등록하면 공통 로그인(LoginService + AuthController)이 자동 활성화된다.
 * 외부 인증서버에 완전 위임하는 프로젝트는 Authenticator 를 구현하지 않으면 공통 로그인이 비활성.
 * 자체 AuthController 가 필요하면 그것을 등록해 덮어쓸 수 있다(@ConditionalOnMissingBean).
 */
@AutoConfiguration
@ConditionalOnBean(Authenticator.class)
public class AuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LoginService loginService(
            Authenticator authenticator,
            JwtProvider jwtProvider,
            TokenStore tokenStore,
            LoginAttemptService loginAttempts) {
        return new LoginService(authenticator, jwtProvider, tokenStore, loginAttempts);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthController frameworkAuthController(LoginService loginService) {
        return new AuthController(loginService);
    }
}
