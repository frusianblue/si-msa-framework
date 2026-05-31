package com.company.framework.i18n.config;

import com.company.framework.i18n.core.ErrorMessageResolver;
import com.company.framework.i18n.core.MessageResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * i18n 코어 오토컨피그.
 * 1단(모듈): @ConditionalOnClass(MessageResolver) — 이 모듈 의존 시에만 활성.
 * 2단(기능): framework.i18n.enabled=true.
 * 3단(override): @ConditionalOnMissingBean — 프로젝트가 messageSource 를 직접 등록하면 양보.
 */
@AutoConfiguration
@ConditionalOnClass(MessageResolver.class)
@ConditionalOnProperty(prefix = "framework.i18n", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(I18nProperties.class)
public class I18nAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "messageSource")
    public MessageSource messageSource(I18nProperties props) {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasenames(props.getBasenames().toArray(String[]::new));
        ms.setDefaultEncoding(props.getEncoding());
        ms.setCacheSeconds(props.getCacheSeconds());
        ms.setFallbackToSystemLocale(false); // 미발견 시 시스템 로케일로 새지 않게
        ms.setUseCodeAsDefaultMessage(false); // 폴백은 resolver 가 명시적으로 처리
        return ms;
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageResolver messageResolver(MessageSource messageSource) {
        return new MessageResolver(messageSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorMessageResolver errorMessageResolver(MessageResolver messageResolver) {
        return new ErrorMessageResolver(messageResolver);
    }
}
