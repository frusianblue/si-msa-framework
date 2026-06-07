package com.example.authtypes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 인증 방식(유형) 카탈로그. 프로파일로 인증 방식을 골라 띄운다.
 *
 * <pre>
 *   ./gradlew bootRun --args='--spring.profiles.active=t1-form-session'
 * </pre>
 *
 * 인증 방식이 무엇이든 공통 보호 리소스({@code GET /api/resource})는 동일하다 — 자물쇠를 여는 방식만 다르다.
 */
@SpringBootApplication
public class AuthTypesApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthTypesApplication.class, args);
    }
}
