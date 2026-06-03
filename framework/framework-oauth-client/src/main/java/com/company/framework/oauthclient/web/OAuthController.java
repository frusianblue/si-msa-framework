package com.company.framework.oauthclient.web;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.response.ApiResponse;
import com.company.framework.security.auth.TokenResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전사 표준 소셜 로그인 엔드포인트. 자체 로그인(/api/v1/auth/login)과 같은 네임스페이스 아래 둔다.
 *
 * <ul>
 *   <li>GET /api/v1/auth/oauth/{provider}/authorize — IdP 인가 페이지로 302 redirect
 *   <li>GET /api/v1/auth/oauth/{provider}/callback — code/state 수신 → 자체 토큰(JSON) 반환
 * </ul>
 *
 * <p>인증 경로이므로 SecurityAutoConfiguration 의 permitAll 화이트리스트(api/v1/auth/**)에 포함되어야 한다.
 */
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthController {

    private final OAuthLoginService oauthLoginService;

    public OAuthController(OAuthLoginService oauthLoginService) {
        this.oauthLoginService = oauthLoginService;
    }

    @GetMapping("/{provider}/authorize")
    public void authorize(@PathVariable String provider, HttpServletResponse response) throws IOException {
        response.sendRedirect(oauthLoginService.authorizeUrl(provider));
    }

    @GetMapping("/{provider}/callback")
    public ApiResponse<TokenResponse> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {
        if (error != null && !error.isBlank()) {
            throw new BusinessException(
                    ErrorCode.Common.UNAUTHORIZED,
                    "소셜 인증이 거부되었습니다: " + error + (errorDescription == null ? "" : " (" + errorDescription + ")"));
        }
        return ApiResponse.ok(oauthLoginService.callback(provider, code, state), "소셜 로그인 성공");
    }
}
