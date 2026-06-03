package com.company.framework.filebatch.config;

import com.company.framework.archive.Archiver;
import com.company.framework.filebatch.FileBatchProcessor;
import com.company.framework.filebatch.ops.BatchArchiveOps;
import com.company.framework.filebatch.ops.BatchImageOps;
import com.company.framework.image.ImageProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 파일 일괄처리 오토컨피그. {@code framework.file-batch.enabled=true} 일 때만 {@link FileBatchProcessor} 를 제공한다.
 *
 * <p>위임 작업 팩토리는 해당 모듈이 클래스패스에 있을 때만 백오프 없이 등록된다:
 * {@link BatchImageOps}({@code @ConditionalOnClass(ImageProcessor)} + 빈 존재), {@link BatchArchiveOps}
 * ({@code @ConditionalOnClass(Archiver)} + 빈 존재). 둘 다 없어도 {@link FileBatchProcessor} 와 rename 은 동작한다.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "framework.file-batch", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FileBatchProperties.class)
public class FileBatchAutoConfiguration {

    /** 일괄처리 오케스트레이터(순수 — 어떤 위임 모듈도 필요 없음). */
    @Bean
    @ConditionalOnMissingBean
    public FileBatchProcessor fileBatchProcessor(FileBatchProperties props) {
        return new FileBatchProcessor(props.getDefaultParallelism());
    }

    /** framework-image 가 있고 ImageProcessor 빈이 있을 때만 이미지 변환 팩토리 등록. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ImageProcessor.class)
    static class ImageOpsConfiguration {

        @Bean
        @ConditionalOnBean(ImageProcessor.class)
        @ConditionalOnMissingBean
        public BatchImageOps batchImageOps(ImageProcessor processor) {
            return new BatchImageOps(processor);
        }
    }

    /** framework-archive 가 있고 Archiver 빈이 있을 때만 압축 팩토리 등록. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Archiver.class)
    static class ArchiveOpsConfiguration {

        @Bean
        @ConditionalOnBean(Archiver.class)
        @ConditionalOnMissingBean
        public BatchArchiveOps batchArchiveOps(Archiver archiver) {
            return new BatchArchiveOps(archiver);
        }
    }
}
