package com.company.framework.webauthn.web;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.response.ApiResponse;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 패스키 자격증명 관리 엔드포인트(현재 사용자 기준 목록/삭제).
 *
 * <ul>
 *   <li>{@code GET  ${framework.webauthn.credentials-path:/api/v1/auth/webauthn/credentials}} — 내 패스키 목록.
 *   <li>{@code DELETE ${...credentials-path}/{credentialId}} — 내 패스키 1건 삭제(소유권 검증).
 * </ul>
 *
 * <p><b>인증 컨텍스트</b>: 이 경로들은 패스키 ceremony 전용 SecurityFilterChain(세션+CSRF, {@code WebAuthnSecurityConfig})
 * 의 {@code securityMatcher} 에 포함되어 {@code authenticated()} 로 보호된다. 즉 등록({@code POST /webauthn/register})과
 * <b>동일한 세션 인증 컨텍스트</b>에서 동작한다 — JWT 무상태 주류만 가진(웹오슨 세션이 없는) 호출자는 진입할 수 없다(설계 경계,
 * README "인증 컨텍스트" 참조). 삭제는 상태변경이라 전용 체인의 CSRF(쿠키 {@code XSRF-TOKEN} 더블서브밋)가 적용된다 —
 * SPA 는 {@code X-XSRF-TOKEN} 헤더를 동봉해야 한다. 목록(GET)은 안전 메서드라 CSRF 토큰이 필요 없다.
 */
@RestController
public class WebAuthnCredentialController {

    private final WebAuthnCredentialService credentialService;

    public WebAuthnCredentialController(WebAuthnCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping("${framework.webauthn.credentials-path:/api/v1/auth/webauthn/credentials}")
    public ApiResponse<List<WebAuthnCredentialSummary>> list() {
        Authentication auth = currentAuthentication();
        return ApiResponse.ok(credentialService.listForCurrentUser(auth));
    }

    @DeleteMapping("${framework.webauthn.credentials-path:/api/v1/auth/webauthn/credentials}/{credentialId}")
    public ApiResponse<Void> delete(@PathVariable String credentialId) {
        Authentication auth = currentAuthentication();
        credentialService.deleteForCurrentUser(auth, credentialId);
        return ApiResponse.ok();
    }

    /** 전용 체인이 이미 {@code authenticated()} 로 보호하므로 아래는 안전망(직접 빈 등록/오구성 대비). */
    private static Authentication currentAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "패스키 관리에는 인증이 필요합니다.");
        }
        return auth;
    }
}
