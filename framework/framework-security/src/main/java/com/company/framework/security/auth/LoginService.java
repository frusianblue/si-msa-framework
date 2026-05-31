package com.company.framework.security.auth;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.security.concurrent.ConcurrentSessionProperties;
import com.company.framework.security.concurrent.ConcurrentSessionService;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.loginattempt.LoginAttemptProperties;
import com.company.framework.security.loginattempt.LoginAttemptService;
import com.company.framework.security.token.TokenStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 인증(프로젝트 Authenticator) → 토큰 발급/회전/폐기(공통).
 * 프로젝트는 Authenticator 만 구현하면 되고 이 흐름은 건드리지 않는다.
 *
 * 추가 기능(모두 선택적·비파괴):
 *  - 접속 감사 이벤트 발행(LoginAuditEvent): events 가 있으면 성공/실패/로그아웃을 발행. framework-audit 가 적재.
 *  - 동시(중복) 로그인 제어: concurrent-session.enabled=true 면 사용자별 세션 수를 제한(거부 또는 최오래 세션 강제 로그아웃).
 * 두 기능 모두 의존 빈이 없거나 꺼져 있으면 기존 동작과 완전히 동일하다.
 */
public class LoginService {

    private final Authenticator authenticator;
    private final JwtProvider jwtProvider;
    private final TokenStore tokenStore;
    private final LoginAttemptService loginAttempts;
    private final LoginAttemptProperties loginAttemptProperties;

    private final ApplicationEventPublisher events; // nullable
    private final ConcurrentSessionService concurrentSessions; // nullable
    private final ConcurrentSessionProperties concurrentProperties; // nullable

    /** 하위호환 생성자(감사 이벤트/동시로그인 제어 미사용). */
    public LoginService(
            Authenticator authenticator,
            JwtProvider jwtProvider,
            TokenStore tokenStore,
            LoginAttemptService loginAttempts,
            LoginAttemptProperties loginAttemptProperties) {
        this(authenticator, jwtProvider, tokenStore, loginAttempts, loginAttemptProperties, null, null, null);
    }

    public LoginService(
            Authenticator authenticator,
            JwtProvider jwtProvider,
            TokenStore tokenStore,
            LoginAttemptService loginAttempts,
            LoginAttemptProperties loginAttemptProperties,
            ApplicationEventPublisher events,
            ConcurrentSessionService concurrentSessions,
            ConcurrentSessionProperties concurrentProperties) {
        this.authenticator = authenticator;
        this.jwtProvider = jwtProvider;
        this.tokenStore = tokenStore;
        this.loginAttempts = loginAttempts;
        this.loginAttemptProperties = loginAttemptProperties;
        this.events = events;
        this.concurrentSessions = concurrentSessions;
        this.concurrentProperties = concurrentProperties;
    }

    /** 하위호환: IP 미상(또는 IP 비결합 정책)일 때. 키는 loginId 단독. */
    public TokenResponse login(LoginCommand command) {
        return login(command, null);
    }

    public TokenResponse login(LoginCommand command, String clientIp) {
        String key = attemptKey(command.loginId(), clientIp);
        loginAttempts.assertNotLocked(key); // 잠겨 있으면 429(LOGIN_LOCKED)
        try {
            AuthenticatedUser user = authenticator.authenticate(command);
            loginAttempts.reset(key); // 성공 → 카운터 초기화
            TokenResponse response = issue(user.userId(), user.roles());
            publish(LoginAuditEvent.Type.LOGIN_SUCCESS, command.loginId(), clientIp, null);
            return response;
        } catch (RuntimeException e) {
            loginAttempts.recordFailure(key); // 실패 → 카운트(임계치 초과 시 잠금)
            publish(LoginAuditEvent.Type.LOGIN_FAILURE, command.loginId(), clientIp, e.getMessage());
            throw e;
        }
    }

    /** 실패 카운트 키 = 정책(LOGIN_ID | LOGIN_ID_AND_IP)에 따라 결정. IP 미상이면 loginId 로 폴백. */
    private String attemptKey(String loginId, String clientIp) {
        if (loginAttemptProperties.getKeyStrategy() == LoginAttemptProperties.KeyStrategy.LOGIN_ID_AND_IP
                && clientIp != null
                && !clientIp.isBlank()) {
            return loginId + "|" + clientIp;
        }
        return loginId;
    }

    public TokenResponse refresh(String refreshToken) {
        TokenStore.RefreshEntry entry = tokenStore
                .findRefresh(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED, "유효하지 않은 refresh token 입니다."));
        tokenStore.removeRefresh(refreshToken); // 회전: 1회용
        unregisterSession(refreshToken); // 회전 전 기존 세션 해제(아래 issue 가 새 세션 등록)
        return issue(entry.userId(), entry.roles());
    }

    public void logout(String accessToken, String refreshToken) {
        logout(accessToken, refreshToken, null);
    }

    public void logout(String accessToken, String refreshToken, String clientIp) {
        String loginId = null;
        if (refreshToken != null) {
            tokenStore.removeRefresh(refreshToken);
            unregisterSession(refreshToken);
        }
        if (accessToken != null && jwtProvider.validate(accessToken)) {
            loginId = jwtProvider.getSubject(accessToken);
            Duration remaining = Duration.between(Instant.now(), jwtProvider.getExpiresAt(accessToken));
            if (!remaining.isNegative() && !remaining.isZero()) {
                tokenStore.blacklist(jwtProvider.getJti(accessToken), remaining);
            }
        }
        publish(LoginAuditEvent.Type.LOGOUT, loginId, clientIp, null);
    }

    private TokenResponse issue(String userId, List<String> roles) {
        String access = jwtProvider.createAccessToken(userId, roles);
        String refresh = UUID.randomUUID().toString().replace("-", "");
        applyConcurrentSessionLimit(userId, refresh, jwtProvider.getJti(access)); // 한도 적용(거부 시 예외, 그 전엔 미저장)
        tokenStore.saveRefresh(refresh, new TokenStore.RefreshEntry(userId, roles), jwtProvider.refreshTtl());
        return new TokenResponse(access, refresh, "Bearer", jwtProvider.accessTtl().toSeconds(), roles);
    }

    /** 동시 로그인 제어. 등록(필요 시 한도 적용) 후, 강제 로그아웃 대상 토큰을 무효화한다. */
    private void applyConcurrentSessionLimit(String userId, String sessionId, String accessJti) {
        if (concurrentSessions == null || concurrentProperties == null || !concurrentProperties.isEnabled()) return;
        List<ConcurrentSessionService.ActiveSession> evicted = concurrentSessions.register(
                new ConcurrentSessionService.ActiveSession(
                        userId, sessionId, accessJti, sessionId, System.currentTimeMillis()));
        for (ConcurrentSessionService.ActiveSession old : evicted) {
            tokenStore.removeRefresh(old.refreshToken());
            if (old.accessJti() != null) {
                tokenStore.blacklist(old.accessJti(), jwtProvider.accessTtl()); // 만료시각 미상 → access TTL 상한으로 무효화
            }
        }
    }

    private void unregisterSession(String sessionId) {
        if (concurrentSessions != null) concurrentSessions.unregister(sessionId);
    }

    private void publish(LoginAuditEvent.Type type, String loginId, String clientIp, String detail) {
        if (events != null) {
            events.publishEvent(LoginAuditEvent.of(type, loginId, clientIp, detail));
        }
    }
}
