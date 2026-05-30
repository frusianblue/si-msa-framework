package com.company.framework.core.crypto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CryptoProperties.class)
@ConditionalOnProperty(prefix = "framework.crypto", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CryptoAutoConfiguration {

    @Bean
    public AesCryptoService aesCryptoService(CryptoProperties props) {
        AesCryptoService service = new AesCryptoService(props.getAesSecret());
        CryptoHolder.set(service);   // TypeHandler 등 비-빈 영역에서 사용 가능하도록 등록
        return service;
    }
}
