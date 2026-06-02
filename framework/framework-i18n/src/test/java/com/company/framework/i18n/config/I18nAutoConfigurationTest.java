package com.company.framework.i18n.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.i18n.core.ErrorMessageResolver;
import com.company.framework.i18n.core.MessageResolver;
import com.company.framework.i18n.web.I18nExceptionAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.LocaleResolver;

/**
 * i18n 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>코어({@link I18nAutoConfiguration}, ApplicationContextRunner): {@code enabled=true} → MessageSource/
 *       {@link MessageResolver}/{@link ErrorMessageResolver}; 기본 → 없음.
 *   <li>웹({@link I18nWebAutoConfiguration}, {@code @ConditionalOnWebApplication(SERVLET)}): 서블릿 컨텍스트에서만
 *       활성 → {@link WebApplicationContextRunner} 로 검증. {@code i18nExceptionAdvice} 가 {@link ErrorMessageResolver}
 *       를 요구하므로 두 오토컨피그를 함께 로드한다.
 * </ul>
 */
class I18nAutoConfigurationTest {

    private final ApplicationContextRunner coreRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class));

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class, I18nWebAutoConfiguration.class));

    @Test
    @DisplayName("코어 enabled=true → MessageResolver/ErrorMessageResolver 등록")
    void registersCoreWhenEnabled() {
        coreRunner.withPropertyValues("framework.i18n.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MessageResolver.class);
            assertThat(context).hasSingleBean(ErrorMessageResolver.class);
        });
    }

    @Test
    @DisplayName("코어 기본(비활성) → i18n 빈 없음")
    void backsOffWhenDisabled() {
        coreRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MessageResolver.class);
            assertThat(context).doesNotHaveBean(ErrorMessageResolver.class);
        });
    }

    @Test
    @DisplayName("웹(서블릿) enabled=true → LocaleResolver + I18nExceptionAdvice 등록")
    void registersWebWhenEnabledInServlet() {
        webRunner.withPropertyValues("framework.i18n.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LocaleResolver.class);
            assertThat(context).hasSingleBean(I18nExceptionAdvice.class);
        });
    }
}
