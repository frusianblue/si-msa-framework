package com.company.user.service;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.Authenticator;
import com.company.framework.security.auth.LoginCommand;
import com.company.framework.security.rbac.mapper.SecurityMapper;
import com.company.user.domain.User;
import com.company.user.mapper.UserMapper;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 이 프로젝트의 인증 방식 = DB 비밀번호 검증.
 * 이 빈(Authenticator) 하나만 등록하면 공통 LoginService/AuthController 가 자동 활성화된다.
 * 다른 프로젝트는 LDAP/SSO 구현으로 교체만 하면 됨(공통 코드 불변).
 */
@Component
public class DbAuthenticationProvider implements Authenticator {

    private final UserMapper userMapper;
    private final SecurityMapper securityMapper;
    private final PasswordEncoder passwordEncoder;

    public DbAuthenticationProvider(
            UserMapper userMapper, SecurityMapper securityMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.securityMapper = securityMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthenticatedUser authenticate(LoginCommand command) {
        User user = userMapper
                .findByLoginId(command.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));
        if (user.getPassword() == null || !passwordEncoder.matches(command.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        List<String> roles = securityMapper.findRolesByLoginId(command.loginId());
        return new AuthenticatedUser(user.getLoginId(), user.getName(), roles);
    }
}
