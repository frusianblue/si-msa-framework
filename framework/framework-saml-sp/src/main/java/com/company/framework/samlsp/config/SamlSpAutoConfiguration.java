package com.company.framework.samlsp.config;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.samlsp.core.SamlLogoutUserResolver;
import com.company.framework.samlsp.core.SamlUserResolver;
import com.company.framework.samlsp.slo.SamlSessionTerminator;
import com.company.framework.samlsp.slo.SamlSloService;
import com.company.framework.samlsp.store.RedisSaml2AuthenticationRequestRepository;
import com.company.framework.samlsp.token.SamlTokenIssuer;
import com.company.framework.samlsp.web.SamlAuthenticationSuccessHandler;
import com.company.framework.samlsp.web.SamlRelyingPartyRegistrations;
import com.company.framework.samlsp.web.SamlSloLogoutHandler;
import com.company.framework.security.auth.LoginService;
import com.company.framework.security.config.SecurityAutoConfiguration;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.token.TokenStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.saml2.provider.service.authentication.AbstractSaml2AuthenticationRequest;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.Saml2AuthenticationRequestRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SAML 2.0 SP 오토컨피그(3단 토글: 모듈 클래스 존재 → {@code framework.saml-sp.enabled=true} →
 * IdP 별 registrations 설정). 추가 가드 {@code @ConditionalOnBean(SamlUserResolver.class)} — 외부 신원을
 * 우리 사용자로 매핑하는 리졸버를 앱이 등록해야만 활성화(소셜 로그인 패턴과 대칭).
 *
 * <p><b>⚠️ 체인 순서(필독):</b> framework-security 의 메인 {@code securityFilterChain} 은
 * {@code @ConditionalOnMissingBean(SecurityFilterChain.class)} 다. 따라서 SAML 체인 빈을 메인보다 먼저 등록하면
 * 메인 인증 체인이 백오프되어 사라진다. 이를 막으려고 이 오토컨피그를
 * {@code @AutoConfiguration(after = SecurityAutoConfiguration.class)} 로 두어 <b>메인 체인이 먼저 등록된 뒤</b>
 * SAML 체인을 추가한다. SAML 체인은 {@code securityMatcher}(/saml2/**, /login/saml2/**) + 높은 우선순위({@link Order})로
 * 먼저 평가되고, 매처 밖 모든 요청은 메인 체인(catch-all)이 처리한다.
 *
 * <p><b>세션:</b> SP-initiated 흐름은 AuthnRequest↔Response 상관을 저장해야 한다. 기본은 SS 의 HttpSession 저장소이며,
 * 이 경우 SAML 체인은 STATELESS 로 강제하지 않는다(메인 체인만 STATELESS). 발급되는 우리 JWT 는 무상태이며, 세션은
 * SAML 핸드셰이크 동안만 쓰인다. 멀티 파드는 스티키 세션 또는 {@code request-repository=redis}(세션 없이 상관관계
 * 쿠키 + redis 공유 저장소, {@link com.company.framework.samlsp.store.RedisSaml2AuthenticationRequestRepository})를 쓴다.
 */
@AutoConfiguration(after = SecurityAutoConfiguration.class)
@ConditionalOnClass({RelyingPartyRegistrationRepository.class, SecurityFilterChain.class})
@ConditionalOnProperty(prefix = "framework.saml-sp", name = "enabled", havingValue = "true")
@ConditionalOnBean(SamlUserResolver.class)
@EnableConfigurationProperties(SamlSpProperties.class)
public class SamlSpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RelyingPartyRegistrationRepository samlRelyingPartyRegistrationRepository(SamlSpProperties properties) {
        return SamlRelyingPartyRegistrations.from(properties);
    }

    /**
     * 멀티 파드 redis AuthnRequest 저장소. {@code request-repository=redis} + 클래스패스에 {@code StringRedisTemplate}
     * (=spring-boot-starter-data-redis) 가 있을 때만 등록된다. Spring Security 7 은 컨텍스트의
     * {@link Saml2AuthenticationRequestRepository} 빈을 자동 감지해 save/load/remove 양쪽 필터에 주입하므로
     * 체인 DSL 을 건드릴 필요가 없다(미등록 시 SS 기본 HttpSession 저장소). {@code session} 이 기본이며 그 경우 이 빈은
     * 만들지 않는다(SS 기본 사용).
     *
     * <p>{@code SameSite=None} + {@code cookie-secure=false} 는 브라우저가 쿠키를 조용히 버려 ACS 콜백 상관이
     * 유실되는 함정이라 시작 시 fail-fast 한다.
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "framework.saml-sp", name = "request-repository", havingValue = "redis")
    @ConditionalOnMissingBean(Saml2AuthenticationRequestRepository.class)
    public Saml2AuthenticationRequestRepository<AbstractSaml2AuthenticationRequest>
            redisSaml2AuthenticationRequestRepository(
                    StringRedisTemplate redisTemplate,
                    RelyingPartyRegistrationRepository relyingPartyRegistrationRepository,
                    SamlSpProperties properties) {
        SamlSpProperties.Redis redis = properties.getRedis();
        if ("None".equalsIgnoreCase(redis.getCookieSameSite()) && !redis.isCookieSecure()) {
            throw new BusinessException(
                    ErrorCode.Common.INTERNAL_ERROR,
                    "framework.saml-sp.redis.cookie-same-site=None 은 cookie-secure=true 를 요구합니다"
                            + "(브라우저가 Secure 없는 SameSite=None 쿠키를 거부 → ACS 콜백에서 상관관계 유실).");
        }
        return new RedisSaml2AuthenticationRequestRepository(redisTemplate, relyingPartyRegistrationRepository, redis);
    }

    /**
     * {@code request-repository=redis} 인데 redis 저장소 빈이 만들어지지 않은 경우(=spring-boot-starter-data-redis
     * 미포함으로 위 빈의 {@code @ConditionalOnClass} 가 백오프)를 <b>조용한 session 폴백 대신 시작 실패</b>로 드러낸다.
     * redis 타입을 참조하지 않아 redis 없는 빌드에서도 안전하다. (위 redis 빈보다 <b>뒤에</b> 선언해야
     * {@code @ConditionalOnMissingBean} 이 redis 빈 등록을 인지한다.)
     */
    @Bean
    @ConditionalOnProperty(prefix = "framework.saml-sp", name = "request-repository", havingValue = "redis")
    @ConditionalOnMissingBean(Saml2AuthenticationRequestRepository.class)
    public Object samlSpRedisRepositoryRequiredGuard() {
        throw new BusinessException(
                ErrorCode.Common.INTERNAL_ERROR,
                "framework.saml-sp.request-repository=redis 인데 Saml2AuthenticationRequestRepository 빈이 없습니다. "
                        + "spring-boot-starter-data-redis(StringRedisTemplate) 를 의존성에 추가하거나 "
                        + "request-repository=session 으로 두세요.");
    }

    @Bean
    @ConditionalOnMissingBean
    public SamlTokenIssuer samlTokenIssuer(JwtProvider jwtProvider, TokenStore tokenStore) {
        return new SamlTokenIssuer.DirectSamlTokenIssuer(jwtProvider, tokenStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public SamlAuthenticationSuccessHandler samlAuthenticationSuccessHandler(
            SamlUserResolver userResolver, SamlTokenIssuer tokenIssuer, SamlSpProperties properties) {
        return new SamlAuthenticationSuccessHandler(userResolver, tokenIssuer, properties);
    }

    // ── IdP-initiated SLO(Single Logout) 수신 (6.2, slo.enabled=true 일 때) ──────────────────────────────
    //   SS 의 saml2Logout 필터가 <LogoutRequest> 서명검증·파싱·<LogoutResponse> 생성을 담당하고, 검증 후
    //   SamlSloLogoutHandler(LogoutHandler)가 NameID→우리 사용자 역매핑(SamlLogoutUserResolver)→토큰 무효화로 잇는다.

    /**
     * 기본 세션 종료기: {@code LoginService.logoutAllByUserId} 위임. {@code LoginService} 가 있을 때만 등록한다
     * (비밀번호 로그인 {@code Authenticator} 없는 SAML 전용 앱은 이 빈을 직접 등록해 무효화 경로를 제공).
     */
    @Bean
    @ConditionalOnProperty(prefix = "framework.saml-sp.slo", name = "enabled", havingValue = "true")
    @ConditionalOnBean(LoginService.class)
    @ConditionalOnMissingBean(SamlSessionTerminator.class)
    public SamlSessionTerminator samlSessionTerminator(LoginService loginService) {
        return (userId, reason) -> loginService.logoutAllByUserId(userId, null, reason);
    }

    /**
     * SLO 오케스트레이션(무상태). {@code SamlLogoutUserResolver}(앱이 NameID→우리 사용자 역매핑 구현)와 종료기가
     * 모두 있어야 활성화된다(역매핑 SPI 미등록이면 SLO 수신 비활성).
     */
    @Bean
    @ConditionalOnProperty(prefix = "framework.saml-sp.slo", name = "enabled", havingValue = "true")
    @ConditionalOnBean({SamlLogoutUserResolver.class, SamlSessionTerminator.class})
    @ConditionalOnMissingBean
    public SamlSloService samlSloService(SamlLogoutUserResolver userResolver, SamlSessionTerminator terminator) {
        return new SamlSloService(userResolver, terminator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.saml-sp.slo", name = "enabled", havingValue = "true")
    @ConditionalOnBean(SamlSloService.class)
    @ConditionalOnMissingBean
    public SamlSloLogoutHandler samlSloLogoutHandler(SamlSloService sloService) {
        return new SamlSloLogoutHandler(sloService);
    }

    /**
     * SAML 전용 체인. {@code /saml2/**}(AuthnRequest·SP 메타데이터)와 {@code /login/saml2/**}(ACS) 만 매칭하고
     * permitAll(인증 자체가 이 경로에서 일어난다). 성공 시 {@link SamlAuthenticationSuccessHandler} 가 자체 JWT 를 발급한다.
     * 메인 체인보다 우선순위가 높아(작은 Order) 먼저 평가된다.
     *
     * <p>{@code slo.enabled=true} 면 {@code /logout/saml2/**}(IdP-initiated SLO 수신)도 매칭하고 {@code saml2Logout} 을
     * 켠다. SS 필터가 LogoutRequest 검증·LogoutResponse 생성을 담당하고, 검증 후 {@link SamlSloLogoutHandler} 가 우리
     * 토큰 무효화로 잇는다(핸들러 빈이 있을 때 — {@code SamlLogoutUserResolver} 미등록이면 핸들러도 없어 SS 기본 SLO 만 동작).
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 50)
    public SecurityFilterChain samlSecurityFilterChain(
            HttpSecurity http,
            SamlAuthenticationSuccessHandler successHandler,
            SamlSpProperties properties,
            ObjectProvider<SamlSloLogoutHandler> sloLogoutHandler)
            throws Exception {

        boolean sloEnabled = properties.getSlo().isEnabled();
        String[] matchers = sloEnabled
                ? new String[] {"/saml2/**", "/login/saml2/**", "/logout/saml2/**"}
                : new String[] {"/saml2/**", "/login/saml2/**"};

        http.securityMatcher(matchers)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .saml2Login(saml2 -> saml2.successHandler(successHandler))
                // saml2Metadata 는 SS7 의 정식 SAML2 DSL(saml2Login/saml2Logout/saml2Metadata) → SP 메타데이터 자동노출.
                .saml2Metadata(Customizer.withDefaults());

        if (sloEnabled) {
            http.saml2Logout(Customizer.withDefaults());
            SamlSloLogoutHandler handler = sloLogoutHandler.getIfAvailable();
            if (handler != null) {
                // 검증된 LogoutRequest 이후 우리 JWT 블랙리스트로 잇는 브리지(OpenSAML 무의존).
                http.logout(logout -> logout.addLogoutHandler(handler));
            }
        }
        return http.build();
    }
}
