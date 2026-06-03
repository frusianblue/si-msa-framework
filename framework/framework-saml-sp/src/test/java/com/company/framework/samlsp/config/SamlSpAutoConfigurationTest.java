package com.company.framework.samlsp.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.samlsp.core.SamlUserInfo;
import com.company.framework.samlsp.core.SamlUserResolver;
import com.company.framework.samlsp.token.SamlTokenIssuer;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.jwt.JwtProperties;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.token.InMemoryTokenStore;
import com.company.framework.security.token.TokenStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 3단 토글 "레지스트레이션 가드" 검증: 모듈이 <b>명시적으로 켜지고 + 앱이 {@link SamlUserResolver} 를 등록했을 때만</b>
 * 활성화됨을 보장한다(소셜 로그인 오토컨피그와 대칭).
 *
 * <p><b>범위 주의:</b> 양성(full-activation) 경로는 {@code samlSecurityFilterChain} 빈이 {@code HttpSecurity} 와
 * {@code RelyingPartyRegistrationRepository}(=IdP 메타데이터 네트워크 fetch)를 요구하므로 비-웹 단위 러너에서는
 * 안정적으로 켤 수 없다. 따라서 여기서는 <b>웹 체인을 인스턴스화하지 않는 백오프(off) 케이스</b>에 집중한다
 * (모듈이 의도치 않게 켜지지 않음을 보장하는 것이 가드 테스트의 핵심). 전체 와이어링/서명검증은 받는 쪽 통합/archtest
 * 와 실제 앱 기동으로 검증한다(작성 환경 Maven/Shibboleth 차단 → SAML 본체 컴파일 불가, sshd/MINA 와 동일 패턴).
 */
class SamlSpAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SamlSpAutoConfiguration.class))
            .withUserConfiguration(TokenStubConfig.class);

    @Test
    void disabled_by_default() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(SamlTokenIssuer.class));
    }

    @Test
    void enabled_but_no_resolver_backs_off() {
        runner.withPropertyValues("framework.saml-sp.enabled=true")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SamlTokenIssuer.class));
    }

    @Test
    void enabled_with_resolver_but_disabled_property_still_off() {
        // enabled 미설정(기본 false) + resolver 있어도 @ConditionalOnProperty 미충족 → 여전히 off.
        runner.withUserConfiguration(ResolverConfig.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SamlTokenIssuer.class));
    }

    @Configuration
    static class TokenStubConfig {
        @Bean
        JwtProvider jwtProvider() {
            return new JwtProvider(new JwtProperties("0123456789012345678901234567890123456789", 1800, 1209600));
        }

        @Bean
        TokenStore tokenStore() {
            return new InMemoryTokenStore();
        }
    }

    @Configuration
    static class ResolverConfig {
        @Bean
        SamlUserResolver resolver() {
            return (SamlUserInfo info) -> new AuthenticatedUser(info.nameId(), info.name(), List.of("USER"));
        }
    }
}
