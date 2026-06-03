package com.company.framework.image.config;

import com.company.framework.image.DefaultImageProcessor;
import com.company.framework.image.ImageProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 이미지 모듈 오토컨피그. {@code framework.image.enabled=true} 일 때만 {@link ImageProcessor} 빈을 제공한다.
 *
 * <p>웹 비의존(배치/스케줄 등 비웹 컨텍스트에서도 사용 가능) — 엔진은 JDK 내장 ImageIO 만 쓰므로 조건부 클래스 검사 불필요.
 * 앱이 {@link ImageProcessor} 빈을 직접 정의하면 그쪽이 우선({@code @ConditionalOnMissingBean}).
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "framework.image", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ImageProperties.class)
public class ImageAutoConfiguration {

    /** 기본 이미지 프로세서(ImageIO 기반). 폭탄 상한/썸네일 기본 포맷·품질을 프로퍼티에서 주입. */
    @Bean
    @ConditionalOnMissingBean
    public ImageProcessor imageProcessor(ImageProperties props) {
        return new DefaultImageProcessor(props.getMaxSourcePixels(), props.getDefaultFormat(), props.getJpegQuality());
    }
}
