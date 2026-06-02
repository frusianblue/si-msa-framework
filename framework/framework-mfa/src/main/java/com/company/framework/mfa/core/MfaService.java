package com.company.framework.mfa.core;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.mfa.config.MfaProperties;
import com.company.framework.mfa.otp.OtpSender;
import com.company.framework.mfa.store.MfaChallengeStore;
import com.company.framework.mfa.store.MfaEnrollment;
import com.company.framework.mfa.store.MfaEnrollmentStore;
import com.company.framework.mfa.store.PendingAuth;
import com.company.framework.mfa.totp.Totp;
import com.company.framework.mfa.totp.TotpSecretGenerator;
import com.company.framework.security.auth.LoginAuditEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;

/**
 * MFA 도메인 오케스트레이션: 등록(TOTP/OTP)·복구코드·로그인 2단계 챌린지 발급/검증.
 *
 * <p>토큰 발급은 framework-security 의 LoginService 가 담당하므로 여기서는 다루지 않는다(검증 성공 시
 * userId/roles 만 돌려준다). 감사 이벤트는 framework-security 의 {@link LoginAuditEvent} 를 재사용한다
 * (발행 publisher 가 없으면 무해히 생략).
 */
public class MfaService {

    /** TOTP 등록 시작 결과. 시크릿(수동 입력용)과 otpauth URI(QR 용)를 함께 반환. */
    public record EnrollmentStart(String method, String secret, String otpauthUri) {}

    /** 검증 성공 시 토큰 발급에 넘길 최소 정보. */
    public record MfaVerification(String userId, List<String> roles) {}

    /** 사용자 MFA 등록 현황. */
    public record MfaStatus(List<String> confirmed, List<String> pending) {}

    private final MfaProperties props;
    private final MfaEnrollmentStore enrollmentStore;
    private final MfaChallengeStore challengeStore;
    private final Totp totp;
    private final TotpSecretGenerator totpSecretGenerator;
    private final OtpSender otpSender; // nullable
    private final ApplicationEventPublisher events; // nullable

    public MfaService(
            MfaProperties props,
            MfaEnrollmentStore enrollmentStore,
            MfaChallengeStore challengeStore,
            Totp totp,
            TotpSecretGenerator totpSecretGenerator,
            OtpSender otpSender,
            ApplicationEventPublisher events) {
        this.props = props;
        this.enrollmentStore = enrollmentStore;
        this.challengeStore = challengeStore;
        this.totp = totp;
        this.totpSecretGenerator = totpSecretGenerator;
        this.otpSender = otpSender;
        this.events = events;
    }

    // ===================== 등록(enrollment) =====================

    /** TOTP 등록 시작: 미확정 시크릿을 발급/저장하고 등록용 URI 를 반환. 확정 전엔 로그인에 사용되지 않는다. */
    public EnrollmentStart beginTotpEnrollment(String userId, String account) {
        if (!props.getTotp().isEnabled()) {
            throw new BusinessException(ErrorCode.Common.FORBIDDEN, "TOTP 방식이 비활성화되어 있습니다.");
        }
        String secret = totpSecretGenerator.newSecret();
        enrollmentStore.save(new MfaEnrollment(userId, MfaMethod.TOTP, secret, List.of(), false, Instant.now()));
        String label = (account == null || account.isBlank()) ? userId : account;
        return new EnrollmentStart(
                MfaMethod.TOTP.code(), secret, totpSecretGenerator.provisioningUri(props.getIssuer(), label, secret));
    }

    /**
     * TOTP 등록 확정: 인증기 앱에서 생성한 코드로 시크릿 보유를 증명하면 활성화하고 복구코드를 발급한다.
     * 복구코드 평문은 이 응답에서만 노출되고 서버는 해시만 보관한다.
     */
    public List<String> confirmTotp(String userId, String code) {
        MfaEnrollment enrollment = enrollmentStore
                .find(userId, MfaMethod.TOTP)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.NOT_FOUND, "진행 중인 TOTP 등록이 없습니다."));
        if (!totp.verify(enrollment.secret(), code)) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "인증 코드가 올바르지 않습니다.");
        }
        List<String> plain = MfaCrypto.recoveryCodes(props.getTotp().getRecoveryCodes());
        List<String> hashes = new ArrayList<>(plain.size());
        for (String c : plain) {
            hashes.add(MfaCrypto.sha256Hex(c));
        }
        enrollmentStore.save(enrollment.withRecoveryCodeHashes(hashes).withConfirmed(true));
        publish(LoginAuditEvent.Type.MFA_ENROLLED, userId, "totp");
        return plain;
    }

    /** OTP(발송형) 등록: 발송 채널(OtpSender)이 있어야 가능. 목적지는 발송 시 sender 가 해석한다. */
    public void enrollOtp(String userId) {
        if (!props.getOtp().isEnabled()) {
            throw new BusinessException(ErrorCode.Common.FORBIDDEN, "OTP 방식이 비활성화되어 있습니다.");
        }
        if (otpSender == null) {
            throw new BusinessException(ErrorCode.Common.FORBIDDEN, "OTP 발송 채널(OtpSender)이 등록되어 있지 않습니다.");
        }
        enrollmentStore.save(new MfaEnrollment(userId, MfaMethod.OTP, null, List.of(), true, Instant.now()));
        publish(LoginAuditEvent.Type.MFA_ENROLLED, userId, "otp");
    }

    /** 등록 해제. */
    public void disable(String userId, MfaMethod method) {
        enrollmentStore.delete(userId, method);
        publish(LoginAuditEvent.Type.MFA_DISABLED, userId, method.code());
    }

    /** 사용자 등록 현황(확정/미확정 방식 목록). */
    public MfaStatus status(String userId) {
        List<String> confirmed = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (MfaEnrollment e : enrollmentStore.findByUser(userId)) {
            (e.confirmed() ? confirmed : pending).add(e.method().code());
        }
        return new MfaStatus(confirmed, pending);
    }

    // ===================== 로그인 2단계 =====================

    /** 확정된 방식 목록. (로그인 게이트가 필요여부 판정에 사용) */
    public List<String> confirmedMethods(String userId) {
        List<String> methods = new ArrayList<>();
        for (MfaEnrollment e : enrollmentStore.findByUser(userId)) {
            if (e.confirmed()) {
                methods.add(e.method().code());
            }
        }
        return methods;
    }

    /** 챌린지 생성(+OTP 자동발송). 티켓·가용 방식을 PendingAuth 로 저장하고 식별자를 반환. */
    public String createChallenge(String userId, List<String> roles, List<String> methods) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        PendingAuth pending = new PendingAuth(userId, roles, methods, null, 0);
        if (methods.contains(MfaMethod.OTP.code()) && props.getOtp().isAutoSend() && otpSender != null) {
            String code = MfaCrypto.numericOtp(props.getOtp().getLength());
            pending = pending.withOtpCodeHash(MfaCrypto.sha256Hex(code));
            otpSender.send(userId, code);
        }
        challengeStore.save(ticket, pending, props.getChallenge().getTtl());
        return ticket;
    }

    /** OTP 재발송(티켓 기준). */
    public void resendOtp(String ticket) {
        PendingAuth pending = loadPending(ticket);
        if (!pending.methods().contains(MfaMethod.OTP.code()) || otpSender == null) {
            throw new BusinessException(ErrorCode.Common.FORBIDDEN, "이 챌린지에서는 OTP 발송을 사용할 수 없습니다.");
        }
        String code = MfaCrypto.numericOtp(props.getOtp().getLength());
        challengeStore.save(
                ticket,
                pending.withOtpCodeHash(MfaCrypto.sha256Hex(code)),
                props.getChallenge().getTtl());
        otpSender.send(pending.userId(), code);
    }

    /**
     * 2차 인증 검증. 성공 시 챌린지를 폐기하고 (userId, roles) 를 반환한다. 실패 시 시도횟수를 누적하고 임계치
     * 초과 시 챌린지를 폐기한다. ({@code method} 가 "recovery" 면 복구코드 1회 소모.)
     */
    public MfaVerification verify(String ticket, String method, String code, String clientIp) {
        PendingAuth pending = loadPending(ticket);
        if (pending.attempts() >= props.getChallenge().getMaxAttempts()) {
            challengeStore.remove(ticket);
            throw new BusinessException(ErrorCode.Common.LOGIN_LOCKED, "인증 시도가 많습니다. 처음부터 다시 로그인하세요.");
        }
        boolean ok =
                switch (method == null ? "" : method.trim().toLowerCase()) {
                    case "totp" -> verifyTotp(pending.userId(), code);
                    case "otp" -> pending.otpCodeHash() != null && MfaCrypto.matches(code, pending.otpCodeHash());
                    case "recovery" -> consumeRecovery(pending.userId(), code);
                    default -> false;
                };
        if (ok) {
            challengeStore.remove(ticket);
            return new MfaVerification(pending.userId(), pending.roles());
        }
        challengeStore.save(
                ticket,
                pending.withAttempts(pending.attempts() + 1),
                props.getChallenge().getTtl());
        publish(LoginAuditEvent.Type.MFA_FAILURE, pending.userId(), clientIp);
        throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "인증 코드가 올바르지 않습니다.");
    }

    public boolean isOtpAvailable() {
        return props.getOtp().isEnabled() && otpSender != null;
    }

    // ===================== 내부 =====================

    private PendingAuth loadPending(String ticket) {
        return challengeStore
                .find(ticket)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED, "만료되었거나 유효하지 않은 인증 요청입니다."));
    }

    private boolean verifyTotp(String userId, String code) {
        Optional<MfaEnrollment> e = enrollmentStore.find(userId, MfaMethod.TOTP);
        return e.isPresent() && e.get().confirmed() && totp.verify(e.get().secret(), code);
    }

    private boolean consumeRecovery(String userId, String code) {
        Optional<MfaEnrollment> opt = enrollmentStore.find(userId, MfaMethod.TOTP);
        if (opt.isEmpty() || !opt.get().confirmed()) {
            return false;
        }
        MfaEnrollment e = opt.get();
        String hash = MfaCrypto.sha256Hex(code);
        List<String> remaining = new ArrayList<>(e.recoveryCodeHashes());
        if (!remaining.remove(hash)) {
            return false;
        }
        enrollmentStore.save(e.withRecoveryCodeHashes(remaining)); // 1회용 소모
        return true;
    }

    private void publish(LoginAuditEvent.Type type, String userId, String detail) {
        if (events != null) {
            events.publishEvent(LoginAuditEvent.of(type, userId, null, detail));
        }
    }
}
