package com.company.framework.commoncode.config;

import com.company.framework.commoncode.mapper.CommonCodeMapper;
import com.company.framework.commoncode.service.CommonCodeService;
import com.company.framework.commoncode.struct.CommonCodeStructMapper;
import com.company.framework.commoncode.web.CommonCodeController;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 공통코드 모듈 자동설정. framework.commoncode.enabled=false 로 끌 수 있다.
 * MyBatis 매퍼/MapStruct 변환기/서비스/컨트롤러를 일괄 등록(서비스의 component-scan 불필요).
 */
@AutoConfiguration
@EnableConfigurationProperties(CommonCodeProperties.class)
@ConditionalOnProperty(prefix = "framework.commoncode", name = "enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("com.company.framework.commoncode.mapper")
public class CommonCodeAutoConfiguration {

    @Bean
    public CommonCodeStructMapper commonCodeStructMapper() {
        return Mappers.getMapper(CommonCodeStructMapper.class);
    }

    @Bean
    public CommonCodeService commonCodeService(CommonCodeMapper mapper, CommonCodeStructMapper struct) {
        return new CommonCodeService(mapper, struct);
    }

    @Bean
    public CommonCodeController commonCodeController(CommonCodeService service) {
        return new CommonCodeController(service);
    }
}
