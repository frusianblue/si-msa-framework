package com.company.framework.archive.config;

import com.company.framework.archive.Archiver;
import com.company.framework.archive.ZipArchiver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 아카이빙 모듈 오토컨피그. {@code framework.archive.enabled=true} 일 때만 {@link Archiver} 빈을 제공한다.
 *
 * <p>웹 비의존(배치/스케줄 등 비웹 컨텍스트에서도 사용 가능) — 엔진은 JDK 내장 {@code java.util.zip} 만 쓴다.
 * 앱이 {@link Archiver} 빈을 직접 정의하면 그쪽이 우선({@code @ConditionalOnMissingBean}).
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "framework.archive", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ArchiveProperties.class)
public class ArchiveAutoConfiguration {

    /** 기본 ZIP/GZIP 아카이버. 압축폭탄 상한(엔트리 수·엔트리 크기·총 바이트)을 프로퍼티에서 주입. */
    @Bean
    @ConditionalOnMissingBean
    public Archiver archiver(ArchiveProperties props) {
        return new ZipArchiver(props.getMaxEntries(), props.getMaxEntrySize(), props.getMaxTotalBytes());
    }
}
