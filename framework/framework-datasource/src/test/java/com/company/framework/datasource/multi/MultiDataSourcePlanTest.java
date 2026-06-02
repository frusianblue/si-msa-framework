package com.company.framework.datasource.multi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link MultiDataSourcePlan} 의 순수 결정/검증 로직(Spring 무의존). */
class MultiDataSourcePlanTest {

    private static Set<String> keys(String... k) {
        return new LinkedHashSet<>(List.of(k));
    }

    @Test
    void singleSourceBecomesPrimaryAutomatically() {
        assertThat(MultiDataSourcePlan.resolvePrimaryKey(keys("order"), null)).isEqualTo("order");
        assertThat(MultiDataSourcePlan.resolvePrimaryKey(keys("order"), "  ")).isEqualTo("order");
    }

    @Test
    void explicitPrimaryIsHonored() {
        assertThat(MultiDataSourcePlan.resolvePrimaryKey(keys("order", "user"), "user"))
                .isEqualTo("user");
    }

    @Test
    void multipleSourcesWithoutPrimaryFails() {
        assertThatThrownBy(() -> MultiDataSourcePlan.resolvePrimaryKey(keys("order", "user"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary");
    }

    @Test
    void primaryNotAmongSourcesFails() {
        assertThatThrownBy(() -> MultiDataSourcePlan.resolvePrimaryKey(keys("order", "user"), "nope"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void emptySourcesFails() {
        assertThatThrownBy(() -> MultiDataSourcePlan.resolvePrimaryKey(keys(), "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sources");
    }

    @Test
    void routingConflictFailsAndOffPasses() {
        assertThatThrownBy(() -> MultiDataSourcePlan.assertNotConflictingWithRouting(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("동시에 켤 수 없습니다");
        MultiDataSourcePlan.assertNotConflictingWithRouting(false); // no throw
    }

    @Test
    void beanNameConvention() {
        assertThat(MultiDataSourcePlan.dataSourceBeanName("order")).isEqualTo("orderDataSource");
        assertThat(MultiDataSourcePlan.sqlSessionFactoryBeanName("order")).isEqualTo("orderSqlSessionFactory");
        assertThat(MultiDataSourcePlan.sqlSessionTemplateBeanName("order")).isEqualTo("orderSqlSessionTemplate");
        assertThat(MultiDataSourcePlan.transactionManagerBeanName("user")).isEqualTo("userTransactionManager");
    }
}
