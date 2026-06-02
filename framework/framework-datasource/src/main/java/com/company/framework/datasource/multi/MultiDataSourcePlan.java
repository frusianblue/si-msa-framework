package com.company.framework.datasource.multi;

import java.util.Set;

/**
 * 독립 다중 DB 구성의 <b>순수 결정/검증 로직</b>(Spring/MyBatis 무의존).
 *
 * <p>"어느 키를 {@code @Primary} 로 삼을지", "설정이 모순되지 않는지"처럼 조용히 틀리면 운영에서 크게 터지는
 * 판단을 별도 함수로 떼어 JDK 단독(인메모리)으로 검증할 수 있게 한다. 실제 빈 등록은
 * {@link MultiDataSourceRegistrar} 가, Hikari/SqlSessionFactory 생성은 {@link MultiDataSourceSupport} 가 맡는다.
 */
public final class MultiDataSourcePlan {

    private MultiDataSourcePlan() {}

    /**
     * 어떤 데이터소스 키를 {@code @Primary} 로 삼을지 결정한다.
     *
     * <ul>
     *   <li>{@code sources} 가 비어 있으면 오류(켰는데 정의가 없음).
     *   <li>{@code configuredPrimary} 가 지정되면 그 키가 실제 존재해야 한다.
     *   <li>지정이 없고 소스가 정확히 1개면 그 1개가 자동 primary.
     *   <li>지정이 없고 소스가 2개 이상이면 모호하므로 오류(반드시 명시).
     * </ul>
     *
     * @param keys 설정된 데이터소스 키 집합(예: {@code {order, user}})
     * @param configuredPrimary {@code framework.datasource.multi.primary} 값(없으면 {@code null}/blank)
     * @return primary 로 확정된 키
     * @throws IllegalStateException 위 규칙 위반 시(메시지에 교정 방법 포함)
     */
    public static String resolvePrimaryKey(Set<String> keys, String configuredPrimary) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("framework.datasource.multi.enabled=true 인데 sources 가 비어 있습니다. "
                    + "framework.datasource.multi.sources.<키>.url 로 최소 1개 DB 를 정의하세요.");
        }
        if (configuredPrimary != null && !configuredPrimary.isBlank()) {
            if (!keys.contains(configuredPrimary)) {
                throw new IllegalStateException("framework.datasource.multi.primary='" + configuredPrimary
                        + "' 가 sources 키에 없습니다. 정의된 키: " + keys + ".");
            }
            return configuredPrimary;
        }
        if (keys.size() == 1) {
            return keys.iterator().next();
        }
        throw new IllegalStateException("sources 가 2개 이상이면(현재 키: " + keys + ") "
                + "framework.datasource.multi.primary 로 @Primary 데이터소스를 명시해야 합니다. "
                + "(Boot 기본 DataSource·Flyway·이름 없는 @Autowired DataSource 가 이 키로 해소됩니다.)");
    }

    /**
     * routing(읽기/쓰기 분리)과 multi(독립 다중 DB)의 동시 활성을 막는다. 둘 다 {@code @Primary} DataSource 를
     * 등록하므로 함께 켜면 빈 충돌로 기동 실패한다 → 사전에 명확한 메시지로 막는다.
     *
     * @param routingEnabled {@code framework.datasource.routing.enabled} 값
     * @throws IllegalStateException routing 이 켜져 있을 때
     */
    public static void assertNotConflictingWithRouting(boolean routingEnabled) {
        if (routingEnabled) {
            throw new IllegalStateException("framework.datasource.routing 과 framework.datasource.multi 는 동시에 켤 수 없습니다. "
                    + "둘 다 @Primary DataSource 를 등록해 충돌합니다. 하나만 선택하세요 "
                    + "(읽기/쓰기 분리=routing, 서로 다른 독립 DB 여러 개=multi).");
        }
    }

    /**
     * 데이터소스 키별로 일관된 빈 이름을 만든다(앱이 {@code @MapperScan} / {@code @Transactional} 에서 참조).
     *
     * <ul>
     *   <li>{@code <키>DataSource}
     *   <li>{@code <키>SqlSessionFactory}
     *   <li>{@code <키>SqlSessionTemplate}
     *   <li>{@code <키>TransactionManager}
     * </ul>
     */
    public static String dataSourceBeanName(String key) {
        return key + "DataSource";
    }

    public static String sqlSessionFactoryBeanName(String key) {
        return key + "SqlSessionFactory";
    }

    public static String sqlSessionTemplateBeanName(String key) {
        return key + "SqlSessionTemplate";
    }

    public static String transactionManagerBeanName(String key) {
        return key + "TransactionManager";
    }
}
