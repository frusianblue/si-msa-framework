package com.company.authserver.user;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.Authenticator;
import com.company.framework.security.auth.LoginCommand;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * auth-server 운영 인증기 — authdb {@code app_user} 비밀번호 검증.
 *
 * <p>user-service 의 {@code DbAuthenticationProvider} 와 동일 패턴(=프레임워크의 Authenticator SPI 구현).
 * 실 프로젝트는 이 빈을 LDAP/AD/GPKI 등 자체 구현으로 교체할 수 있다(공통 로그인 흐름 불변).
 *
 * <p>비밀번호는 framework-security 의 위임형 인코더(BcryptEnforcingPasswordEncoder)로 검증하므로
 * 저장값은 {@code {bcrypt}$2a$...} 포맷이어야 한다(평문/{noop} 은 매칭 거부).
 */
public class DbAuthenticator implements Authenticator {

    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public DbAuthenticator(AppUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthenticatedUser authenticate(LoginCommand command) {
        AppUser user = userMapper
                .findByLoginId(command.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "비활성화된 계정입니다.");
        }
        if (user.getPassword() == null || !passwordEncoder.matches(command.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return new AuthenticatedUser(user.getLoginId(), user.getName(), List.of(user.getRole()));
    }
}
