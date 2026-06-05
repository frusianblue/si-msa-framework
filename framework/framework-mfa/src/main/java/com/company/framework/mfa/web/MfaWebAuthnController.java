package com.company.framework.mfa.web;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.response.ApiResponse;
import com.company.framework.mfa.core.MfaService;
import com.company.framework.mfa.core.MfaWebAuthnService;
import com.company.framework.mybatis.support.CurrentUserProvider;
import com.company.framework.security.auth.LoginService;
import com.company.framework.security.auth.TokenResponse;
import com.company.framework.security.loginattempt.LoginAttemptProperties;
import com.company.framework.security.support.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebAuthn 2차 인증 엔드포인트(등록 + 로그인 2단계 검증). framework-webauthn 활성(=RP 빈 존재) 시에만
 * {@code MfaAutoConfiguration} 의 중첩 설정이 이 빈을 등록한다 — spring-security-webauthn 부재 앱에서는 이 컨트롤러
 * 자체가 로드되지 않아 클래스 로딩 문제가 없다(WebAuthn 결합을 별도 컨트롤러로 격리).
 *
 * <p>경로를 둘로 나눠 절대경로로 매핑한다(클래스 단위 prefix 대신):
 *
 * <ul>
 *   <li><b>등록(인증 필요, {@code /api/v1/mfa/webauthn/**})</b> — JWT 본인이 자신의 패스키를 MFA 수단으로 등록.
 *       {@code POST .../enroll} 로 옵션·티켓을 받고, {@code POST .../enroll/confirm} 으로 attestation 을 확정.
 *   <li><b>검증(permitAll, {@code /api/v1/auth/mfa/webauthn/**})</b> — 1단계 통과 티켓으로 2차 검증.
 *       {@code POST .../options} 로 assertion 옵션을 받고, {@code POST .../verify} 로 토큰을 발급.
 * </ul>
 *
 * <p>attestation/assertion 은 클라이언트가 {@code navigator.credentials.*} 결과를 문자열화(JSON.stringify)한
 * {@code credentialJson} 으로 보낸다(중첩 객체를 문자열로 받아 WebAuthn 전용 매퍼로 파싱 — 글로벌 Jackson 무영향).
 */
@RestController
public class MfaWebAuthnController {

    private final MfaWebAuthnService webAuthnService;
    private final CurrentUserProvider currentUserProvider;
    private final LoginService loginService;
    private final LoginAttemptProperties loginAttemptProperties;

    public MfaWebAuthnController(
            MfaWebAuthnService webAuthnService,
            CurrentUserProvider currentUserProvider,
            LoginService loginService,
            LoginAttemptProperties loginAttemptProperties) {
        this.webAuthnService = webAuthnService;
        this.currentUserProvider = currentUserProvider;
        this.loginService = loginService;
        this.loginAttemptProperties = loginAttemptProperties;
    }

    // ===================== 등록(인증 필요) =====================

    @PostMapping("/api/v1/mfa/webauthn/enroll")
    public ApiResponse<MfaWebAuthnService.WebAuthnRegistrationChallenge> enroll() {
        return ApiResponse.ok(webAuthnService.beginEnrollment(currentUserId()), "WebAuthn 등록을 시작했습니다.");
    }

    @PostMapping("/api/v1/mfa/webauthn/enroll/confirm")
    public ApiResponse<Void> confirm(@RequestBody Map<String, String> body) {
        String userId = currentUserId();
        webAuthnService.confirmEnrollment(
                userId, value(body, "ticket"), value(body, "credentialJson"), value(body, "label"));
        return ApiResponse.ok(null, "WebAuthn 인증이 등록되었습니다.");
    }

    // ===================== 로그인 2단계(permitAll) =====================

    @PostMapping("/api/v1/auth/mfa/webauthn/options")
    public ApiResponse<String> options(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(webAuthnService.beginAssertion(value(body, "ticket")));
    }

    @PostMapping("/api/v1/auth/mfa/webauthn/verify")
    public ApiResponse<TokenResponse> verify(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String clientIp = ClientIpResolver.resolve(request, loginAttemptProperties.getClientIpHeader());
        MfaService.MfaVerification verified =
                webAuthnService.verify(value(body, "ticket"), value(body, "credentialJson"), clientIp);
        TokenResponse tokens = loginService.completeMfa(verified.userId(), verified.roles(), clientIp);
        return ApiResponse.ok(tokens, "로그인 성공");
    }

    private static String value(Map<String, String> body, String key) {
        return body == null ? null : body.get(key);
    }

    private String currentUserId() {
        return currentUserProvider
                .getCurrentUser()
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED));
    }
}
