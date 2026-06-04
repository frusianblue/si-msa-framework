package com.company.authserver.user;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
// OAuth2TokenType 은 .token 하위가 아니라 server.authorization 패키지에 있다(SS7 7.0.5 확인).

/**
 * 발급 토큰(access_token, id_token)에 우리 권한을 클레임으로 싣는다.
 *
 * <p>RP/리소스 서버가 기존 자체 JWT 와 동일한 방식으로 권한을 읽을 수 있도록 {@code roles} 클레임을 표준화한다(우리 게이트웨이/리소스 서버 규약과 정합).
 */
public final class RoleClaimTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        OAuth2TokenType type = context.getTokenType();
        boolean isAccess = OAuth2TokenType.ACCESS_TOKEN.equals(type);
        boolean isIdToken = "id_token".equals(type.getValue());
        if (!isAccess && !isIdToken) {
            return;
        }
        Authentication principal = context.getPrincipal();
        // 앱 역할만 roles 로 — 인증 팩터(FactorGrantedAuthority, 예: FACTOR_PASSWORD)는 제외한다.
        // SS7 form-login 인증은 권한에 인증 팩터를 함께 싣는데(id_token auth_time 산출용), 이는 인증 메커니즘
        // 메타데이터지 인가용 역할이 아니므로 roles 클레임/다운스트림 권한으로 새 나가면 안 된다.
        List<String> roles = principal.getAuthorities().stream()
                .filter(authority -> !(authority instanceof FactorGrantedAuthority))
                .map(GrantedAuthority::getAuthority)
                .toList();
        context.getClaims().claim("roles", roles);
        // 표준 sub 는 이미 principal.getName()(= 우리 userId)로 설정된다. 추가 커스텀 클레임은 여기서.
    }
}
