package com.company.framework.security.rbac.core;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * DB 기반 동적 인가. 요청 URL 에 매핑된 역할 중 하나라도 사용자가 보유하면 허용.
 * 매핑이 없는 URL 은 '인증만 되면 허용'(기본 정책) — 필요 시 deny 로 변경 가능.
 */
public class DynamicAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final SecurityMetadataService metadataService;

    public DynamicAuthorizationManager(SecurityMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public AuthorizationDecision authorize(
            Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        var request = context.getRequest();
        List<String> requiredRoles = metadataService.requiredRoles(request.getRequestURI(), request.getMethod());

        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        // 매핑이 없으면 인증된 사용자에게 허용 (정책에 따라 false 로 강화 가능)
        if (requiredRoles.isEmpty()) {
            return new AuthorizationDecision(true);
        }
        Set<String> userRoles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        boolean granted = requiredRoles.stream().anyMatch(userRoles::contains);
        return new AuthorizationDecision(granted);
    }
}
