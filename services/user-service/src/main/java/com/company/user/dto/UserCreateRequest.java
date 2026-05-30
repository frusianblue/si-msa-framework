package com.company.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
        @NotBlank(message = "로그인ID는 필수입니다.") String loginId,
        @NotBlank(message = "이름은 필수입니다.") String name,
        @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
        String phone) {}
