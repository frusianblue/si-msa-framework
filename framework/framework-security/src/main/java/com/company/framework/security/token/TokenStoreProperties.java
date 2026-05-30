package com.company.framework.security.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   security:
 *     token-store:
 *       type: memory   # memory | jdbc | redis
 */
@ConfigurationProperties(prefix = "framework.security.token-store")
public class TokenStoreProperties {
    private String type = "memory";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
