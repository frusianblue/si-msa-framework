package com.company.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 관리자 비밀번호 강제 초기화 요청. 현재 비밀번호 없이 새 비밀번호로 교체(ADMIN 전용).
 * 새 비밀번호 강도는 공통 PasswordPolicy 가 검증한다.
 */
public record PasswordResetRequest(
        @NotBlank(message = "새 비밀번호는 필수입니다.") String newPassword) {}
