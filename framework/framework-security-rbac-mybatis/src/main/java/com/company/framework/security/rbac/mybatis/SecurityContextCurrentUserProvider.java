package com.company.framework.security.rbac.mybatis;

import com.company.framework.mybatis.support.CurrentUserProvider;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 로그인 사용자 ID 를 감사필드(created_by/updated_by)에 공급.
 * framework-mybatis 의 기본(system) 구현을 대체한다(감사 브리지).
 *
 * <p>이전엔 framework-security 코어에 있었으나(코어→mybatis 의 두 번째 컴파일 결합), 보안-영속 결합 분리에서
 * 보안+MyBatis 를 모두 가진 유일한 자리인 이 어댑터로 이전했다. 어댑터 자동설정이 {@code @Primary} 로 등록하므로
 * framework-mybatis 의 기본 {@code defaultCurrentUserProvider}({@code @ConditionalOnMissingBean})보다 우선한다.
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
