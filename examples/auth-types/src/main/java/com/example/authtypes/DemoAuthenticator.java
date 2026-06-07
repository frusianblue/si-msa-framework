package com.example.authtypes;

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
 * 카탈로그용 데모 인증기 — 인메모리 사용자.
 *
 * <p>프로젝트는 {@link Authenticator} 하나만 구현하면 인증 방식(세션/JWT/...)과 무관하게 공통 로그인 흐름이 동작한다.
 * 이 빈 하나로 모든 트랙(T1 세션, T2 JWT, ...)이 같은 사용자/검증 로직을 공유한다 — 바뀌는 건 "방식"뿐.
 *
 * <p>실서비스는 이 자리를 DB/LDAP/SSO 검증으로 교체한다(예: {@code services/user-service} 의 {@code DbAuthenticationProvider}).
 */
@Component
public class DemoAuthenticator implements Authenticator {

    /** 데모 계정: 로그인ID → (평문 비번, 이름, 역할). 평문은 부팅 시 BCrypt 로 인코딩해 보관. */
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
