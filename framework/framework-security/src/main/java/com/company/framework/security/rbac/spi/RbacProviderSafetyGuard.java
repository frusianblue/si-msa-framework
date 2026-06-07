package com.company.framework.security.rbac.spi;

/**
 * RBAC 영속 포트 부재 안전장치(fail-fast). dev-auth/JWT-secret 가드와 동일 패턴.
 *
 * <p>{@code framework.security.dynamic-authorization=true}(기본) 인데 {@link ResourceMetadataProvider}
 * 어댑터 빈이 없으면, 동적 인가가 <b>조용히 무력화</b>(매핑 0건 → 인증만 되면 전부 허용)되어 운영 사고로 이어진다.
 * 이를 막기 위해 해당 조건에서 이 가드 빈이 등록되며, 생성 즉시 부팅을 실패시킨다.
 *
 * <p>활성/부재 판정은 {@code SecurityAutoConfiguration} 의
 * {@code @ConditionalOnProperty(dynamic-authorization=true)} + {@code @ConditionalOnMissingBean(ResourceMetadataProvider)}
 * 가 담당한다. 프로파일과 무관하게(local/dev/prod 동일) 실패시켜 환경별 누락을 회귀 없이 드러낸다.
 */
public final class RbacProviderSafetyGuard {

    public RbacProviderSafetyGuard() {
        throw new IllegalStateException(
                "[보안 차단] framework.security.dynamic-authorization=true 이면 RBAC provider 가 필요합니다 — "
                        + "RBAC 영속 어댑터를 의존에 추가하세요(예: implementation project(':framework:framework-security-rbac-mybatis')). "
                        + "동적 인가를 쓰지 않는다면 framework.security.dynamic-authorization=false 로 명시하세요"
                        + "(이 경우 DataSource/MyBatis 없이 인증만으로 부팅).");
    }
}
