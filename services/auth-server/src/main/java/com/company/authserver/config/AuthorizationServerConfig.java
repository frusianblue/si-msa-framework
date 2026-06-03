package com.company.authserver.config;

import com.company.authserver.jose.JdbcRotatingJwkSource;
import com.company.authserver.jose.SigningKeyMapper;
import com.company.authserver.user.FrameworkAuthenticationProvider;
import com.company.authserver.user.RoleClaimTokenCustomizer;
import com.company.framework.security.auth.Authenticator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
// SS7: SAS 가 Spring Security 로 흡수되며 config 클래스가 메인 config 모듈로 이동(standalone 1.x 의 oauth2.server.authorization.config.*
// 아님).
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Authorization Server 핵심 설정.
 *
 * <p>그랜트 정책: {@code authorization_code} + <b>PKCE</b>(SS7 에서 기본 활성)와 {@code client_credentials}(서버-서버)만 채택. implicit/password 는
 * 폐기 그랜트라 미채택.
 *
 * <p>저장소(다중 파드 K8s 전제 → 전부 JDBC, 결정 ②·④):
 *
 * <ul>
 *   <li>{@link RegisteredClientRepository} = JDBC(클라이언트 등록/시크릿 해시/redirect-uri 화이트리스트)
 *   <li>{@link OAuth2AuthorizationService} = JDBC(인가/토큰 상태)
 *   <li>{@link OAuth2AuthorizationConsentService} = JDBC(동의)
 *   <li>{@link JWKSource} = {@link JdbcRotatingJwkSource}(DB 공유 + 회전 오버랩)
 * </ul>
 *
 * <p>⚠️ <b>Jackson 3</b>: SS7 의 {@code spring-security-oauth2-authorization-server} 는 기본적으로 Jackson 3(tools.jackson.*)을 쓴다.
 * {@code JdbcOAuth2AuthorizationService} 기본 매퍼가 이미 Jackson 3 + {@code OAuth2AuthorizationServerJacksonModule} 로 구성되므로
 * {@code com.fasterxml} 누수가 없다. 커스텀 principal 타입을 저장해야 한다면 {@code SecurityJacksonModules.getModules(loader)} + {@code
 * JsonMapper.builder()} 로 모듈을 만들고 {@code BasicPolymorphicTypeValidator.allowIfSubType(...)} 로 타입을 허용할 것(구버전
 * {@code SecurityJackson2Modules}/{@code ObjectMapper} 금지). 본 골격은 표준 {@code User} principal 만 써서 기본 매퍼로 안전.
 *
 * <p><b>SS7 패키지 재배치(확정, 7.0.5 소스 확인)</b>: SAS 가 Spring Security 7 로 흡수되며 config 클래스가 메인 config 모듈로 이동.
 * {@code OAuth2AuthorizationServerConfiguration} → {@code org.springframework.security.config.annotation.web.configuration},
 * {@code OAuth2AuthorizationServerConfigurer} → {@code org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization}.
 * 또한 {@code applyDefaultSecurity}/static {@code authorizationServer()} 가 없어져 {@code new OAuth2AuthorizationServerConfigurer()} +
 * {@code http.securityMatcher(getEndpointsMatcher()).with(...)} DSL 로 적용한다(standalone 1.x 와 다름).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthServerProperties.class)
public class AuthorizationServerConfig {

    /** (1) AS 프로토콜 엔드포인트 전용 체인: /oauth2/authorize, /oauth2/token, /oauth2/jwks, /.well-known/*, /userinfo, /oauth2/revoke, /oauth2/introspect. */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // SS7 에서는 applyDefaultSecurity 가 제거됨 → DSL 로 적용. 엔드포인트 매처로 이 체인 범위를 AS 엔드포인트로 한정.
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, as -> as.oidc(Customizer.withDefaults())) // OIDC 활성
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                // 미인증 사용자 대면 요청은 우리 로그인 페이지로.
                .exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                // /userinfo 등 보호 리소스는 자체 발급 JWT(access_token)로 인증.
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** (2) 그 외 요청(로그인 폼 등) 체인. 사용자 인증은 framework-security {@link Authenticator} 로 위임. */
    @Bean
    @Order(2)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, AuthenticationProvider provider)
            throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .authenticationProvider(provider)
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    /** 폼 로그인 → 우리 인증 계약. */
    @Bean
    AuthenticationProvider frameworkAuthenticationProvider(Authenticator authenticator) {
        return new FrameworkAuthenticationProvider(authenticator);
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        // 기본 매퍼 = Jackson 3(SS7). 위 클래스 javadoc 의 Jackson 3 주의 참조.
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /** DB 공유 + 회전 오버랩 JWKS. */
    @Bean
    JWKSource<SecurityContext> jwkSource(SigningKeyMapper signingKeyMapper, AuthServerProperties props) {
        return new JdbcRotatingJwkSource(signingKeyMapper, props.jwkCacheTtl());
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /** 발급자(iss). discovery/토큰에 박히므로 외부 접근 가능한 안정 URL. */
    @Bean
    AuthorizationServerSettings authorizationServerSettings(AuthServerProperties props) {
        return AuthorizationServerSettings.builder().issuer(props.issuer()).build();
    }

    /** 발급 토큰에 우리 roles 클레임 부여. */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return new RoleClaimTokenCustomizer();
    }

    /** 클라이언트 시크릿 해시 검증용. framework-security 가 이미 PasswordEncoder 를 제공하면 그쪽이 우선. */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
