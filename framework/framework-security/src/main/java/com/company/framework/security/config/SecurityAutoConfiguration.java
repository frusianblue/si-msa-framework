package com.company.framework.security.config;

import com.company.framework.security.auth.Authenticator;
import com.company.framework.security.auth.session.SessionAuthController;
import com.company.framework.security.auth.session.SessionAuthService;
import com.company.framework.security.concurrent.ConcurrentSessionProperties;
import com.company.framework.security.concurrent.ConcurrentSessionService;
import com.company.framework.security.concurrent.InMemoryConcurrentSessionService;
import com.company.framework.security.concurrent.JdbcConcurrentSessionService;
import com.company.framework.security.crypto.PasswordEncoderConfig;
import com.company.framework.security.devauth.DevAuthInjectionFilter;
import com.company.framework.security.devauth.DevAuthProperties;
import com.company.framework.security.devauth.DevAuthSafetyGuard;
import com.company.framework.security.handler.RestAccessDeniedHandler;
import com.company.framework.security.handler.RestAuthenticationEntryPoint;
import com.company.framework.security.jwt.DownstreamTokenAuthenticator;
import com.company.framework.security.jwt.JwtAuthenticationFilter;
import com.company.framework.security.jwt.JwtProperties;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.jwt.JwtSecretSafetyGuard;
import com.company.framework.security.jwt.ResourceServerJwtVerifier;
import com.company.framework.security.loginattempt.InMemoryLoginAttemptService;
import com.company.framework.security.loginattempt.LoginAttemptProperties;
import com.company.framework.security.loginattempt.LoginAttemptService;
import com.company.framework.security.password.InMemoryPasswordHistoryStore;
import com.company.framework.security.password.JdbcPasswordHistoryStore;
import com.company.framework.security.password.PasswordHistoryStore;
import com.company.framework.security.password.PasswordLifecycleService;
import com.company.framework.security.password.PasswordPolicy;
import com.company.framework.security.password.PasswordProperties;
import com.company.framework.security.password.PasswordSafetyGuard;
import com.company.framework.security.rbac.core.DynamicAuthorizationManager;
import com.company.framework.security.rbac.core.MenuService;
import com.company.framework.security.rbac.core.SecurityMetadataService;
import com.company.framework.security.rbac.spi.MenuProvider;
import com.company.framework.security.rbac.spi.RbacProviderSafetyGuard;
import com.company.framework.security.rbac.spi.ResourceMetadataProvider;
import com.company.framework.security.rbac.web.MenuController;
import com.company.framework.security.token.TokenStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 공통 보안 자동 설정.
 *  - 평상시(normal chain): JWT 인증 + DB 동적 인가 + 시큐어헤더 + 401/403 표준화
 *  - 개발 모드(dev chain): dev-auth.enabled=true 면 인증 우회 + 가짜 사용자 주입(코드 변경 0)
 * dev-auth 는 prod 프로파일에서 부팅 실패(DevAuthSafetyGuard)로 오용을 막는다.
 */
@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({
    JwtProperties.class,
    FrameworkSecurityProperties.class,
    DevAuthProperties.class,
    PasswordProperties.class,
    LoginAttemptProperties.class,
    ConcurrentSessionProperties.class
})
@ConditionalOnProperty(prefix = "framework.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(PasswordEncoderConfig.class)
public class SecurityAutoConfiguration {

    @Bean
    public JwtProvider jwtProvider(JwtProperties props) {
        return new JwtProvider(props);
    }

    /**
     * 리소스 서버 검증기(이중 발급기 — AUTH_SERVER.md §4). {@code framework.security.resource-server.enabled=true} 일 때만
     * 등록 → zero-trust 다운스트림 재검증에서 AS(OP) RS256/JWKS 토큰을 받는다. issuer 가 비면 fail-fast.
     */
    @Bean
    @ConditionalOnProperty(prefix = "framework.security.resource-server", name = "enabled", havingValue = "true")
    public ResourceServerJwtVerifier resourceServerJwtVerifier(FrameworkSecurityProperties props) {
        FrameworkSecurityProperties.ResourceServer rs = props.getResourceServer();
        if (!StringUtils.hasText(rs.getIssuer())) {
            throw new IllegalStateException(
                    "framework.security.resource-server.enabled=true 이면 issuer 가 필요합니다(= auth-server.issuer 와 동일 값).");
        }
        String jwksUri = rs.resolvedJwksUri();
        if (!StringUtils.hasText(jwksUri)) {
            throw new IllegalStateException("framework.security.resource-server.jwks-uri 를 해석할 수 없습니다(issuer 확인).");
        }
        return new ResourceServerJwtVerifier(
                RestClient.create(),
                rs.getIssuer(),
                jwksUri,
                rs.getRolesClaim(),
                rs.getAudience(),
                rs.getClockSkew(),
                rs.getJwkCacheTtl());
    }

    /**
     * 다운스트림 발급자 분기 인증기. 리소스 서버 검증기가 있으면 AS 토큰(iss 일치)을 JWKS 로, 그 외는 자체 JWT(HMAC)로
     * 재검증한다. 검증기가 없으면(기본) 항상 자체 JWT 경로 — 도입 전 동작과 동일.
     */
    @Bean
    public DownstreamTokenAuthenticator downstreamTokenAuthenticator(
            JwtProvider jwtProvider, ObjectProvider<ResourceServerJwtVerifier> resourceServerVerifier) {
        return new DownstreamTokenAuthenticator(jwtProvider, resourceServerVerifier.getIfAvailable());
    }

    /** prod 에서 기본/약한 JWT 시크릿이면 부팅 실패시키는 안전장치(dev-auth 가드와 동일 패턴). */
    @Bean
    public JwtSecretSafetyGuard jwtSecretSafetyGuard(JwtProperties props, Environment env) {
        return new JwtSecretSafetyGuard(props, env);
    }

    @Bean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
        return new RestAuthenticationEntryPoint();
    }

    @Bean
    public RestAccessDeniedHandler restAccessDeniedHandler() {
        return new RestAccessDeniedHandler();
    }

    @Bean
    public DevAuthSafetyGuard devAuthSafetyGuard(DevAuthProperties devProps, Environment env) {
        return new DevAuthSafetyGuard(devProps, env);
    }

    // ================= RBAC 영속(동적 인가/메뉴) — 포트/어댑터(SPI) =================
    // 코어는 RBAC 포트(ResourceMetadataProvider/MenuProvider)만 알고, 구현(MyBatis 등)은 어댑터 모듈이 제공한다
    // (framework-security-rbac-mybatis). 인증만 쓰는 서비스는 어댑터를 의존하지 않으면 DataSource/MyBatis 불필요.
    // 감사 브리지(SecurityContextCurrentUserProvider, @Primary CurrentUserProvider)도 어댑터로 이전됐다.

    /** RBAC provider 가 있을 때만 활성. 어댑터가 SecurityAutoConfiguration 보다 먼저 로딩(before=)되어 정확히 인식된다. */
    @Bean
    @ConditionalOnBean(ResourceMetadataProvider.class)
    public SecurityMetadataService securityMetadataService(ResourceMetadataProvider resourceMetadataProvider) {
        return new SecurityMetadataService(resourceMetadataProvider);
    }

    @Bean
    @ConditionalOnBean(SecurityMetadataService.class)
    public DynamicAuthorizationManager dynamicAuthorizationManager(SecurityMetadataService svc) {
        return new DynamicAuthorizationManager(svc);
    }

    @Bean
    @ConditionalOnBean(MenuProvider.class)
    @ConditionalOnProperty(prefix = "framework.security", name = "menu", havingValue = "true", matchIfMissing = true)
    public MenuService menuService(MenuProvider menuProvider) {
        return new MenuService(menuProvider);
    }

    @Bean
    @ConditionalOnBean(MenuService.class)
    @ConditionalOnProperty(prefix = "framework.security", name = "menu", havingValue = "true", matchIfMissing = true)
    public MenuController menuController(MenuService menuService) {
        return new MenuController(menuService);
    }

    /**
     * fail-fast: dynamic-authorization=true(기본) 인데 RBAC provider 어댑터가 없으면 부팅 실패.
     * 동적 인가가 조용히 무력화(매핑 0건=전부 허용)되는 운영 사고를 차단(프로파일 무관). 가드 빈 생성 즉시 throw.
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "framework.security",
            name = "dynamic-authorization",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(ResourceMetadataProvider.class)
    public RbacProviderSafetyGuard rbacProviderSafetyGuard() {
        return new RbacProviderSafetyGuard();
    }

    // ================= 개발 모드 체인 (인증 우회) =================
    @Bean
    @ConditionalOnProperty(prefix = "framework.security.dev-auth", name = "enabled", havingValue = "true")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http, DevAuthProperties devProps) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new DevAuthInjectionFilter(devProps), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordPolicy passwordPolicy(PasswordProperties props) {
        return new PasswordPolicy(props);
    }

    // ===== 비밀번호 수명주기(만료/이력) — ISMS-P. 기능은 PasswordProperties.expiry/history 로 토글 =====
    @Bean
    @ConditionalOnMissingBean(PasswordHistoryStore.class)
    @ConditionalOnProperty(
            prefix = "framework.security.password.history.store",
            name = "type",
            havingValue = "memory",
            matchIfMissing = true)
    public PasswordHistoryStore inMemoryPasswordHistoryStore() {
        return new InMemoryPasswordHistoryStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordLifecycleService passwordLifecycleService(
            PasswordHistoryStore historyStore, PasswordEncoder passwordEncoder, PasswordProperties props) {
        return new PasswordLifecycleService(historyStore, passwordEncoder, props);
    }

    // ===== 동시(중복) 로그인 제어 — ISMS-P. 기능은 framework.security.concurrent-session.enabled 로 토글 =====
    @Bean
    @ConditionalOnMissingBean(ConcurrentSessionService.class)
    @ConditionalOnProperty(
            prefix = "framework.security.concurrent-session.store",
            name = "type",
            havingValue = "memory",
            matchIfMissing = true)
    public ConcurrentSessionService inMemoryConcurrentSessionService(ConcurrentSessionProperties props) {
        return new InMemoryConcurrentSessionService(props);
    }

    @Bean
    @ConditionalOnMissingBean(LoginAttemptService.class)
    public LoginAttemptService loginAttemptService(LoginAttemptProperties props) {
        return new InMemoryLoginAttemptService(props);
    }

    @Bean
    public PasswordSafetyGuard passwordSafetyGuard(PasswordProperties props, Environment env) {
        return new PasswordSafetyGuard(props, env);
    }

    private static void applyCommonHardening(
            HttpSecurity http, RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(
                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(
                                csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'self'")))
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler));
    }

    // ================= JDBC 백엔드 저장소 (선택) — spring-jdbc(JdbcTemplate) 있을 때만 =================
    // 보안 자체의 JDBC 저장소(비번이력·동시세션)는 host 앱이 spring-jdbc 를 제공할 때만 등록한다.
    // (보안-영속 결합 분리: 인증만 쓰는 서비스는 spring-jdbc 가 없어 이 설정이 통째로 백오프 → DataSource 불필요.)
    // 톱레벨이 아니라 nested + 클래스레벨 @ConditionalOnClass 로 둬야, JdbcTemplate 부재 시 메서드 introspection 으로 깨지지 않는다.
    // (token-store=jdbc 는 TokenStoreAutoConfiguration 이 동일 방식으로 가드. memory 기본값은 톱레벨 유지.)
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    static class JdbcSecurityStoreConfig {

        @Bean
        @ConditionalOnMissingBean(PasswordHistoryStore.class)
        @ConditionalOnProperty(
                prefix = "framework.security.password.history.store",
                name = "type",
                havingValue = "jdbc")
        public PasswordHistoryStore jdbcPasswordHistoryStore(JdbcTemplate jdbcTemplate) {
            return new JdbcPasswordHistoryStore(jdbcTemplate);
        }

        @Bean
        @ConditionalOnMissingBean(ConcurrentSessionService.class)
        @ConditionalOnProperty(
                prefix = "framework.security.concurrent-session.store",
                name = "type",
                havingValue = "jdbc")
        public ConcurrentSessionService jdbcConcurrentSessionService(
                JdbcTemplate jdbcTemplate, ConcurrentSessionProperties props) {
            return new JdbcConcurrentSessionService(jdbcTemplate, props);
        }
    }

    // ================= 정상 체인 — 무상태(JWT) : framework.security.session.mode=stateless(기본) =================
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "framework.security.session",
            name = "mode",
            havingValue = "stateless",
            matchIfMissing = true)
    static class StatelessChainConfig {

        @Bean
        @ConditionalOnProperty(
                prefix = "framework.security.dev-auth",
                name = "enabled",
                havingValue = "false",
                matchIfMissing = true)
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        public SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                DownstreamTokenAuthenticator downstreamAuthenticator,
                TokenStore tokenStore,
                FrameworkSecurityProperties props,
                ObjectProvider<DynamicAuthorizationManager> dynamicAuthorizationManager,
                RestAuthenticationEntryPoint entryPoint,
                RestAccessDeniedHandler accessDeniedHandler)
                throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
            applyCommonHardening(http, entryPoint, accessDeniedHandler);
            http.authorizeHttpRequests(auth -> {
                        auth.requestMatchers("/actuator/**", "/api/*/auth/**", "/swagger-ui/**", "/v3/api-docs/**")
                                .permitAll();
                        if (props.isDynamicAuthorization()) {
                            // dynamic-authorization=true → RBAC provider 필수(없으면 RbacProviderSafetyGuard 가 부팅 차단).
                            auth.anyRequest().access(dynamicAuthorizationManager.getObject());
                        } else {
                            auth.anyRequest().authenticated();
                        }
                    })
                    .addFilterBefore(
                            new JwtAuthenticationFilter(
                                    downstreamAuthenticator,
                                    tokenStore,
                                    props.getEdgeTrust().getMode()
                                                    == FrameworkSecurityProperties.EdgeTrust.Mode.GATEWAY_HEADERS
                                            ? JwtAuthenticationFilter.Mode.GATEWAY_HEADERS
                                            : JwtAuthenticationFilter.Mode.ZERO_TRUST,
                                    props.getEdgeTrust().getUserIdHeader(),
                                    props.getEdgeTrust().getRolesHeader()),
                            UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }

    // ================= 정상 체인 — 서버 세션 : framework.security.session.mode=session =================
    // 무상태 대신 HttpSession 에 SecurityContext 저장(쿠키 세션ID). JwtAuthenticationFilter 미장착 —
    // 인증은 세션 로그인(SessionAuthController)이 수립하고, 매 요청은 SecurityContextHolderFilter 가 세션에서 복원한다.
    // 권한은 무상태 경로와 동일한 ROLE_* 형태 → RBAC/@PreAuthorize 동일 동작.
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "framework.security.session", name = "mode", havingValue = "session")
    static class SessionChainConfig {

        /** 세션 컨텍스트 저장소. SessionAuthController(쓰기)와 체인(읽기)이 동일 인스턴스를 공유하도록 빈으로 노출. */
        @Bean
        @ConditionalOnMissingBean(SecurityContextRepository.class)
        public SecurityContextRepository securityContextRepository() {
            return new HttpSessionSecurityContextRepository();
        }

        @Bean
        @ConditionalOnProperty(
                prefix = "framework.security.dev-auth",
                name = "enabled",
                havingValue = "false",
                matchIfMissing = true)
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        public SecurityFilterChain sessionSecurityFilterChain(
                HttpSecurity http,
                FrameworkSecurityProperties props,
                SecurityContextRepository securityContextRepository,
                ObjectProvider<DynamicAuthorizationManager> dynamicAuthorizationManager,
                RestAuthenticationEntryPoint entryPoint,
                RestAccessDeniedHandler accessDeniedHandler)
                throws Exception {
            if (props.getSession().isCsrf()) {
                // SPA 쿠키 더블서브밋(XSRF-TOKEN). 평문 핸들러 — 기본 XOR(BREACH 방어)은 쿠키의 원시 토큰을
                // 그대로 헤더로 보내는 SPA 와 불일치(403)를 일으키므로 명시. 인증 부트스트랩 엔드포인트는 토큰 발급 전이라 면제.
                http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/*/auth/**"));
            } else {
                http.csrf(AbstractHttpConfigurer::disable);
            }
            http.formLogin(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                    .securityContext(sc -> sc.securityContextRepository(securityContextRepository));
            applyCommonHardening(http, entryPoint, accessDeniedHandler);
            http.authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/**", "/api/*/auth/**", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll();
                if (props.isDynamicAuthorization()) {
                    // dynamic-authorization=true → RBAC provider 필수(없으면 RbacProviderSafetyGuard 가 부팅 차단).
                    auth.anyRequest().access(dynamicAuthorizationManager.getObject());
                } else {
                    auth.anyRequest().authenticated();
                }
            });
            return http.build();
        }

        /** 프로젝트가 Authenticator 를 등록하면 세션 로그인 흐름이 자동 활성(JWT 경로의 AuthAutoConfiguration 과 동일 사상). */
        @Bean
        @ConditionalOnBean(Authenticator.class)
        @ConditionalOnMissingBean
        public SessionAuthService sessionAuthService(
                Authenticator authenticator,
                LoginAttemptService loginAttempts,
                SecurityContextRepository securityContextRepository,
                ApplicationEventPublisher eventPublisher) {
            return new SessionAuthService(authenticator, loginAttempts, securityContextRepository, eventPublisher);
        }

        @Bean
        @ConditionalOnBean(SessionAuthService.class)
        @ConditionalOnMissingBean
        public SessionAuthController sessionAuthController(
                SessionAuthService sessionAuthService, LoginAttemptProperties loginAttemptProperties) {
            return new SessionAuthController(sessionAuthService, loginAttemptProperties);
        }
    }
}
