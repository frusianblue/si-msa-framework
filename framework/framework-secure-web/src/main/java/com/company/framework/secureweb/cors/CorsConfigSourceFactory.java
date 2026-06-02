package com.company.framework.secureweb.cors;

import com.company.framework.secureweb.config.SecureWebProperties;
import java.util.List;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 프로퍼티로부터 Spring {@link CorsConfiguration} 을 구성해 {@link UrlBasedCorsConfigurationSource} 로 만든다.
 * CORS 자체 구현(프리플라이트/credentials 처리)은 직접 짜지 않고 Spring 의 {@code CorsFilter} 에 위임한다
 * (프레임워크 원칙: 범용 표준 기능 재발명 금지).
 *
 * <p>주의: {@code allowCredentials=true} 일 때 origin 에 "*" 를 쓰면 브라우저/Spring 이 거부한다.
 * 이 경우 {@code allowed-origin-patterns} 를 사용해야 한다. 아래 팩토리는 설정값을 그대로 반영하므로,
 * credentials + origins "*" 조합은 호출 측에서 피해야 한다.
 */
public final class CorsConfigSourceFactory {

    private CorsConfigSourceFactory() {}

    public static UrlBasedCorsConfigurationSource create(SecureWebProperties.Cors cfg) {
        CorsConfiguration cc = new CorsConfiguration();

        if (notEmpty(cfg.getAllowedOriginPatterns())) {
            cc.setAllowedOriginPatterns(cfg.getAllowedOriginPatterns());
        }
        if (notEmpty(cfg.getAllowedOrigins())) {
            cc.setAllowedOrigins(cfg.getAllowedOrigins());
        }
        if (notEmpty(cfg.getAllowedMethods())) {
            cc.setAllowedMethods(cfg.getAllowedMethods());
        }
        if (notEmpty(cfg.getAllowedHeaders())) {
            cc.setAllowedHeaders(cfg.getAllowedHeaders());
        }
        if (notEmpty(cfg.getExposedHeaders())) {
            cc.setExposedHeaders(cfg.getExposedHeaders());
        }
        cc.setAllowCredentials(cfg.isAllowCredentials());
        cc.setMaxAge(cfg.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        String pattern =
                (cfg.getPathPattern() == null || cfg.getPathPattern().isBlank()) ? "/**" : cfg.getPathPattern();
        source.registerCorsConfiguration(pattern, cc);
        return source;
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }
}
