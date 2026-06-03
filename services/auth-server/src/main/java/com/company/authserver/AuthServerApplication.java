package com.company.authserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Authorization Server (OP) 부트 앱.
 *
 * <p>우리가 외부/그룹사에 표준 OAuth2/OIDC 토큰을 발급하는 독립 배포 서비스다. 내부 1차 인증/세션은 기존 자체 JWT(framework-security)를 그대로
 * 유지하고, <b>외부/그룹사 위임 발급만</b> 이 서버를 거친다(이중 발급기 경계 = 결정 ③).
 */
@SpringBootApplication
@MapperScan("com.company.authserver.jose")
public class AuthServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
