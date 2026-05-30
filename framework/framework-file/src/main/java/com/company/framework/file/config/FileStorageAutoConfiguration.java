package com.company.framework.file.config;

import com.company.framework.file.mapper.FileMapper;
import com.company.framework.file.service.FileService;
import com.company.framework.file.storage.FileStorage;
import com.company.framework.file.storage.FileSystemFileStorage;
import com.company.framework.file.web.FileController;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
    public FileService fileService(FileStorage storage, FileMapper fileMapper, FileStorageProperties props) {
        return new FileService(storage, fileMapper, props);
    }

    @Bean
    public FileController fileController(FileService fileService) {
        return new FileController(fileService);
    }
}
