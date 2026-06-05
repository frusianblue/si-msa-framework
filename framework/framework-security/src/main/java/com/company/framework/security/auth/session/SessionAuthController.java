package com.company.framework.security.auth.session;

import com.company.framework.core.response.ApiResponse;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.LoginCommand;
import com.company.framework.security.loginattempt.LoginAttemptProperties;
import com.company.framework.security.support.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 세션 기반 인증의 표준 엔드포인트({@code framework.security.session.mode=session} 일 때만 등록).
 *
 * <p>JWT 경로의 {@code AuthController}(/api/v1/auth/login)와 짝이 되는 세션 버전이다. 토큰을 내려주지 않고
 * {@code Set-Cookie: SESSION=...} 으로 서버 세션을 수립한다. CSRF 보호가 켜져 있으면(기본) 이후 변경 요청은
 * 시큐리티가 내려준 {@code XSRF-TOKEN} 쿠키 값을 {@code X-XSRF-TOKEN} 헤더로 동봉해야 한다.
 */
@RestController
@RequestMapping("/api/v1/auth/session")
public class SessionAuthController {

    private final SessionAuthService sessionAuthService;
    private final LoginAttemptProperties loginAttemptProperties;

    public SessionAuthController(SessionAuthService sessionAuthService, LoginAttemptProperties loginAttemptProperties) {
        this.sessionAuthService = sessionAuthService;
        this.loginAttemptProperties = loginAttemptProperties;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(
            @Valid @RequestBody LoginCommand command, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ClientIpResolver.resolve(request, loginAttemptProperties.getClientIpHeader());
        String lockKey = attemptKey(command.loginId(), clientIp);
        AuthenticatedUser user = sessionAuthService.login(command, lockKey, clientIp, request, response);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.userId());
        payload.put("name", user.name());
        payload.put("roles", user.roles());
        return ApiResponse.ok(payload, "로그인 성공");
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String clientIp = ClientIpResolver.resolve(request, loginAttemptProperties.getClientIpHeader());
        sessionAuthService.logout(clientIp, request);
        return ApiResponse.ok(null, "로그아웃 되었습니다.");
    }

    /** JWT 경로(LoginService#attemptKey)와 동일한 잠금키 규약 — 정책(LOGIN_ID|LOGIN_ID_AND_IP), IP 미상 시 loginId 폴백. */
    private String attemptKey(String loginId, String clientIp) {
        if (loginAttemptProperties.getKeyStrategy() == LoginAttemptProperties.KeyStrategy.LOGIN_ID_AND_IP
                && clientIp != null
                && !clientIp.isBlank()) {
            return loginId + "|" + clientIp;
        }
        return loginId;
    }
}
