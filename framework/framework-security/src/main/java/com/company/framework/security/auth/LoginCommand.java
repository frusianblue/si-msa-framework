package com.company.framework.security.auth;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** 로그인 입력. 표준 필드 + 프로젝트별 추가 파라미터(params)로 확장. */
public record LoginCommand(
        @NotBlank(message = "로그인 ID는 필수입니다.") String loginId,
        @NotBlank(message = "비밀번호는 필수입니다.") String password,
        Map<String, String> params) {}
