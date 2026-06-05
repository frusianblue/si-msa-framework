package com.company.framework.webauthn.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

/**
 * WebAuthn 오토컨피그 로딩/토글 검증(비-web ApplicationContextRunner).
 *
 * <ul>
 *   <li>{@code framework.webauthn.enabled=true} → RP 연산 + 기본 in-memory 저장소(사용자/자격증명)가 등록.
 *   <li>기본(미설정/false) → 모듈이 의존성에 있어도 빈을 만들지 않음(무상태 주류 무영향, 완전 하위호환).
 *   <li>{@code store.type=jdbc} + JdbcTemplate 존재 → JDBC 저장소 구현이 선택.
 * </ul>
 *
 * <p>전용 SecurityFilterChain·토큰 교환 컨트롤러는 {@code @ConditionalOnWebApplication(SERVLET)} 의 중첩 설정에 있어,
 * 비-web 러너인 본 테스트에서는 생성되지 않는다(HttpSecurity/UserDetailsService 불요). ceremony 실서명/MockMvc 스모크는
 * 받는 쪽 web 컨텍스트에서 검증한다.
 */
class WebAuthnAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(WebAuthnAutoConfiguration.class));

    @Test
    @DisplayName("enabled=true → RP 연산 + 기본 in-memory 저장소 등록")
    void registersBeansWhenEnabled() {
        runner.withPropertyValues("framework.webauthn.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(WebAuthnRelyingPartyOperations.class);
            assertThat(context).hasSingleBean(PublicKeyCredentialUserEntityRepository.class);
            assertThat(context).hasSingleBean(UserCredentialRepository.class);
            // 저장소 타입 미지정 → matchIfMissing=memory 분기로 in-memory 구현이 선택돼야 한다.
            assertThat(context.getBean(PublicKeyCredentialUserEntityRepository.class))
                    .isInstanceOf(MapPublicKeyCredentialUserEntityRepository.class);
            assertThat(context.getBean(UserCredentialRepository.class)).isInstanceOf(MapUserCredentialRepository.class);
        });
    }

    @Test
    @DisplayName("기본(비활성) → 어떤 WebAuthn 빈도 만들지 않음")
    void backsOffWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(WebAuthnRelyingPartyOperations.class);
            assertThat(context).doesNotHaveBean(PublicKeyCredentialUserEntityRepository.class);
            assertThat(context).doesNotHaveBean(UserCredentialRepository.class);
        });
    }

    @Test
    @DisplayName("store.type=jdbc + JdbcTemplate → JDBC 저장소 선택")
    void selectsJdbcStoreWhenConfigured() {
        // JDBC 리포지토리 생성자는 JdbcOperations 만 보관(생성 시 DB 미접근)하므로, 실제 DB 엔진 없이
        // mock DataSource 기반 JdbcTemplate 으로 토글 라우팅(store.type=jdbc → Jdbc* 구현 선택)만 검증한다.
        runner.withBean(JdbcTemplate.class, () -> new JdbcTemplate(mock(DataSource.class)))
                .withPropertyValues("framework.webauthn.enabled=true", "framework.webauthn.store.type=jdbc")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PublicKeyCredentialUserEntityRepository.class))
                            .isInstanceOf(JdbcPublicKeyCredentialUserEntityRepository.class);
                    assertThat(context.getBean(UserCredentialRepository.class))
                            .isInstanceOf(JdbcUserCredentialRepository.class);
                });
    }
}
