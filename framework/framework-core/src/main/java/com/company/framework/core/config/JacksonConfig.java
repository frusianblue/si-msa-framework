package com.company.framework.core.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * 전사 공통 JSON 직렬화 규칙. Spring Boot 4 = Jackson 3 (tools.jackson.*).
 *  - 알 수 없는 필드는 무시 (하위 호환 안전)
 *  - 날짜는 timestamp 가 아닌 ISO-8601 문자열로 직렬화
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer commonJacksonCustomizer() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
