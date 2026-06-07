package com.company.framework.security.rbac.core;

import com.company.framework.security.rbac.domain.Resource;
import com.company.framework.security.rbac.spi.ResourceMetadataProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.util.AntPathMatcher;

/**
 * URL-권한 매핑을 영속 포트({@link ResourceMetadataProvider})에서 로딩해 메모리에 캐시한다.
 * 운영에서 권한 변경 시 reload() 호출(관리자 API/스케줄러)로 무중단 반영.
 *
 * <p>특정 영속 기술에 결합되지 않도록 MyBatis 매퍼가 아닌 {@link ResourceMetadataProvider} 포트에 의존한다.
 * 구현은 어댑터 모듈(예: {@code framework-security-rbac-mybatis})이 제공한다.
 */
public final class SecurityMetadataService {

    private final ResourceMetadataProvider resourceProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final AtomicReference<List<Resource>> cache = new AtomicReference<>(List.of());

    public SecurityMetadataService(ResourceMetadataProvider resourceProvider) {
        this.resourceProvider = resourceProvider;
        reload();
    }

    public void reload() {
        try {
            cache.set(resourceProvider.findAllResources());
        } catch (Exception e) {
            // 기동 시 테이블 미생성/DB 미연결 등은 빈 캐시로 시작하고 경고만 남긴다.
            org.slf4j.LoggerFactory.getLogger(SecurityMetadataService.class)
                    .warn("리소스-권한 매핑 로딩 실패(빈 캐시로 시작): {}", e.getMessage());
        }
    }

    /** 요청 URL+메서드 에 매칭되는 리소스들이 요구하는 역할 목록 반환 */
    public List<String> requiredRoles(String requestUri, String method) {
        return cache.get().stream()
                .filter(r -> pathMatcher.match(r.getUrlPattern(), requestUri))
                .filter(r -> "ALL".equalsIgnoreCase(r.getHttpMethod()) || method.equalsIgnoreCase(r.getHttpMethod()))
                .map(Resource::getRoleName)
                .toList();
    }
}
