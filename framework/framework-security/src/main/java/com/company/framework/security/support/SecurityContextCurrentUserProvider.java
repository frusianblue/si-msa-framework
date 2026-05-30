package com.company.framework.security.support;

import com.company.framework.mybatis.support.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 로그인 사용자 ID 를 감사필드(created_by/updated_by)에 공급.
 * framework-mybatis 의 기본(system) 구현을 대체한다.
 */
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {
    @Override
    public Optional<String> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }
}
