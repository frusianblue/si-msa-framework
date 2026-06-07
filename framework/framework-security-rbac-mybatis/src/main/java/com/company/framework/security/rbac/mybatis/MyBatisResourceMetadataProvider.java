package com.company.framework.security.rbac.mybatis;

import com.company.framework.security.rbac.domain.Resource;
import com.company.framework.security.rbac.mapper.SecurityMapper;
import com.company.framework.security.rbac.spi.ResourceMetadataProvider;
import java.util.List;

/**
 * {@link ResourceMetadataProvider} 의 MyBatis 구현. URL-역할 매핑을 {@link SecurityMapper} 로 조회한다.
 * 보안 코어는 이 포트만 알고, MyBatis 결합은 이 어댑터 모듈에 갇힌다.
 */
public class MyBatisResourceMetadataProvider implements ResourceMetadataProvider {

    private final SecurityMapper securityMapper;

    public MyBatisResourceMetadataProvider(SecurityMapper securityMapper) {
        this.securityMapper = securityMapper;
    }

    @Override
    public List<Resource> findAllResources() {
        return securityMapper.findAllResources();
    }
}
