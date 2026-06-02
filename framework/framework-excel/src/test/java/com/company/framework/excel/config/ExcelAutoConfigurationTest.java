package com.company.framework.excel.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.excel.exporter.ExcelExporter;
import com.company.framework.excel.importer.ExcelImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Excel 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@code framework.excel.enabled=true} → {@link ExcelExporter}/{@link ExcelImporter} 등록(외부 인프라 무의존).
 *   <li>기본(미설정/false) → 어떤 빈도 만들지 않음.
 * </ul>
 *
 * <p>POI(Workbook)는 이 모듈의 {@code implementation} 의존이라 자기 test 클래스패스엔 존재 →
 * {@code @ConditionalOnClass(Workbook)} 통과. 두 빈 모두 {@code ExcelProperties} 외 의존이 없어 스텁 불필요.
 */
class ExcelAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ExcelAutoConfiguration.class));

    @Test
    @DisplayName("enabled=true → Exporter/Importer 빈 등록")
    void registersBeansWhenEnabled() {
        runner.withPropertyValues("framework.excel.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ExcelExporter.class);
            assertThat(context).hasSingleBean(ExcelImporter.class);
        });
    }

    @Test
    @DisplayName("기본(비활성) → 어떤 Excel 빈도 만들지 않음")
    void backsOffWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ExcelExporter.class);
            assertThat(context).doesNotHaveBean(ExcelImporter.class);
        });
    }
}
