package com.company.authserver.user;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        context.getClaims().claim("roles", roles);
        // 표준 sub 는 이미 principal.getName()(= 우리 userId)로 설정된다. 추가 커스텀 클레임은 여기서.
    }
}
