package com.company.framework.mfa.core;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.mfa.config.MfaProperties;
import com.company.framework.mfa.store.MfaChallengeStore;
import com.company.framework.mfa.store.MfaEnrollment;
import com.company.framework.mfa.store.MfaEnrollmentStore;
import com.company.framework.mfa.store.PendingAuth;
import com.company.framework.security.auth.LoginAuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;

/**
 * WebAuthn 2차 인증 ceremony 오케스트레이션: 등록(enroll/confirm)·검증(options/verify)을 티켓 기반으로 운영한다.
 *
 * <p><b>분리 이유(클래스 로딩 안전).</b> 이 빈은 SS7 webauthn 타입을 캡슐화한 {@link MfaWebAuthnSupport} 를 필드로
 * 들어 spring-security-webauthn 에 (간접) 결합한다. 그래서 항상 로드되는 핵심 빈 {@link MfaService} 에 합치지 않고
 * 분리해, MfaAutoConfiguration 의 중첩 {@code @ConditionalOnClass(WebAuthnRelyingPartyOperations)} 설정에서만
 * 인스턴스화되게 한다 — webauthn 모듈을 안 쓰는 앱에서 이 클래스가 로드/검증되어 {@code NoClassDefFoundError} 가
 * 나는 것을 원천 차단한다(PITFALLS §4: SS 캡슐화 클래스를 핵심 빈 필드로 들지 말 것).
 *
 * <p>설계는 "독립 등록형": WebAuthn 을 TOTP 처럼 1급 방식으로 별도 enroll/confirm 관리하고({@link MfaEnrollment}
 * 에 {@link MfaMethod#WEBAUTHN} 확정 기록), 자격증명 본체와 RP 연산은 framework-webauthn 의 것을 재사용한다.
 * challenge(옵션)는 세션 대신 {@link MfaChallengeStore} 의 티켓에 바인딩해 무상태 일관성을 유지한다.
 */
public class MfaWebAuthnService {

    /** WebAuthn 등록 ceremony 시작 결과: 단기 등록 티켓 + creation 옵션 JSON(브라우저 navigator.credentials.create 입력). */
    public record WebAuthnRegistrationChallenge(String ticket, String optionsJson) {}

    private final MfaProperties props;
    private final MfaEnrollmentStore enrollmentStore;
    private final MfaChallengeStore challengeStore;
    private final MfaWebAuthnSupport support;
    private final ApplicationEventPublisher events; // nullable

    public MfaWebAuthnService(
            MfaProperties props,
            MfaEnrollmentStore enrollmentStore,
            MfaChallengeStore challengeStore,
            MfaWebAuthnSupport support,
            ApplicationEventPublisher events) {
        this.props = props;
        this.enrollmentStore = enrollmentStore;
        this.challengeStore = challengeStore;
        this.support = support;
        this.events = events;
    }

    // ===================== 등록(enrollment) =====================

    /**
     * WebAuthn 등록 ceremony 시작(인증된 본인). creation 옵션을 발급하고, 그 옵션(challenge)을 단기 등록 티켓에
     * 바인딩 보관한 뒤 옵션 JSON 과 티켓을 반환한다. 클라이언트는 {@code navigator.credentials.create} 결과를
     * {@link #confirmEnrollment} 로 티켓과 함께 제출한다(세션 대신 티켓 보관 → 무상태 일관).
     */
    public WebAuthnRegistrationChallenge beginEnrollment(String userId) {
        requireEnabled();
        String optionsJson = support.createRegistrationOptionsJson(userId);
        String ticket = UUID.randomUUID().toString().replace("-", "");
        PendingAuth pending =
                new PendingAuth(userId, List.of(), List.of(MfaMethod.WEBAUTHN.code()), null, 0, optionsJson);
        challengeStore.save(ticket, pending, props.getChallenge().getTtl());
        return new WebAuthnRegistrationChallenge(ticket, optionsJson);
    }

    /**
     * WebAuthn 등록 확정: 보관한 creation 옵션 + 클라이언트 attestation 을 RP 로 검증/저장하고, MFA 등록 메타를
     * 확정 기록한다(자격증명 본체는 RP 가 {@code UserCredentialRepository} 에 저장). 등록 티켓은 1회용으로 폐기한다.
     */
    public void confirmEnrollment(String userId, String ticket, String credentialJson, String label) {
        requireEnabled();
        PendingAuth pending = loadPending(ticket);
        if (!pending.userId().equals(userId) || pending.webauthnOptionsJson() == null) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "유효하지 않은 등록 요청입니다.");
        }
        support.registerCredential(pending.webauthnOptionsJson(), credentialJson, label);
        enrollmentStore.save(new MfaEnrollment(userId, MfaMethod.WEBAUTHN, null, List.of(), true, Instant.now()));
        challengeStore.remove(ticket);
        publish(LoginAuditEvent.Type.MFA_ENROLLED, userId, "webauthn");
    }

    // ===================== 로그인 2단계(assertion) =====================

    /**
     * 로그인 2단계 WebAuthn assertion 옵션 발급. 기존 로그인 티켓에 request 옵션(challenge)을 바인딩 보관하고 옵션
     * JSON 을 반환한다(클라이언트 {@code navigator.credentials.get} 입력). 옵션은 {@link #verify} 에서 복원된다.
     */
    public String beginAssertion(String ticket) {
        requireEnabled();
        PendingAuth pending = loadPending(ticket);
        if (!pending.methods().contains(MfaMethod.WEBAUTHN.code())) {
            throw new BusinessException(ErrorCode.Common.FORBIDDEN, "이 챌린지에서는 WebAuthn 을 사용할 수 없습니다.");
        }
        String optionsJson = support.createAssertionOptionsJson(pending.userId());
        challengeStore.save(
                ticket,
                pending.withWebauthnOptionsJson(optionsJson),
                props.getChallenge().getTtl());
        return optionsJson;
    }

    /**
     * 로그인 2단계 WebAuthn assertion 검증. 보관한 request 옵션 + 클라이언트 assertion 을 RP 로 검증하고, 서명한
     * 자격증명 소유자가 티켓의 userId 와 일치하면 챌린지를 폐기하고 (userId, roles) 를 반환한다. 실패 시 시도횟수를
     * 누적하고 임계치 초과 시 챌린지를 폐기한다.
     */
    public MfaService.MfaVerification verify(String ticket, String credentialJson, String clientIp) {
        requireEnabled();
        PendingAuth pending = loadPending(ticket);
        if (pending.attempts() >= props.getChallenge().getMaxAttempts()) {
            challengeStore.remove(ticket);
            throw new BusinessException(ErrorCode.Common.LOGIN_LOCKED, "인증 시도가 많습니다. 처음부터 다시 로그인하세요.");
        }
        if (pending.webauthnOptionsJson() == null) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "WebAuthn 옵션이 발급되지 않았습니다.");
        }
        boolean ok;
        try {
            String authenticatedUser = support.authenticate(pending.webauthnOptionsJson(), credentialJson);
            ok = pending.userId().equals(authenticatedUser);
        } catch (BusinessException ex) {
            ok = false;
        }
        if (ok) {
            challengeStore.remove(ticket);
            return new MfaService.MfaVerification(pending.userId(), pending.roles());
        }
        challengeStore.save(
                ticket,
                pending.withAttempts(pending.attempts() + 1),
                props.getChallenge().getTtl());
        publish(LoginAuditEvent.Type.MFA_FAILURE, pending.userId(), clientIp);
        throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "WebAuthn 인증에 실패했습니다.");
    }

    // ===================== 내부 =====================

    private void requireEnabled() {
        if (!props.getWebauthn().isEnabled()) {
            throw new BusinessException(ErrorCode.Common.FORBIDDEN, "WebAuthn 2차 인증이 비활성화되어 있습니다.");
        }
    }

    private PendingAuth loadPending(String ticket) {
        return challengeStore
                .find(ticket)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED, "만료되었거나 유효하지 않은 인증 요청입니다."));
    }

    private void publish(LoginAuditEvent.Type type, String userId, String detail) {
        if (events != null) {
            events.publishEvent(LoginAuditEvent.of(type, userId, null, detail));
        }
    }
}
