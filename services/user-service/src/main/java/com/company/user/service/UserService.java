package com.company.user.service;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.aspect.AuditLog;
import com.company.framework.core.page.PageRequest;
import com.company.framework.core.page.PageResponse;
import com.company.user.domain.User;
import com.company.user.dto.UserCreateRequest;
import com.company.user.dto.UserResponse;
import com.company.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional
    @AuditLog(action = "USER_CREATE", target = "USER")
    public UserResponse create(UserCreateRequest req) {
        userMapper.findByLoginId(req.loginId()).ifPresent(u -> {
            throw new BusinessException(ErrorCode.Common.CONFLICT, "이미 존재하는 로그인ID입니다: " + req.loginId());
        });
        User user = new User();
        user.setLoginId(req.loginId());
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPhone(req.phone());
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setCreatedBy("system");
        userMapper.insert(user);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        User user = userMapper.findById(id)
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
