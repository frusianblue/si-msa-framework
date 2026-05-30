package com.company.framework.security.auth;

import java.util.Map;

/** 로그인 입력. 표준 필드 + 프로젝트별 추가 파라미터(params)로 확장. */
public record LoginCommand(String loginId, String password, Map<String, String> params) {}
