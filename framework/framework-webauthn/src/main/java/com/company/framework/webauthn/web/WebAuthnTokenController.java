package com.company.framework.webauthn.web;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.response.ApiResponse;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.TokenResponse;
import com.company.framework.webauthn.token.WebAuthnTokenIssuer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 패스키 인증 성공(세션) → 프레임워크 표준 JWT 교환 엔드포인트.
 *
 * <p>흐름: SPA 가 {@code POST /login/webauthn} 로 패스키 assertion 을 검증하면 SS 가 <b>세션</b>에 인증을 수립한다(전용
 * SecurityFilterChain — {@code WebAuthnSecurityConfig}). 이어서 같은 세션 쿠키로 이 엔드포인트를 호출하면, 세션의
 * {@link Authentication} 을 읽어 {@link WebAuthnTokenIssuer} 로 프레임워크 access(JWT)+refresh 를 발급한다. 이후 SPA 는
 * 무상태 주류 체인에 대해 JWT 로 동작한다(외부 IdP 성공 후 자체 JWT 를 발급하는 oauth-client 패턴과 동일).
 *
 * <p>경로는 {@code framework.webauthn.token-path}(기본 {@code /api/v1/auth/webauthn/token}). 전용 체인이 이 경로를
 * {@code authenticated()} 로 보호하므로, 세션 인증이 없으면 진입 자체가 401 이다(아래 가드는 안전망).
 */
@RestController
public class WebAuthnTokenController {

    private final WebAuthnTokenIssuer tokenIssuer;
    private final WebAuthnAuthenticatedUserResolver userResolver;

    public WebAuthnTokenController(WebAuthnTokenIssuer tokenIssuer, WebAuthnAuthenticatedUserResolver userResolver) {
        this.tokenIssuer = tokenIssuer;
        this.userResolver = userResolver;
    }

    @PostMapping("${framework.webauthn.token-path:/api/v1/auth/webauthn/token}")
    public ApiResponse<TokenResponse> exchange() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "패스키 인증 세션이 없습니다.");
        }
        AuthenticatedUser user = userResolver.resolve(auth);
        return ApiResponse.ok(tokenIssuer.issue(user), "패스키 인증 성공");
    }
}
