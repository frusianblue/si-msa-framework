package com.company.authsession;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 인증 방식 = 서버 세션(HttpSession + 쿠키). {@code framework.security.session.mode=session} 기본.
 *
 * <p>인증 방식별 실서비스 분리의 첫 번째(T1). JWT/OIDC/SAML 은 각각 별도 서비스
 * ({@code auth-jwt-service} / {@code auth-oidc-service} / {@code auth-saml-service})로 둔다 —
 * 서비스 목록만 봐도 어떤 인증을 쓰는지 읽히게.
 */
@SpringBootApplication
public class AuthSessionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthSessionServiceApplication.class, args);
    }
}
