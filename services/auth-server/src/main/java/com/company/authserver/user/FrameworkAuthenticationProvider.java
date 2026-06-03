package com.company.authserver.user;

import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.Authenticator;
import com.company.framework.security.auth.LoginCommand;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * SAS 의 폼 로그인(아이디/비번)을 우리 {@link Authenticator} 로 위임한다.
 *
 * <p>사용자 소스 = framework-security(DB/LDAP/SSO 등 프로젝트가 구현한 단일 인증 계약). AS 가 자체 사용자 저장소를 갖지 않고 기존 자산을 재사용한다(결정 ③·④).
 *
 * <p><b>직렬화 주의</b>: principal 로 우리 커스텀 타입 대신 표준 {@link User} 를 쓴다. SAS 의 {@code JdbcOAuth2AuthorizationService} 는
 * authorization_code 흐름의 {@code Authentication} 을 JSON 으로 저장하는데, Jackson 3 의 {@code PolymorphicTypeValidator} 가 기본적으로 Spring
 * Security 타입만 허용한다. 표준 {@code User} 는 {@code CoreJacksonModule} 이 처리하므로 커스텀 모듈 등록 없이 안전하다. (userId/name 등 우리 클레임은 {@link
 * RoleClaimTokenCustomizer} 가 토큰 생성 시 부여.)
 */
public final class FrameworkAuthenticationProvider implements AuthenticationProvider {

    private final Authenticator authenticator;

    public FrameworkAuthenticationProvider(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String loginId = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());
        try {
            AuthenticatedUser au = authenticator.authenticate(new LoginCommand(loginId, password, Map.of()));
            List<GrantedAuthority> authorities = toAuthorities(au.roles());
            // username = 우리 userId. 토큰 sub 로 이어지고, 클레임 매핑의 기준이 된다.
            User principal = new User(au.userId(), "", authorities);
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        } catch (RuntimeException e) {
            // 인증 실패는 표준 스프링 예외로 변환(SAS 폼 로그인 흐름이 처리).
            throw new BadCredentialsException("인증에 실패했습니다.", e);
        }
    }

    private static List<GrantedAuthority> toAuthorities(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
