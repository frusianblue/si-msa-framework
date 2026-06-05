package com.company.framework.webauthn.config;

import com.company.framework.security.config.SecurityAutoConfiguration;
import com.company.framework.security.handler.RestAccessDeniedHandler;
import com.company.framework.security.handler.RestAuthenticationEntryPoint;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.token.TokenStore;
import com.company.framework.webauthn.token.DirectWebAuthnTokenIssuer;
import com.company.framework.webauthn.token.WebAuthnTokenIssuer;
import com.company.framework.webauthn.web.DefaultWebAuthnAuthenticatedUserResolver;
import com.company.framework.webauthn.web.WebAuthnAuthenticatedUserResolver;
import com.company.framework.webauthn.web.WebAuthnCredentialController;
import com.company.framework.webauthn.web.WebAuthnCredentialService;
import com.company.framework.webauthn.web.WebAuthnTokenController;
import java.util.LinkedHashSet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.management.CredentialRecordOwnerAuthorizationManager;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

/**
 * 패스키/WebAuthn 오토컨피그(SS7 네이티브 {@code http.webAuthn()} 래핑).
 *
 * <ul>
 *   <li>1단(모듈): {@code @ConditionalOnClass(WebAuthnRelyingPartyOperations)} — {@code spring-security-webauthn}
 *       아티팩트가 클래스패스에 있어야 활성(코어 web 아님 — 공식 이슈 #18377).
 *   <li>2단(기능): {@code framework.webauthn.enabled=true}(기본 false=opt-in).
 *   <li>3단(구현): 자격증명 저장소 {@code store.type=memory|jdbc} — {@code @ConditionalOnMissingBean} 으로 프로젝트
 *       override 허용.
 * </ul>
 *
 * <h3>전용 SecurityFilterChain (무상태↔ceremony 충돌 회피 — 결정 ②-b)</h3>
 * 프레임워크 주류 보안 체인은 무상태(JWT, CSRF off)라 세션+CSRF 기반 WebAuthn ceremony 와 상충한다. 따라서
 * {@code /webauthn/**}·{@code /login/webauthn}·토큰교환경로에만 적용되는 <b>세션+CSRF 전용 체인</b>을 더 높은 우선순위로
 * 둔다. 패스키 인증이 세션에 인증을 수립하면 {@link WebAuthnTokenController} 가 프레임워크 표준 JWT 로 교환하고, 이후는
 * 무상태 주류로 동작한다.
 *
 * <p><b>메인 체인 억제 회피</b>: framework-security 의 메인 체인은 {@code @ConditionalOnMissingBean(SecurityFilterChain)}
 * 가드를 갖는다. 본 오토컨피그를 {@code after = SecurityAutoConfiguration} 으로 두어 <em>메인 체인이 먼저 등록</em>되게 하고,
 * 본 전용 체인에는 그 가드를 <em>달지 않아</em> 두 체인이 공존하게 한다(전용=고우선순위 path 한정, 메인=catch-all)
 * [PITFALLS: 다중 SecurityFilterChain ↔ @ConditionalOnMissingBean 순서 함정].
 *
 * <p><b>전제(앱 제공)</b>: WebAuthn assertion 검증은 SS 의 {@code WebAuthnAuthenticationProvider} 가 {@code UserDetailsService}
 * 로 권한을 적재한다 → {@code http.webAuthn()} 는 <b>{@code UserDetailsService} 빈을 필수</b>로 요구한다(없으면 부팅 실패).
 * 또한 WebAuthn 은 HTTPS(SecureContext)에서만 동작한다(localhost 예외).
 */
@AutoConfiguration(after = SecurityAutoConfiguration.class)
@ConditionalOnClass(WebAuthnRelyingPartyOperations.class)
@ConditionalOnProperty(prefix = "framework.webauthn", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WebAuthnProperties.class)
public class WebAuthnAutoConfiguration {

    // ===================== 자격증명/사용자 저장소: memory | jdbc =====================

    @Bean
    @ConditionalOnMissingBean(PublicKeyCredentialUserEntityRepository.class)
    @ConditionalOnProperty(
            prefix = "framework.webauthn.store",
            name = "type",
            havingValue = "memory",
            matchIfMissing = true)
    public PublicKeyCredentialUserEntityRepository webAuthnUserEntityRepository() {
        return new MapPublicKeyCredentialUserEntityRepository();
    }

    @Bean
    @ConditionalOnMissingBean(UserCredentialRepository.class)
    @ConditionalOnProperty(
            prefix = "framework.webauthn.store",
            name = "type",
            havingValue = "memory",
            matchIfMissing = true)
    public UserCredentialRepository webAuthnUserCredentialRepository() {
        return new MapUserCredentialRepository();
    }

    @Bean
    @ConditionalOnClass(JdbcOperations.class)
    @ConditionalOnMissingBean(PublicKeyCredentialUserEntityRepository.class)
    @ConditionalOnProperty(prefix = "framework.webauthn.store", name = "type", havingValue = "jdbc")
    public PublicKeyCredentialUserEntityRepository jdbcWebAuthnUserEntityRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnClass(JdbcOperations.class)
    @ConditionalOnMissingBean(UserCredentialRepository.class)
    @ConditionalOnProperty(prefix = "framework.webauthn.store", name = "type", havingValue = "jdbc")
    public UserCredentialRepository jdbcWebAuthnUserCredentialRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcUserCredentialRepository(jdbcTemplate);
    }

    // ===================== Relying Party 연산(rpId/rpName/origin) =====================

    @Bean
    @ConditionalOnMissingBean(WebAuthnRelyingPartyOperations.class)
    public WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository userCredentials,
            WebAuthnProperties props) {
        PublicKeyCredentialRpEntity rp = PublicKeyCredentialRpEntity.builder()
                .id(props.getRpId())
                .name(props.resolvedRpName())
                .build();
        return new Webauthn4JRelyingPartyOperations(
                userEntities, userCredentials, rp, new LinkedHashSet<>(props.getAllowedOrigins()));
    }

    // ===================== 웹 계층: 전용 체인 + 토큰 교환 =====================

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = Type.SERVLET)
    static class WebAuthnSecurityConfig {

        /**
         * 패스키 ceremony 전용 체인(세션+CSRF). securityMatcher 로 {@code /webauthn/**}·{@code /login/webauthn}·토큰교환
         * 경로에만 적용되며, 메인(catch-all) 체인보다 높은 우선순위로 먼저 평가된다. {@code .webAuthn()} 는 컨텍스트의
         * RP/저장소/UserDetailsService 빈을 자동 픽업한다.
         */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE + 50)
        public SecurityFilterChain webAuthnSecurityFilterChain(
                HttpSecurity http,
                WebAuthnProperties props,
                RestAuthenticationEntryPoint entryPoint,
                RestAccessDeniedHandler accessDeniedHandler)
                throws Exception {
            http.securityMatcher(
                            "/webauthn/**",
                            "/login/webauthn",
                            props.getTokenPath(),
                            props.getCredentialsPath(),
                            props.getCredentialsPath() + "/**")
                    // ceremony 는 세션에 챌린지/인증을 보관 → 무상태 불가. SPA 쿠키 더블서브밋(XSRF-TOKEN)로 CSRF 유지.
                    .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                    .authorizeHttpRequests(auth -> auth
                            // 인증 전 ceremony(인증 옵션/assertion 검증)는 permitAll. 등록/등록옵션/토큰교환은 세션 인증 필요.
                            .requestMatchers("/webauthn/authenticate/options", "/login/webauthn")
                            .permitAll()
                            .anyRequest()
                            .authenticated())
                    .webAuthn(webAuthn -> {
                        webAuthn.rpId(props.getRpId()).rpName(props.resolvedRpName());
                        if (!props.getAllowedOrigins().isEmpty()) {
                            webAuthn.allowedOrigins(new LinkedHashSet<>(props.getAllowedOrigins()));
                        }
                    })
                    .exceptionHandling(
                            ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler));
            return http.build();
        }

        @Bean
        @ConditionalOnMissingBean
        public WebAuthnAuthenticatedUserResolver webAuthnAuthenticatedUserResolver() {
            return new DefaultWebAuthnAuthenticatedUserResolver();
        }

        @Bean
        @ConditionalOnBean({JwtProvider.class, TokenStore.class})
        @ConditionalOnMissingBean
        public WebAuthnTokenIssuer directWebAuthnTokenIssuer(JwtProvider jwtProvider, TokenStore tokenStore) {
            return new DirectWebAuthnTokenIssuer(jwtProvider, tokenStore);
        }

        @Bean
        @ConditionalOnBean(WebAuthnTokenIssuer.class)
        @ConditionalOnMissingBean
        public WebAuthnTokenController webAuthnTokenController(
                WebAuthnTokenIssuer tokenIssuer, WebAuthnAuthenticatedUserResolver userResolver) {
            return new WebAuthnTokenController(tokenIssuer, userResolver);
        }

        // ===================== 패스키 관리(목록/삭제) =====================

        /**
         * 삭제 소유권 검증기. 자체 비교 대신 SS7 네이티브 {@link CredentialRecordOwnerAuthorizationManager}(since 6.5.10)를
         * 그대로 쓴다 — 인증 여부·credential 존재·소유(handle 일치)를 한 번에 판정하며, 소유 아님/미존재를 모두 deny 로
         * 동일 처리해 존재 여부를 노출하지 않는다.
         */
        @Bean
        @ConditionalOnMissingBean
        public CredentialRecordOwnerAuthorizationManager webAuthnCredentialOwnerAuthorizationManager(
                UserCredentialRepository userCredentials, PublicKeyCredentialUserEntityRepository userEntities) {
            return new CredentialRecordOwnerAuthorizationManager(userCredentials, userEntities);
        }

        @Bean
        @ConditionalOnMissingBean
        public WebAuthnCredentialService webAuthnCredentialService(
                UserCredentialRepository userCredentials,
                PublicKeyCredentialUserEntityRepository userEntities,
                CredentialRecordOwnerAuthorizationManager ownerAuthorization) {
            return new WebAuthnCredentialService(userCredentials, userEntities, ownerAuthorization);
        }

        @Bean
        @ConditionalOnMissingBean
        public WebAuthnCredentialController webAuthnCredentialController(WebAuthnCredentialService credentialService) {
            return new WebAuthnCredentialController(credentialService);
        }
    }
}
