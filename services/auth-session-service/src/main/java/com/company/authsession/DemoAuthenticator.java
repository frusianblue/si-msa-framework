package com.company.authsession;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.Authenticator;
import com.company.framework.security.auth.LoginCommand;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 이 서비스의 인증 계약 구현 — 데모용 인메모리 사용자.
 *
 * <p>{@link Authenticator} 하나만 등록하면 공통 세션 로그인 흐름({@code SessionAuthController})이 자동 활성화된다.
 * 실 운영에서는 이 자리를 DB/LDAP/SSO 검증으로 교체한다(예: {@code services/user-service} 의 {@code DbAuthenticationProvider}).
 */
@Component
public class DemoAuthenticator implements Authenticator {

    private record DemoUser(String encodedPassword, String name, List<String> roles) {}

    private final PasswordEncoder passwordEncoder;
    private final Map<String, DemoUser> users;

    public DemoAuthenticator(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.users = Map.of(
                "alice", new DemoUser(passwordEncoder.encode("Password1!"), "앨리스", List.of("USER")),
                "admin", new DemoUser(passwordEncoder.encode("Admin1234!"), "관리자", List.of("ADMIN", "USER")));
    }

    @Override
    public AuthenticatedUser authenticate(LoginCommand command) {
        DemoUser user = users.get(command.loginId());
        if (user == null || !passwordEncoder.matches(command.password(), user.encodedPassword())) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return new AuthenticatedUser(command.loginId(), user.name(), user.roles());
    }
}
