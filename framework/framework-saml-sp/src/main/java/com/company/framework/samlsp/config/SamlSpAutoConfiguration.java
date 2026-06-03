package com.company.framework.samlsp.config;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.samlsp.core.SamlUserResolver;
import com.company.framework.samlsp.token.SamlTokenIssuer;
import com.company.framework.samlsp.web.SamlAuthenticationSuccessHandler;
import com.company.framework.samlsp.web.SamlRelyingPartyRegistrations;
import com.company.framework.security.config.SecurityAutoConfiguration;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.token.TokenStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
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
 * <p><b>세션:</b> SP-initiated 흐름은 AuthnRequest↔Response 상관을 위해 SS 가 HTTP 세션을 쓴다(기본
 * HttpSession 저장소). 따라서 SAML 체인은 STATELESS 로 강제하지 않는다(메인 체인만 STATELESS). 발급되는 우리
 * JWT 는 무상태이며, 세션은 SAML 핸드셰이크 동안만 쓰인다. 멀티 파드는 스티키 세션 또는 redis 기반
 * {@code Saml2AuthenticationRequestRepository}(다음 단계) 필요.
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
        // redis 기반 Saml2AuthenticationRequestRepository 는 다음 단계(미구현). 설정이 redis 면 조용한 no-op 가 되지 않게
        //   시작 시 명확히 실패시킨다. 멀티 파드는 그때까지 게이트웨이/인그레스 스티키 세션으로 핸드셰이크 구간을 묶는다.
        if (properties.getRequestRepository() == SamlSpProperties.RequestRepository.REDIS) {
            throw new BusinessException(
                    ErrorCode.Common.INTERNAL_ERROR,
                    "framework.saml-sp.request-repository=redis 는 아직 미구현입니다(다음 단계). "
                            + "현재는 session(기본) + 게이트웨이 스티키 세션으로 멀티 파드를 처리하세요.");
        }
        return SamlRelyingPartyRegistrations.from(properties);
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

    /**
     * SAML 전용 체인. {@code /saml2/**}(AuthnRequest·SP 메타데이터)와 {@code /login/saml2/**}(ACS) 만 매칭하고
     * permitAll(인증 자체가 이 경로에서 일어난다). 성공 시 {@link SamlAuthenticationSuccessHandler} 가 자체 JWT 를 발급한다.
     * 메인 체인보다 우선순위가 높아(작은 Order) 먼저 평가된다.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 50)
    public SecurityFilterChain samlSecurityFilterChain(
            HttpSecurity http, SamlAuthenticationSuccessHandler successHandler) throws Exception {
        http.securityMatcher("/saml2/**", "/login/saml2/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .saml2Login(saml2 -> saml2.successHandler(successHandler))
                // saml2Metadata 는 SS7 의 정식 SAML2 DSL(saml2Login/saml2Logout/saml2Metadata) → SP 메타데이터 자동노출.
                .saml2Metadata(Customizer.withDefaults());
        return http.build();
    }
}
