package com.company.authserver.user;

import com.company.framework.security.auth.Authenticator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 운영/개발(=local 이 아닌 모든 프로파일)용 인증기 배선.
 *
 * <ul>
 *   <li>{@code @Profile("!local")}: local 프로파일은 {@code LocalDemo} 의 demo/demo 데모 인증기를 쓴다(상호 배타).
 *   <li>{@code @ConditionalOnMissingBean(Authenticator.class)}: 실 프로젝트가 자체 Authenticator(LDAP/AD/GPKI 등)를
 *       넣으면 그쪽이 우선하고 이 기본 DB 인증기는 양보한다(레퍼런스 기본값 의미).
 * </ul>
 *
 * <p>@Component 가 아니라 @Configuration+@Bean 으로 등록하는 이유: {@code @ConditionalOnMissingBean} 은
 * 컴포넌트 스캔 빈에서는 평가 순서가 불안정하다(@Bean 메서드에서만 신뢰 가능).
 */
@Configuration(proxyBeanMethods = false)
@Profile("!local")
public class ProdAuthenticatorConfig {

    @Bean
    @ConditionalOnMissingBean(Authenticator.class)
    Authenticator dbAuthenticator(AppUserMapper userMapper, PasswordEncoder passwordEncoder) {
        return new DbAuthenticator(userMapper, passwordEncoder);
    }
}
