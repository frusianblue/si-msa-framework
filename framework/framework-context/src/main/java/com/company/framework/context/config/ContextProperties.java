package com.company.framework.context.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 요청 컨텍스트 설정. 접두사 {@code framework.context}. 선택형 모듈 컨벤션대로 기본 비활성.
 *
 * <pre>
 * framework:
 *   context:
 *     enabled: true                 # 모듈 전체 토글(기본 false)
 *     tenant-header: X-Tenant-Id    # 테넌트 식별 헤더명
 *     user-header: X-User-Id        # 사용자 식별 헤더명
 *     put-to-mdc: true              # 식별정보를 MDC 에 심어 로그 노출(기본 true)
 *     mdc-tenant-key: tenantId      # MDC 키(테넌트)
 *     mdc-user-key: userId          # MDC 키(사용자)
 *     propagate-downstream: true    # 아웃바운드 헤더 전파 인터셉터 빈 등록(기본 true)
 * </pre>
 */
@ConfigurationProperties(prefix = "framework.context")
public class ContextProperties {

    /** 모듈 전체 활성 여부(기본 false). */
    private boolean enabled = false;

    /** 테넌트 식별 헤더명(기본 X-Tenant-Id). */
    private String tenantHeader = "X-Tenant-Id";

    /** 사용자 식별 헤더명(기본 X-User-Id). */
    private String userHeader = "X-User-Id";

    /** 식별정보를 MDC 에 심을지(기본 true). */
    private boolean putToMdc = true;

    /** MDC 테넌트 키(기본 tenantId). */
    private String mdcTenantKey = "tenantId";

    /** MDC 사용자 키(기본 userId). */
    private String mdcUserKey = "userId";

    /** 아웃바운드 호출에 컨텍스트 헤더를 전파하는 인터셉터 빈을 등록할지(기본 true). */
    private boolean propagateDownstream = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantHeader() {
        return tenantHeader;
    }

    public void setTenantHeader(String tenantHeader) {
        this.tenantHeader = tenantHeader;
    }

    public String getUserHeader() {
        return userHeader;
    }

    public void setUserHeader(String userHeader) {
        this.userHeader = userHeader;
    }

    public boolean isPutToMdc() {
        return putToMdc;
    }

    public void setPutToMdc(boolean putToMdc) {
        this.putToMdc = putToMdc;
    }

    public String getMdcTenantKey() {
        return mdcTenantKey;
    }

    public void setMdcTenantKey(String mdcTenantKey) {
        this.mdcTenantKey = mdcTenantKey;
    }

    public String getMdcUserKey() {
        return mdcUserKey;
    }

    public void setMdcUserKey(String mdcUserKey) {
        this.mdcUserKey = mdcUserKey;
    }

    public boolean isPropagateDownstream() {
        return propagateDownstream;
    }

    public void setPropagateDownstream(boolean propagateDownstream) {
        this.propagateDownstream = propagateDownstream;
    }
}
