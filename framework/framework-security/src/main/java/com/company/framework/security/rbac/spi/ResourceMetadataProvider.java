package com.company.framework.security.rbac.spi;

import com.company.framework.security.rbac.domain.Resource;
import java.util.List;

/**
 * RBAC 동적 인가용 영속 포트(SPI).
 *
 * <p>보안 코어는 URL-역할 매핑을 <b>이 포트로만</b> 조회한다. 특정 영속 기술(MyBatis/JPA/JDBC)에 결합되지
 * 않도록, 실제 구현은 어댑터 모듈이 제공한다(예: {@code framework-security-rbac-mybatis} 의
 * {@code MyBatisResourceMetadataProvider}). {@code Authenticator} 와 동일한 포트/어댑터 사상.
 *
 * <p>이 포트 빈이 컨텍스트에 존재할 때만 코어의 {@code SecurityMetadataService}/
 * {@code DynamicAuthorizationManager} 가 활성화된다({@code @ConditionalOnBean}). 또한
 * {@code dynamic-authorization=true} 인데 이 포트가 없으면 부팅을 실패시켜 "조용한 인가 무력화"를 차단한다
 * (fail-fast).
 */
public interface ResourceMetadataProvider {

    /** URL-역할 매핑 전체(동적 인가용). 매핑이 없으면 빈 리스트. */
    List<Resource> findAllResources();
}
