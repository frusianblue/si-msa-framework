package com.company.framework.mfa.config;

import com.company.framework.mfa.core.DefaultMfaGate;
import com.company.framework.mfa.core.MfaService;
import com.company.framework.mfa.core.MfaWebAuthnService;
import com.company.framework.mfa.core.MfaWebAuthnSupport;
import com.company.framework.mfa.otp.OtpSender;
import com.company.framework.mfa.store.InMemoryMfaChallengeStore;
import com.company.framework.mfa.store.InMemoryMfaEnrollmentStore;
import com.company.framework.mfa.store.JdbcMfaEnrollmentStore;
import com.company.framework.mfa.store.MfaChallengeStore;
import com.company.framework.mfa.store.MfaEnrollmentStore;
import com.company.framework.mfa.store.RedisMfaChallengeStore;
import com.company.framework.mfa.totp.Totp;
import com.company.framework.mfa.totp.TotpSecretGenerator;
import com.company.framework.mfa.web.MfaEnrollmentController;
import com.company.framework.mfa.web.MfaVerificationController;
import com.company.framework.mfa.web.MfaWebAuthnController;
import com.company.framework.mybatis.support.CurrentUserProvider;
import com.company.framework.security.auth.LoginService;
import com.company.framework.security.auth.MfaGate;
import com.company.framework.security.loginattempt.LoginAttemptProperties;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

/**
 * 2단계 인증(MFA) 오토컨피그.
 *
 * <ul>
 *   <li>1단(모듈): {@code @ConditionalOnClass(MfaService)} — 이 모듈을 의존성에 넣어야 활성.
 *   <li>2단(기능): {@code framework.mfa.enabled=true}.
 *   <li>3단(구현): 등록 저장소 {@code enrollment.store.type=memory|jdbc}, 챌린지 저장소
 *       {@code challenge.store.type=memory|redis} — 각각 {@code @ConditionalOnMissingBean} 으로 프로젝트 override 허용.
 * </ul>
 *
 * <p>{@link DefaultMfaGate} 는 framework-security 의 {@link MfaGate} 구현으로 등록되어, LoginService 가
 * {@code ObjectProvider<MfaGate>} 로 선택 주입한다(의존 방향 mfa → security 단방향). MFA 미의존/비활성 환경에서는
 * 이 빈이 없으므로 LoginService 는 단일단계 로그인으로 동작한다(완전 하위호환).
 *
 * <p>검증 컨트롤러는 {@code completeMfa} 를 위해 {@link LoginService} 가 필요하므로
 * {@code @ConditionalOnBean(LoginService.class)} 로 가드한다(Authenticator 빈이 있는 프로젝트에서만 활성).
 */
@AutoConfiguration
@ConditionalOnClass(MfaService.class)
@ConditionalOnProperty(prefix = "framework.mfa", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MfaProperties.class)
public class MfaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Totp mfaTotp(MfaProperties props) {
        MfaProperties.Totp t = props.getTotp();
        return new Totp(t.getAlgorithm(), t.getDigits(), t.getPeriodSeconds(), t.getWindow());
    }

    @Bean
    @ConditionalOnMissingBean
    public TotpSecretGenerator mfaTotpSecretGenerator(MfaProperties props) {
        MfaProperties.Totp t = props.getTotp();
        return new TotpSecretGenerator(t.getSecretLengthBytes(), t.getAlgorithm(), t.getDigits(), t.getPeriodSeconds());
    }

    // ===================== 등록 저장소(enrollment store): memory | jdbc =====================

    @Bean
    @ConditionalOnMissingBean(MfaEnrollmentStore.class)
    @ConditionalOnProperty(
            prefix = "framework.mfa.enrollment.store",
            name = "type",
            havingValue = "memory",
            matchIfMissing = true)
    public MfaEnrollmentStore inMemoryMfaEnrollmentStore() {
        return new InMemoryMfaEnrollmentStore();
    }

    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnMissingBean(MfaEnrollmentStore.class)
    @ConditionalOnProperty(prefix = "framework.mfa.enrollment.store", name = "type", havingValue = "jdbc")
    public MfaEnrollmentStore jdbcMfaEnrollmentStore(JdbcTemplate jdbcTemplate) {
        return new JdbcMfaEnrollmentStore(jdbcTemplate);
    }

    // ===================== 챌린지 저장소(challenge store): memory | redis =====================

    @Bean
    @ConditionalOnMissingBean(MfaChallengeStore.class)
    @ConditionalOnProperty(
            prefix = "framework.mfa.challenge.store",
            name = "type",
            havingValue = "memory",
            matchIfMissing = true)
    public MfaChallengeStore inMemoryMfaChallengeStore() {
        return new InMemoryMfaChallengeStore();
    }

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(MfaChallengeStore.class)
    @ConditionalOnProperty(prefix = "framework.mfa.challenge.store", name = "type", havingValue = "redis")
    public MfaChallengeStore redisMfaChallengeStore(StringRedisTemplate redisTemplate, MfaProperties props) {
        return new RedisMfaChallengeStore(
                redisTemplate, props.getChallenge().getStore().getKeyPrefix());
    }

    // ===================== 서비스 / 게이트 / 컨트롤러 =====================

    @Bean
    @ConditionalOnMissingBean
    public MfaService mfaService(
            MfaProperties props,
            MfaEnrollmentStore enrollmentStore,
            MfaChallengeStore challengeStore,
            Totp totp,
            TotpSecretGenerator totpSecretGenerator,
            ObjectProvider<OtpSender> otpSender,
            ApplicationEventPublisher eventPublisher) {
        return new MfaService(
                props,
                enrollmentStore,
                challengeStore,
                totp,
                totpSecretGenerator,
                otpSender.getIfAvailable(),
                eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(MfaGate.class)
    public MfaGate frameworkMfaGate(MfaService mfaService, MfaProperties props) {
        return new DefaultMfaGate(mfaService, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public MfaEnrollmentController frameworkMfaEnrollmentController(
            MfaService mfaService, CurrentUserProvider currentUserProvider) {
        return new MfaEnrollmentController(mfaService, currentUserProvider);
    }

    @Bean
    @ConditionalOnBean(LoginService.class)
    @ConditionalOnMissingBean
    public MfaVerificationController frameworkMfaVerificationController(
            MfaService mfaService, LoginService loginService, LoginAttemptProperties loginAttemptProperties) {
        return new MfaVerificationController(mfaService, loginService, loginAttemptProperties);
    }

    /**
     * WebAuthn 2차 인증 지원(선택). framework-webauthn 이 활성이면 등록되는 {@link WebAuthnRelyingPartyOperations}
     * 빈을 재사용해 {@link MfaWebAuthnSupport} 를 구성한다. spring-security-webauthn 가 런타임에 없으면(=대부분의
     * 앱) 이 중첩 설정 자체가 로드되지 않으므로, 최상위 {@code @Bean} 파라미터로 두었을 때의 클래스 로딩 문제를
     * 회피한다(클래스레벨 {@code @ConditionalOnClass} 격리 — compileOnly→런타임 부재 안전, PITFALLS §4).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebAuthnRelyingPartyOperations.class)
    static class WebAuthnMfaConfiguration {

        @Bean
        @ConditionalOnBean(WebAuthnRelyingPartyOperations.class)
        @ConditionalOnMissingBean
        public MfaWebAuthnSupport mfaWebAuthnSupport(WebAuthnRelyingPartyOperations rpOperations) {
            return new MfaWebAuthnSupport(rpOperations);
        }

        @Bean
        @ConditionalOnBean(MfaWebAuthnSupport.class)
        @ConditionalOnMissingBean
        public MfaWebAuthnService mfaWebAuthnService(
                MfaProperties props,
                MfaEnrollmentStore enrollmentStore,
                MfaChallengeStore challengeStore,
                MfaWebAuthnSupport support,
                ApplicationEventPublisher eventPublisher) {
            return new MfaWebAuthnService(props, enrollmentStore, challengeStore, support, eventPublisher);
        }

        @Bean
        @ConditionalOnBean({MfaWebAuthnService.class, LoginService.class})
        @ConditionalOnMissingBean
        public MfaWebAuthnController frameworkMfaWebAuthnController(
                MfaWebAuthnService webAuthnService,
                CurrentUserProvider currentUserProvider,
                LoginService loginService,
                LoginAttemptProperties loginAttemptProperties) {
            return new MfaWebAuthnController(
                    webAuthnService, currentUserProvider, loginService, loginAttemptProperties);
        }
    }
}
