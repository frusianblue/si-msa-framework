package com.company.authserver;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Authorization Server (OP) 부트 앱.
 *
 * <p>우리가 외부/그룹사에 표준 OAuth2/OIDC 토큰을 발급하는 독립 배포 서비스다. 내부 1차 인증/세션은 기존 자체 JWT(framework-security)를 그대로
 * 유지하고, <b>외부/그룹사 위임 발급만</b> 이 서버를 거친다(이중 발급기 경계 = 결정 ③).
 *
 * <p>{@code @EnableScheduling}: 서명키 회전 스케줄러({@code @Scheduled})를 위해 필요. 회전 빈 자체는
 * {@code auth-server.signing-key.rotation.enabled=true} 일 때만 등록되므로, 비활성 시 스케줄 대상이 없어 무동작(켜 둬도 무해).
 *
 * <p>{@code @MapperScan(annotationClass = Mapper.class)}: {@code jose} 패키지에는 MyBatis 매퍼({@link
 * com.company.authserver.jose.SigningKeyMapper})뿐 아니라 SPI 인터페이스({@code SigningKeyCipher}/{@code SigningKeyGenerator})도
 * 있다. {@code annotationClass} 필터가 없으면 MyBatis 스캐너가 이 비매퍼 인터페이스까지 매퍼 빈으로 등록하려다, 같은 이름의
 * {@code @Bean}(예: {@code signingKeyCipher})과 {@code ConflictingBeanDefinitionException} 으로 충돌한다. {@code @Mapper}
 * 애너테이션이 붙은 인터페이스만 스캔하도록 명시한다(매퍼는 항상 {@code @Mapper} 를 붙이는 규약).
 */
@SpringBootApplication
@EnableScheduling
@MapperScan(
        basePackages = {"com.company.authserver.jose", "com.company.authserver.user"},
        annotationClass = Mapper.class)
public class AuthServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
