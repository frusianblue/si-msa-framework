package com.company.framework.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 공통 OpenAPI 설정. JWT Bearer 보안 스키마를 자동 등록해
 * Swagger UI 에서 'Authorize' 로 토큰을 넣어 인증 API 를 테스트할 수 있다.
 * Swagger UI: /swagger-ui.html   OpenAPI JSON: /v3/api-docs
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@EnableConfigurationProperties(OpenApiProperties.class)
@ConditionalOnProperty(prefix = "framework.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiAutoConfiguration {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI frameworkOpenAPI(OpenApiProperties props) {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
        return new OpenAPI()
                .info(new Info()
                        .title(props.getTitle())
                        .version(props.getVersion())
                        .description(props.getDescription()))
                .components(new Components().addSecuritySchemes(SCHEME, bearer))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
