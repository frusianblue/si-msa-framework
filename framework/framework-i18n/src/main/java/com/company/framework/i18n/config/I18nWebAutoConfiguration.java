package com.company.framework.i18n.config;

import com.company.framework.i18n.core.ErrorMessageResolver;
import com.company.framework.i18n.web.I18nExceptionAdvice;
import java.util.Locale;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * i18n 웹 연동. 서블릿 웹앱에서만 활성.
 * - Accept-Language 헤더로 로케일 결정(AcceptHeaderLocaleResolver)
 * - BusinessException 메시지 i18n 화(error-localization=true 일 때만)
 */
@AutoConfiguration
@ConditionalOnClass(LocaleResolver.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework.i18n", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(I18nProperties.class)
public class I18nWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "localeResolver")
    public LocaleResolver localeResolver(I18nProperties props) {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.forLanguageTag(props.getDefaultLocale()));
        return resolver;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "framework.i18n", name = "error-localization", havingValue = "true",
            matchIfMissing = true)
    public I18nExceptionAdvice i18nExceptionAdvice(ErrorMessageResolver resolver) {
        return new I18nExceptionAdvice(resolver);
    }
}
