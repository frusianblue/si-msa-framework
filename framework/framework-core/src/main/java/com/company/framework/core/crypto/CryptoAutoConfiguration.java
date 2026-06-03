package com.company.framework.core.crypto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(CryptoProperties.class)
@ConditionalOnProperty(prefix = "framework.crypto", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CryptoAutoConfiguration {

    @Bean
    public AesCryptoService aesCryptoService(CryptoProperties props) {
        AesCryptoService service = new AesCryptoService(props.getAesSecret());
        CryptoHolder.set(service); // TypeHandler 등 비-빈 영역에서 사용 가능하도록 등록
        return service;
    }

    /**
     * prod 에서 약한/기본 AES 마스터 키를 부팅 실패시키는 가드. ENC(...) 설정 복호화의 신뢰 기반이므로 강하게.
     * (EncryptedPropertyEnvironmentPostProcessor 는 컨텍스트 이전에 동작하므로 가드는 빈 시점에 평문 키를 검증.)
     */
    @Bean
    public AesMasterKeySafetyGuard aesMasterKeySafetyGuard(CryptoProperties props, Environment env) {
        return new AesMasterKeySafetyGuard(props, env);
    }
}
