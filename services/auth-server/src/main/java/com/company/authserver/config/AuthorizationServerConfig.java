package com.company.authserver.config;

import com.company.authserver.jose.AesSigningKeyCipher;
import com.company.authserver.jose.JdbcRotatingJwkSource;
import com.company.authserver.jose.RsaSigningKeyGenerator;
import com.company.authserver.jose.SigningKeyCipher;
import com.company.authserver.jose.SigningKeyGenerator;
import com.company.authserver.jose.SigningKeyMapper;
import com.company.authserver.jose.SigningKeyRotationScheduler;
import com.company.authserver.jose.SigningKeyRotationService;
import com.company.authserver.user.FrameworkAuthenticationProvider;
import com.company.authserver.user.RoleClaimTokenCustomizer;
import com.company.framework.core.crypto.AesCryptoService;
import com.company.framework.security.auth.Authenticator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
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
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

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
 *   <li>{@link JWKSource} = {@link JdbcRotatingJwkSource}(DB 공유 + 회전 오버랩 + 개인키 암호화)
 * </ul>
 *
 * <p><b>서명키 회전</b>: 키 생성/오버랩/부트스트랩은 {@link JdbcRotatingJwkSource}, 주기 회전은 {@link SigningKeyRotationScheduler}
 * (+{@link SigningKeyRotationService}) 가 담당하며 {@code @SchedulerLock}(framework-lock 리더 선출)으로 다중 파드 단일 실행을 보장한다.
 * 개인키는 {@link AesSigningKeyCipher}(framework-core {@link AesCryptoService}) 로 컬럼 암호화 저장한다. 회전 빈은
 * {@code auth-server.signing-key.rotation.enabled=true} 일 때만 등록(기본 off).
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
@EnableConfigurationProperties({AuthServerProperties.class, SigningKeyProperties.class})
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
                // 로그인 폼 리다이렉트는 브라우저(text/html) 요청에만 한정한다(SS7 정본 멀티체인 샘플 패턴).
                //   ⚠️ 무조건 authenticationEntryPoint(LoginUrl) 로 두면 ExceptionHandlingConfigurer 가 그 디폴트를
                //      그대로 반환하면서, AS configurer.init() 이 토큰/introspection/revocation 엔드포인트에 심어둔
                //      HttpStatusEntryPoint(401) 매처-매핑을 통째로 덮어쓴다 → /oauth2/token 등 API 가 401 대신
                //      302(→/login) 로 응답(=client_credentials 토큰 발급이 로그인 폼으로 튕김). (PITFALLS §9)
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                // /userinfo 등 보호 리소스는 자체 발급 JWT(access_token)로 인증.
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** (2) 그 외 요청(로그인 폼 등) 체인. 사용자 인증은 framework-security {@link Authenticator} 로 위임. */
    @Bean
    @Order(2)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, AuthenticationProvider provider)
            throws Exception {
        // ⚠️ actuator 는 반드시 미인증 허용. K8s startup/liveness/readiness 프로브(/actuator/health{,/liveness,/readiness}),
        //    로컬 compose healthcheck, Prometheus 스크레이프(/actuator/prometheus)가 토큰 없이 접근한다.
        //    이 permitAll 이 없으면 /actuator/** 가 아래 anyRequest().authenticated() + formLogin 에 걸려
        //    302(→/login)/401 이 떨어지고, 프로브/헬스체크가 영영 실패 → 컨테이너 unhealthy(부팅은 정상인데 죽음).
        //    framework-security 기본 체인(SecurityAutoConfiguration)이 user/admin/gateway 에 쓰는 규약과 동일하게 맞춘다.
        //    (노출 엔드포인트 자체는 application.yml management.endpoints.web.exposure.include 로 이미 한정됨.)
        http.authorizeHttpRequests(a -> a.requestMatchers("/actuator/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
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

    /**
     * 서명키 개인키 보호. framework-core {@link AesCryptoService}(마스터키 = framework.crypto.aes-secret) 재사용. 쓰기 암호화는
     * {@code encryption.enabled}(기본 on) 토글, 읽기 복호화는 항상 마커 인지(혼재/롤백 안전). KMS/Vault 는 이 빈만 교체.
     */
    @Bean
    @ConditionalOnMissingBean(SigningKeyCipher.class)
    SigningKeyCipher signingKeyCipher(AesCryptoService aesCryptoService, SigningKeyProperties props) {
        return new AesSigningKeyCipher(aesCryptoService, props.encryption().enabled());
    }

    /** DB 공유 + 회전 오버랩 + 개인키 암호화 JWKS. */
    @Bean
    JWKSource<SecurityContext> jwkSource(
            SigningKeyMapper signingKeyMapper, SigningKeyCipher signingKeyCipher, AuthServerProperties props) {
        return new JdbcRotatingJwkSource(signingKeyMapper, signingKeyCipher, props.jwkCacheTtl());
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

    /**
     * 서명키 회전 빈(스케줄러 + 작업 서비스 + 키 생성기). {@code auth-server.signing-key.rotation.enabled=true} 일 때만 등록.
     * 비활성(기본)이면 {@code @Scheduled} 메서드가 아예 없어 아무것도 돌지 않는다(@EnableScheduling 만으로는 무동작).
     *
     * <p>⚠️ 다중 파드에서 단일 실행을 보장하려면 {@code framework.lock.enabled=true} + {@code type=jdbc|redis} 필요(미설정 시
     * {@code @SchedulerLock} 애스펙트가 없어 모든 파드가 회전 → 경합).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "auth-server.signing-key.rotation", name = "enabled", havingValue = "true")
    static class SigningKeyRotationConfig {

        @Bean
        @ConditionalOnMissingBean(SigningKeyGenerator.class)
        SigningKeyGenerator signingKeyGenerator(SigningKeyCipher signingKeyCipher) {
            return new RsaSigningKeyGenerator(signingKeyCipher);
        }

        @Bean
        SigningKeyRotationService signingKeyRotationService(
                SigningKeyMapper mapper, SigningKeyGenerator generator, SigningKeyProperties props) {
            return new SigningKeyRotationService(
                    mapper,
                    generator,
                    props.rotation().retireGrace(),
                    props.rotation().minInterval());
        }

        @Bean
        SigningKeyRotationScheduler signingKeyRotationScheduler(SigningKeyRotationService service) {
            return new SigningKeyRotationScheduler(service);
        }
    }
}
