package com.company.framework.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.notification.NotificationService;
import com.company.framework.notification.channel.sms.LoggingSmsClient;
import com.company.framework.notification.channel.sms.SmsClient;
import com.company.framework.notification.channel.sms.SmsNotificationSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 알림 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@code framework.notification.enabled=true} → {@link NotificationService} 등록(등록된 sender 0개여도 빈 List 주입).
 *   <li>+ {@code channels.sms.enabled=true} → 기본 {@link LoggingSmsClient} + {@link SmsNotificationSender} 등록.
 *   <li>기본(미설정/false) → 어떤 알림 빈도 만들지 않음.
 * </ul>
 *
 * <p>메일 채널은 {@code JavaMailSender} 빈(=spring.mail.host)을 {@code @ConditionalOnBean} 으로 요구하므로
 * 이 컨텍스트(메일 서버 미설정)에선 생성되지 않는다. {@code spring-boot-starter-mail} 은 {@code api} 의존이라
 * {@code JavaMailSender} 클래스 자체는 존재 → introspection 무방.
 */
class NotificationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NotificationAutoConfiguration.class));

    @Test
    @DisplayName("enabled=true → NotificationService 등록(sender 미등록 시 빈 라우터)")
    void registersServiceWhenEnabled() {
        runner.withPropertyValues("framework.notification.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationService.class);
        });
    }

    @Test
    @DisplayName("enabled=true + sms 채널 on → 기본 LoggingSmsClient + SmsNotificationSender 등록")
    void registersSmsChannel() {
        runner.withPropertyValues(
                        "framework.notification.enabled=true", "framework.notification.channels.sms.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SmsClient.class);
                    assertThat(context.getBean(SmsClient.class)).isInstanceOf(LoggingSmsClient.class);
                    assertThat(context).hasSingleBean(SmsNotificationSender.class);
                });
    }

    @Test
    @DisplayName("기본(비활성) → 어떤 알림 빈도 만들지 않음")
    void backsOffWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(NotificationService.class);
            assertThat(context).doesNotHaveBean(SmsNotificationSender.class);
        });
    }
}
