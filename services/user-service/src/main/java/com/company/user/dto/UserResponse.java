package com.company.user.dto;

import com.company.framework.core.util.MaskingUtils;
import com.company.user.domain.User;

/**
 * 응답 시 개인정보는 공통 마스킹 유틸로 가공.
 */
public record UserResponse(Long id, String loginId, String name, String email, String phone, String role) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getLoginId(),
                MaskingUtils.maskName(u.getName()),
                MaskingUtils.maskEmail(u.getEmail()),
                MaskingUtils.maskPhone(u.getPhone()),
                u.getRole());
    }
}
