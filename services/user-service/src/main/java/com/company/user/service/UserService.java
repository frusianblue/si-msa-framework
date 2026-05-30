package com.company.user.service;

import com.company.framework.core.aspect.AuditLog;
import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.page.PageRequest;
import com.company.framework.core.page.PageResponse;
import com.company.framework.mybatis.support.CurrentUserProvider;
import com.company.framework.security.password.PasswordPolicy;
import com.company.user.domain.User;
import com.company.user.dto.PasswordChangeRequest;
import com.company.user.dto.PasswordResetRequest;
import com.company.user.dto.UserCreateRequest;
import com.company.user.dto.UserResponse;
import com.company.user.mapper.UserMapper;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    public UserService(
            UserMapper userMapper,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            CurrentUserProvider currentUserProvider) {
        this.userMapper = userMapper;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    @AuditLog(action = "USER_CREATE", target = "USER")
    public UserResponse create(UserCreateRequest req) {
        userMapper.findByLoginId(req.loginId()).ifPresent(u -> {
            throw new BusinessException(ErrorCode.Common.CONFLICT, "이미 존재하는 로그인ID입니다: " + req.loginId());
        });
        passwordPolicy.validate(req.password()); // 강도 검증(위반 시 INVALID_INPUT)
        User user = new User();
        user.setLoginId(req.loginId());
        user.setPassword(passwordEncoder.encode(req.password())); // 항상 BCrypt 로 저장
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPhone(req.phone());
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setCreatedBy(currentUserProvider.getCurrentUser().orElse("system"));
        userMapper.insert(user);
        return UserResponse.from(user);
    }

    /**
     * 본인 비밀번호 변경: 인증 컨텍스트의 사용자 본인만 대상(타인 변경 불가).
     * 현재 비밀번호 확인 → 동일 비번 거부 → 정책 검증 → BCrypt 재저장.
     */
    @Transactional
    @AuditLog(action = "USER_PASSWORD_CHANGE", target = "USER")
    public void changeMyPassword(PasswordChangeRequest req) {
        String loginId = currentUserProvider
                .getCurrentUser()
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED, "인증 정보가 없습니다."));
        User user = userMapper
                .findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.NOT_FOUND, "사용자를 찾을 수 없습니다: " + loginId));
        if (user.getPassword() == null || !passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        passwordPolicy.validate(req.newPassword()); // 강도 검증(위반 시 INVALID_INPUT)
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(req.newPassword()), loginId);
    }

    /**
     * 관리자 강제 초기화: 현재 비밀번호 없이 대상 사용자 비밀번호를 교체(ADMIN 전용 — 컨트롤러에서 인가).
     * 본인 확인 절차가 없으므로 반드시 호출 측에서 ADMIN 권한을 강제할 것.
     */
    @Transactional
    @AuditLog(action = "USER_PASSWORD_RESET", target = "USER")
    public void resetPassword(Long id, PasswordResetRequest req) {
        User user = userMapper
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.NOT_FOUND, "사용자를 찾을 수 없습니다: " + id));
        passwordPolicy.validate(req.newPassword()); // 강도 검증(위반 시 INVALID_INPUT)
        String updatedBy = currentUserProvider.getCurrentUser().orElse("system");
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(req.newPassword()), updatedBy);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        User user = userMapper
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.NOT_FOUND, "사용자를 찾을 수 없습니다: " + id));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(PageRequest page) {
        long total = userMapper.countAll();
        var content = userMapper.findPage(page).stream().map(UserResponse::from).toList();
        return PageResponse.of(content, page, total);
    }
}
