package com.company.framework.webauthn.web;

import com.company.framework.security.auth.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * 기본 resolver. principal 이름을 userId/name 으로, 권한 목록을 roles 로 매핑한다. Spring Security 권한은
 * {@code ROLE_*} 접두형이고 프레임워크 {@link AuthenticatedUser#roles()} 는 접두 없는 역할명을 쓰므로
 * (JwtProvider 가 발급 시 다시 {@code ROLE_} 를 붙인다) 접두를 제거해 자체 비밀번호 로그인과 동일한 형태로 맞춘다.
 */
public class DefaultWebAuthnAuthenticatedUserResolver implements WebAuthnAuthenticatedUserResolver {

    @Override
    public AuthenticatedUser resolve(Authentication authentication) {
        String userId = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList();
        return new AuthenticatedUser(userId, userId, roles);
    }
}
