package com.company.framework.excel.config;

import com.company.framework.excel.exporter.ExcelExporter;
import com.company.framework.excel.importer.ExcelImporter;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Excel 모듈 오토컨피그.
 *
 * <ul>
 *   <li>1단(모듈): {@code @ConditionalOnClass(Workbook)} — POI 가 클래스패스에 있어야 활성.
 *   <li>2단(기능): {@code framework.excel.enabled=true} → {@link ExcelExporter}/{@link ExcelImporter} 빈 제공.
 * </ul>
 *
 * <p>두 빈 모두 외부 인프라(DB/Kafka 등) 의존이 없어 켜는 즉시 사용 가능하다. 업무 코드는 빈을 주입해
 * 다운로드(스트리밍)·업로드(양식검증)에 바로 쓴다.
 */
@AutoConfiguration
@ConditionalOnClass(Workbook.class)
@ConditionalOnProperty(prefix = "framework.excel", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ExcelProperties.class)
public class ExcelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExcelExporter excelExporter(ExcelProperties properties) {
        return new ExcelExporter(properties.getExport().getWindowSize());
    }

    @Bean
    @ConditionalOnMissingBean
    public ExcelImporter excelImporter(ExcelProperties properties) {
        return new ExcelImporter(properties.getImport().getMaxRows());
    }
}
