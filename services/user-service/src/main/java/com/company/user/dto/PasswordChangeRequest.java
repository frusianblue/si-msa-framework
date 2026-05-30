package com.company.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 본인 비밀번호 변경 요청. 현재 비밀번호로 본인 확인 후 새 비밀번호로 교체.
 * 새 비밀번호 강도는 공통 PasswordPolicy 가 검증한다(DTO 는 필수값만).
 */
public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다.") String currentPassword,
        @NotBlank(message = "새 비밀번호는 필수입니다.") String newPassword) {}
