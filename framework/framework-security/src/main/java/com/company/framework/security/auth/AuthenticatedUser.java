package com.company.framework.security.auth;

import java.util.List;
import java.util.Map;

/** 인증 성공 결과 표준 모델. 공통이 이걸로 토큰을 만든다. */
public record AuthenticatedUser(String userId, String name, List<String> roles, Map<String, Object> extra) {
    public AuthenticatedUser(String userId, String name, List<String> roles) {
        this(userId, name, roles, Map.of());
    }
}
