package com.company.framework.mfa.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.mfa.core.MfaService;
import com.company.framework.mfa.store.InMemoryMfaChallengeStore;
import com.company.framework.mfa.store.InMemoryMfaEnrollmentStore;
import com.company.framework.mfa.store.MfaChallengeStore;
import com.company.framework.mfa.store.MfaEnrollmentStore;
import com.company.framework.mfa.totp.Totp;
import com.company.framework.mfa.totp.TotpSecretGenerator;
import com.company.framework.mybatis.support.CurrentUserProvider;
import com.company.framework.security.auth.MfaGate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * MFA 오토컨피그 로딩/토글 검증.
 *
 * <ul>
 *   <li>{@code framework.mfa.enabled=true} → 핵심 빈(Totp/SecretGenerator/Service/Gate)과 기본 저장소(in-memory)가 등록.
 *   <li>기본(미설정/false) → 모듈은 의존성에 있어도 빈을 만들지 않음(완전 하위호환).
 * </ul>
 *
 * <p>{@code frameworkMfaEnrollmentController} 는 {@link CurrentUserProvider} 를 요구하므로 스텁 빈을 제공한다.
 * 검증 컨트롤러는 {@code @ConditionalOnBean(LoginService)} 라 LoginService 없는 이 컨텍스트에선 생성되지 않는다.
 */
class MfaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MfaAutoConfiguration.class))
            .withBean(CurrentUserProvider.class, () -> new CurrentUserProvider() {
                @Override
                public Optional<String> getCurrentUser() {
                    return Optional.of("tester");
                }
            });

    @Test
    @DisplayName("enabled=true → 핵심 빈 + 기본 in-memory 저장소 등록")
    void registersBeansWhenEnabled() {
        runner.withPropertyValues("framework.mfa.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(Totp.class);
            assertThat(context).hasSingleBean(TotpSecretGenerator.class);
            assertThat(context).hasSingleBean(MfaService.class);
            assertThat(context).hasSingleBean(MfaGate.class);
            assertThat(context).hasSingleBean(MfaEnrollmentStore.class);
            assertThat(context).hasSingleBean(MfaChallengeStore.class);
            // 저장소 타입 미지정 → matchIfMissing=memory 분기로 in-memory 구현이 선택돼야 한다.
            assertThat(context.getBean(MfaEnrollmentStore.class)).isInstanceOf(InMemoryMfaEnrollmentStore.class);
            assertThat(context.getBean(MfaChallengeStore.class)).isInstanceOf(InMemoryMfaChallengeStore.class);
        });
    }

    @Test
    @DisplayName("기본(비활성) → 어떤 MFA 빈도 만들지 않음")
    void backsOffWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MfaService.class);
            assertThat(context).doesNotHaveBean(Totp.class);
            assertThat(context).doesNotHaveBean(MfaGate.class);
        });
    }
}
