package com.company.framework.security.config;

import com.company.framework.mybatis.support.CurrentUserProvider;
import com.company.framework.security.crypto.PasswordEncoderConfig;
import com.company.framework.security.devauth.DevAuthInjectionFilter;
import com.company.framework.security.devauth.DevAuthProperties;
import com.company.framework.security.devauth.DevAuthSafetyGuard;
import com.company.framework.security.handler.RestAccessDeniedHandler;
import com.company.framework.security.handler.RestAuthenticationEntryPoint;
import com.company.framework.security.jwt.JwtAuthenticationFilter;
import com.company.framework.security.jwt.JwtProperties;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.rbac.core.DynamicAuthorizationManager;
import com.company.framework.security.rbac.core.MenuService;
import com.company.framework.security.rbac.core.SecurityMetadataService;
import com.company.framework.security.rbac.mapper.SecurityMapper;
import com.company.framework.security.rbac.web.MenuController;
import com.company.framework.security.support.SecurityContextCurrentUserProvider;
import com.company.framework.security.token.TokenStore;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * 공통 보안 자동 설정.
 *  - 평상시(normal chain): JWT 인증 + DB 동적 인가 + 시큐어헤더 + 401/403 표준화
 *  - 개발 모드(dev chain): dev-auth.enabled=true 면 인증 우회 + 가짜 사용자 주입(코드 변경 0)
 * dev-auth 는 prod 프로파일에서 부팅 실패(DevAuthSafetyGuard)로 오용을 막는다.
 */
@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, FrameworkSecurityProperties.class, DevAuthProperties.class})
@ConditionalOnProperty(prefix = "framework.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("com.company.framework.security.rbac.mapper")
@Import(PasswordEncoderConfig.class)
public class SecurityAutoConfiguration {

    @Bean
    public JwtProvider jwtProvider(JwtProperties props) {
        return new JwtProvider(props);
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

    @Bean
    @Primary
    public CurrentUserProvider securityContextCurrentUserProvider() {
        return new SecurityContextCurrentUserProvider();
    }

    @Bean
    public SecurityMetadataService securityMetadataService(SecurityMapper securityMapper) {
        return new SecurityMetadataService(securityMapper);
    }

    @Bean
    public DynamicAuthorizationManager dynamicAuthorizationManager(SecurityMetadataService svc) {
        return new DynamicAuthorizationManager(svc);
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.security", name = "menu", havingValue = "true", matchIfMissing = true)
    public MenuService menuService(SecurityMapper securityMapper) {
        return new MenuService(securityMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.security", name = "menu", havingValue = "true", matchIfMissing = true)
    public MenuController menuController(MenuService menuService) {
        return new MenuController(menuService);
    }

    // ================= 개발 모드 체인 (인증 우회) =================
    @Bean
    @ConditionalOnProperty(prefix = "framework.security.dev-auth", name = "enabled", havingValue = "true")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http, DevAuthProperties devProps) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(new DevAuthInjectionFilter(devProps), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // ================= 정상 체인 (실제 인증/인가) =================
    @Bean
    @ConditionalOnProperty(prefix = "framework.security.dev-auth", name = "enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtProvider jwtProvider,
                                                   TokenStore tokenStore,
                                                   FrameworkSecurityProperties props,
                                                   DynamicAuthorizationManager dynamicAuthorizationManager,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'self'")))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/**", "/api/*/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                if (props.isDynamicAuthorization()) {
                    auth.anyRequest().access(dynamicAuthorizationManager);
                } else {
                    auth.anyRequest().authenticated();
                }
            })
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, tokenStore),
                             UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
