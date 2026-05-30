package com.company.framework.mybatis.support;

import java.util.Optional;

/**
 * 감사필드(created_by/updated_by)에 채울 현재 사용자 ID 제공자.
 * 기본 구현은 "system". framework-security 가 SecurityContext 기반 구현으로 대체한다.
 */
public interface CurrentUserProvider {
    Optional<String> getCurrentUser();
}
