package com.company.framework.security.auth;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.loginattempt.LoginAttemptService;
import com.company.framework.security.token.TokenStore;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 인증(프로젝트 Authenticator) → 토큰 발급/회전/폐기(공통).
 * 프로젝트는 Authenticator 만 구현하면 되고 이 흐름은 건드리지 않는다.
 */
public class LoginService {

    private final Authenticator authenticator;
    private final JwtProvider jwtProvider;
    private final TokenStore tokenStore;
    private final LoginAttemptService loginAttempts;

    public LoginService(
            Authenticator authenticator,
            JwtProvider jwtProvider,
            TokenStore tokenStore,
            LoginAttemptService loginAttempts) {
        this.authenticator = authenticator;
        this.jwtProvider = jwtProvider;
        this.tokenStore = tokenStore;
        this.loginAttempts = loginAttempts;
    }

    public TokenResponse login(LoginCommand command) {
        String key = command.loginId();
        loginAttempts.assertNotLocked(key); // 잠겨 있으면 429(LOGIN_LOCKED)
        try {
            AuthenticatedUser user = authenticator.authenticate(command);
            loginAttempts.reset(key); // 성공 → 카운터 초기화
            return issue(user.userId(), user.roles());
        } catch (RuntimeException e) {
            loginAttempts.recordFailure(key); // 실패 → 카운트(임계치 초과 시 잠금)
            throw e;
        }
    }

    public TokenResponse refresh(String refreshToken) {
        TokenStore.RefreshEntry entry = tokenStore
                .findRefresh(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED, "유효하지 않은 refresh token 입니다."));
        tokenStore.removeRefresh(refreshToken); // 회전: 1회용
        return issue(entry.userId(), entry.roles());
    }

    public void logout(String accessToken, String refreshToken) {
        if (refreshToken != null) tokenStore.removeRefresh(refreshToken);
        if (accessToken != null && jwtProvider.validate(accessToken)) {
            Duration remaining = Duration.between(Instant.now(), jwtProvider.getExpiresAt(accessToken));
            if (!remaining.isNegative() && !remaining.isZero()) {
                tokenStore.blacklist(jwtProvider.getJti(accessToken), remaining);
            }
        }
    }

    private TokenResponse issue(String userId, java.util.List<String> roles) {
        String access = jwtProvider.createAccessToken(userId, roles);
        String refresh = UUID.randomUUID().toString().replace("-", "");
        tokenStore.saveRefresh(refresh, new TokenStore.RefreshEntry(userId, roles), jwtProvider.refreshTtl());
        return new TokenResponse(
                access, refresh, "Bearer", jwtProvider.accessTtl().toSeconds(), roles);
    }
}
