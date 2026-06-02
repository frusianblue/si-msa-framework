package com.company.framework.mfa.core;

import com.company.framework.mfa.config.MfaProperties;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.MfaGate;
import com.company.framework.security.auth.MfaTicket;
import java.util.List;

/**
 * {@link MfaGate} 기본 구현. framework-security 의 로그인 흐름과 framework-mfa 의 {@link MfaService} 를 잇는다.
 *
 * <ul>
 *   <li>{@link #isRequired(AuthenticatedUser)} — 정책이 OFF 가 아니고, 사용자에게 <b>확정된</b> MFA 등록이 하나라도 있으면 true.
 *       (정책 ENROLLED: 등록한 사용자만 2차 인증. 미등록 사용자는 단일단계 로그인 유지 → 점진적 도입 가능.)
 *   <li>{@link #issueChallenge(AuthenticatedUser, String)} — 대기 인증 상태를 보관하고 티켓/사용 가능 방식을 반환.
 *       OTP 자동 발송은 {@link MfaService#createChallenge} 내부에서 처리.
 * </ul>
 */
public class DefaultMfaGate implements MfaGate {

    private final MfaService mfaService;
    private final MfaProperties props;

    public DefaultMfaGate(MfaService mfaService, MfaProperties props) {
        this.mfaService = mfaService;
        this.props = props;
    }

    @Override
    public boolean isRequired(AuthenticatedUser user) {
        if (props.getPolicy() == MfaProperties.Policy.OFF) {
            return false;
        }
        return !mfaService.confirmedMethods(user.userId()).isEmpty();
    }

    @Override
    public MfaTicket issueChallenge(AuthenticatedUser user, String clientIp) {
        List<String> methods = mfaService.confirmedMethods(user.userId());
        String ticket = mfaService.createChallenge(user.userId(), user.roles(), methods);
        return new MfaTicket(ticket, methods, props.getChallenge().getTtl().toSeconds());
    }
}
