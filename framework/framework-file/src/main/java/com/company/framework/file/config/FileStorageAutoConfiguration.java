package com.company.framework.file.config;

import com.company.framework.file.mapper.FileMapper;
import com.company.framework.file.service.FileService;
import com.company.framework.file.storage.EncryptingFileStorage;
import com.company.framework.file.storage.FileStorage;
import com.company.framework.file.storage.FileSystemFileStorage;
import com.company.framework.file.validator.FileContentTypeValidator;
import com.company.framework.file.validator.NoOpFileContentTypeValidator;
import com.company.framework.file.validator.TikaFileContentTypeValidator;
import com.company.framework.file.web.FileController;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;

/**
 * 파일 모듈 자동설정. 저장소는 framework.file.storage.type 으로 선택:
 *  - local(기본)/nas → 파일시스템 저장소(여기서 등록)
 *  - s3 → framework-file-s3 모듈이 FileStorage 빈 제공
 */
@AutoConfiguration
@EnableConfigurationProperties(FileStorageProperties.class)
@ConditionalOnProperty(prefix = "framework.file", name = "enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("com.company.framework.file.mapper")
public class FileStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FileStorage.class)
    @ConditionalOnProperty(
            prefix = "framework.file.storage",
            name = "type",
            havingValue = "local",
            matchIfMissing = true)
    public FileStorage localFileStorage(FileStorageProperties props) {
        return new FileSystemFileStorage(props.getStorage().getBasePath(), "local");
    }

    @Bean
    @ConditionalOnMissingBean(FileStorage.class)
    @ConditionalOnProperty(prefix = "framework.file.storage", name = "type", havingValue = "nas")
    public FileStorage nasFileStorage(FileStorageProperties props) {
        return new FileSystemFileStorage(props.getStorage().getBasePath(), "nas");
    }

    @Bean
    public FileService fileService(
            FileStorage storage,
            FileMapper fileMapper,
            FileStorageProperties props,
            FileContentTypeValidator contentTypeValidator) {
        return new FileService(storage, fileMapper, props, contentTypeValidator);
    }

    @Bean
    public FileController fileController(FileService fileService) {
        return new FileController(fileService);
    }

    /**
     * 콘텐츠 타입 검증기. content-type-detection=true 이고 tika-core 가 클래스패스에 있으면 Tika 검증기를,
     * 아니면 기존 동작(클라이언트 contentType 신뢰)인 NoOp 을 등록한다. 토글이 켜졌는데 Tika 가 없으면 경고 후 NoOp.
     */
    @Bean
    @ConditionalOnMissingBean(FileContentTypeValidator.class)
    public FileContentTypeValidator fileContentTypeValidator(FileStorageProperties props) {
        FileStorageProperties.Validation v = props.getValidation();
        if (v.isContentTypeDetection()) {
            if (ClassUtils.isPresent("org.apache.tika.Tika", getClass().getClassLoader())) {
                return new TikaFileContentTypeValidator(v.getBlockedContentTypes());
            }
            LoggerFactory.getLogger(FileStorageAutoConfiguration.class)
                    .warn("framework.file.validation.content-type-detection=true 이지만 tika-core 가 클래스패스에 "
                            + "없어 콘텐츠 검증을 건너뜁니다 — 'org.apache.tika:tika-core' 의존을 추가하세요.");
        }
        return new NoOpFileContentTypeValidator();
    }

    /**
     * 저장소 at-rest 암호화. encrypt=true 면 등록된 FileStorage(local/nas/s3)를 EncryptingFileStorage 로 감싼다.
     * BeanPostProcessor 라 어느 백엔드 구현에도 일괄 적용된다. (static 메서드로 조기 초기화 방지)
     */
    @Bean
    @ConditionalOnProperty(prefix = "framework.file.storage", name = "encrypt", havingValue = "true")
    public static BeanPostProcessor fileStorageEncryptionPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof FileStorage fs && !(bean instanceof EncryptingFileStorage)) {
                    return new EncryptingFileStorage(fs);
                }
                return bean;
            }
        };
    }
}
