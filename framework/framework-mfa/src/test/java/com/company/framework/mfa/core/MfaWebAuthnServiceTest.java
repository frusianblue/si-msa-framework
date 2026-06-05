package com.company.framework.mfa.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.mfa.config.MfaProperties;
import com.company.framework.mfa.store.InMemoryMfaChallengeStore;
import com.company.framework.mfa.store.InMemoryMfaEnrollmentStore;
import com.company.framework.mfa.store.MfaEnrollment;
import com.company.framework.mfa.store.PendingAuth;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MfaWebAuthnService} 오케스트레이션 단위 테스트. 실제 WebAuthn 크립토(attestation/assertion 서명)는 인증기
 * 없이는 생성할 수 없으므로 {@link MfaWebAuthnSupport}(RP+직렬화)를 mock 하고, 티켓 바인딩·등록 메타 기록·소유 검증·
 * 시도 누적·토글만 검증한다. 직렬화/RP 자체의 정합은 SS7 소스 대조로 보증한다.
 */
class MfaWebAuthnServiceTest {

    private MfaProperties props;
    private InMemoryMfaEnrollmentStore enrollmentStore;
    private InMemoryMfaChallengeStore challengeStore;
    private MfaWebAuthnSupport support;
    private MfaWebAuthnService service;

    private static final Duration TTL = Duration.ofMinutes(5);

    @BeforeEach
    void setUp() {
        props = new MfaProperties();
        enrollmentStore = new InMemoryMfaEnrollmentStore();
        challengeStore = new InMemoryMfaChallengeStore();
        support = mock(MfaWebAuthnSupport.class);
        service = new MfaWebAuthnService(props, enrollmentStore, challengeStore, support, null);
    }

    @Test
    void 등록_시작은_옵션을_티켓에_바인딩한다() {
        when(support.createRegistrationOptionsJson("alice")).thenReturn("{creation-options}");

        MfaWebAuthnService.WebAuthnRegistrationChallenge challenge = service.beginEnrollment("alice");

        assertThat(challenge.optionsJson()).isEqualTo("{creation-options}");
        PendingAuth pending = challengeStore.find(challenge.ticket()).orElseThrow();
        assertThat(pending.userId()).isEqualTo("alice");
        assertThat(pending.methods()).containsExactly("webauthn");
        assertThat(pending.webauthnOptionsJson()).isEqualTo("{creation-options}");
    }

    @Test
    void 등록_확정은_자격증명을_등록하고_WEBAUTHN_확정_메타를_기록한다() {
        challengeStore.save(
                "t1", new PendingAuth("alice", List.of(), List.of("webauthn"), null, 0, "{creation-options}"), TTL);

        service.confirmEnrollment("alice", "t1", "{attestation}", "내 노트북");

        verify(support).registerCredential("{creation-options}", "{attestation}", "내 노트북");
        Optional<MfaEnrollment> e = enrollmentStore.find("alice", MfaMethod.WEBAUTHN);
        assertThat(e).isPresent();
        assertThat(e.get().confirmed()).isTrue();
        assertThat(challengeStore.find("t1")).isEmpty(); // 1회용 폐기
    }

    @Test
    void 등록_확정_userId_불일치는_거부하고_등록하지_않는다() {
        challengeStore.save(
                "t1", new PendingAuth("alice", List.of(), List.of("webauthn"), null, 0, "{creation-options}"), TTL);

        assertThatThrownBy(() -> service.confirmEnrollment("mallory", "t1", "{attestation}", "x"))
                .isInstanceOf(BusinessException.class);
        verify(support, never()).registerCredential(anyString(), anyString(), anyString());
        assertThat(enrollmentStore.find("alice", MfaMethod.WEBAUTHN)).isEmpty();
    }

    @Test
    void assertion_시작은_request_옵션을_로그인_티켓에_바인딩한다() {
        challengeStore.save("login1", new PendingAuth("bob", List.of("USER"), List.of("webauthn"), null, 0, null), TTL);
        when(support.createAssertionOptionsJson("bob")).thenReturn("{request-options}");

        String optionsJson = service.beginAssertion("login1");

        assertThat(optionsJson).isEqualTo("{request-options}");
        assertThat(challengeStore.find("login1").orElseThrow().webauthnOptionsJson())
                .isEqualTo("{request-options}");
    }

    @Test
    void assertion_시작은_webauthn_미가용_챌린지를_거부한다() {
        challengeStore.save("login1", new PendingAuth("bob", List.of("USER"), List.of("totp"), null, 0, null), TTL);

        assertThatThrownBy(() -> service.beginAssertion("login1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void 검증_성공은_챌린지를_폐기하고_userId_roles를_반환한다() {
        challengeStore.save(
                "login1",
                new PendingAuth("bob", List.of("USER"), List.of("webauthn"), null, 0, "{request-options}"),
                TTL);
        when(support.authenticate("{request-options}", "{assertion}")).thenReturn("bob");

        MfaService.MfaVerification verified = service.verify("login1", "{assertion}", "1.2.3.4");

        assertThat(verified.userId()).isEqualTo("bob");
        assertThat(verified.roles()).containsExactly("USER");
        assertThat(challengeStore.find("login1")).isEmpty();
    }

    @Test
    void 검증_username_불일치는_시도횟수를_누적하고_실패시킨다() {
        challengeStore.save(
                "login1",
                new PendingAuth("bob", List.of("USER"), List.of("webauthn"), null, 0, "{request-options}"),
                TTL);
        when(support.authenticate(eq("{request-options}"), anyString())).thenReturn("someone-else");

        assertThatThrownBy(() -> service.verify("login1", "{assertion}", "1.2.3.4"))
                .isInstanceOf(BusinessException.class);
        assertThat(challengeStore.find("login1").orElseThrow().attempts()).isEqualTo(1); // 누적, 폐기 아님
    }

    @Test
    void 검증_시도횟수_초과는_챌린지를_폐기하고_LOGIN_LOCKED() {
        int max = props.getChallenge().getMaxAttempts();
        challengeStore.save(
                "login1",
                new PendingAuth("bob", List.of("USER"), List.of("webauthn"), null, max, "{request-options}"),
                TTL);

        assertThatThrownBy(() -> service.verify("login1", "{assertion}", "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex ->
                        assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.Common.LOGIN_LOCKED));
        assertThat(challengeStore.find("login1")).isEmpty();
    }

    @Test
    void webauthn_비활성화면_FORBIDDEN() {
        props.getWebauthn().setEnabled(false);

        assertThatThrownBy(() -> service.beginEnrollment("alice"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex ->
                        assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.Common.FORBIDDEN));
    }
}
