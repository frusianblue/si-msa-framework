package com.company.framework.oauthclient.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.oauthclient.core.OAuthUserInfo;
import com.company.framework.oauthclient.core.OAuthUserResolver;
import com.company.framework.oauthclient.token.OAuthTokenIssuer;
import com.company.framework.oauthclient.web.OAuthController;
import com.company.framework.oauthclient.web.OAuthLoginService;
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
 * 3단 토글 동작 검증: 기본 비활성 / enabled=true + resolver 빈 있을 때만 활성 / resolver 없으면 백오프.
 * 토큰 발급 의존(JwtProvider/TokenStore)은 테스트용 스텁으로 제공한다.
 */
class OAuthClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OAuthClientAutoConfiguration.class))
            .withUserConfiguration(TokenStubConfig.class);

    @Test
    void disabled_by_default() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(OAuthLoginService.class));
    }

    @Test
    void enabled_but_no_resolver_backs_off() {
        runner.withPropertyValues("framework.oauth-client.enabled=true")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OAuthLoginService.class));
    }

    @Test
    void enabled_with_resolver_activates() {
        runner.withPropertyValues("framework.oauth-client.enabled=true")
                .withUserConfiguration(ResolverConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(OAuthLoginService.class);
                    assertThat(ctx).hasSingleBean(OAuthController.class);
                    assertThat(ctx).hasSingleBean(OAuthTokenIssuer.class);
                });
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
        OAuthUserResolver resolver() {
            return (OAuthUserInfo info) -> new AuthenticatedUser(info.providerId(), info.name(), List.of("USER"));
        }
    }
}
