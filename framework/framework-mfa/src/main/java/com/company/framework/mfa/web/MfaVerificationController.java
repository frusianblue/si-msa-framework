package com.company.framework.mfa.web;

import com.company.framework.core.response.ApiResponse;
import com.company.framework.mfa.core.MfaService;
import com.company.framework.security.auth.LoginService;
import com.company.framework.security.auth.TokenResponse;
import com.company.framework.security.loginattempt.LoginAttemptProperties;
import com.company.framework.security.support.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 2단계(2차 인증) 검증 엔드포인트(<b>permitAll</b>). 1단계 로그인이 {@code MfaRequired} 티켓을 반환한 뒤,
 * 클라이언트가 티켓 + 선택 방식 + 사용자 코드를 제시한다. 아직 JWT 가 없는 단계이므로 경로를
 * {@code /api/v1/auth/mfa/**}(SecurityAutoConfiguration 의 {@code /api/*&#47;auth/**} permitAll 매처에 포함) 아래 둔다.
 *
 * <ul>
 *   <li>{@code POST /verify} {@code {ticket, method, code}} — 코드 검증 성공 시 토큰 발급(기존 로그인 응답과 동일한 {@link TokenResponse}).
 *   <li>{@code POST /challenge/resend} {@code {ticket}} — OTP 재발송(TOTP 는 해당 없음).
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaVerificationController {

    private final MfaService mfaService;
    private final LoginService loginService;
    private final LoginAttemptProperties loginAttemptProperties;

    public MfaVerificationController(
            MfaService mfaService, LoginService loginService, LoginAttemptProperties loginAttemptProperties) {
        this.mfaService = mfaService;
        this.loginService = loginService;
        this.loginAttemptProperties = loginAttemptProperties;
    }

    @PostMapping("/verify")
    public ApiResponse<TokenResponse> verify(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String clientIp = ClientIpResolver.resolve(request, loginAttemptProperties.getClientIpHeader());
        String ticket = body.get("ticket");
        String method = body.get("method");
        String code = body.get("code");
        MfaService.MfaVerification verified = mfaService.verify(ticket, method, code, clientIp);
        TokenResponse tokens = loginService.completeMfa(verified.userId(), verified.roles(), clientIp);
        return ApiResponse.ok(tokens, "로그인 성공");
    }

    @PostMapping("/challenge/resend")
    public ApiResponse<Void> resend(@RequestBody Map<String, String> body) {
        mfaService.resendOtp(body.get("ticket"));
        return ApiResponse.ok(null, "인증 코드를 재발송했습니다.");
    }
}
