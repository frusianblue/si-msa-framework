package com.company.framework.security.auth;

import com.company.framework.core.response.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 전사 표준 인증 엔드포인트. 인증 방식이 달라도 모든 프로젝트가 동일한 URL/응답을 갖는다.
 * 인증 경로(api/v1/auth 등)는 SecurityAutoConfiguration 에서 permitAll 로 열려 있다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@RequestBody LoginCommand command) {
        return ApiResponse.ok(loginService.login(command), "로그인 성공");
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(loginService.refresh(body.get("refreshToken")));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, String> body) {
        String accessToken =
                (authorization != null && authorization.startsWith("Bearer ")) ? authorization.substring(7) : null;
        String refreshToken = body == null ? null : body.get("refreshToken");
        loginService.logout(accessToken, refreshToken);
        return ApiResponse.ok(null, "로그아웃 되었습니다.");
    }
}
