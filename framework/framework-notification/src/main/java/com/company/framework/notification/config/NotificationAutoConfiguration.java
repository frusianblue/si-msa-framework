package com.company.framework.notification.config;

import com.company.framework.notification.NotificationSender;
import com.company.framework.notification.NotificationService;
import com.company.framework.notification.channel.MailNotificationSender;
import com.company.framework.notification.channel.alimtalk.AlimtalkClient;
import com.company.framework.notification.channel.alimtalk.AlimtalkNotificationSender;
import com.company.framework.notification.channel.alimtalk.LoggingAlimtalkClient;
import com.company.framework.notification.channel.sms.LoggingSmsClient;
import com.company.framework.notification.channel.sms.SmsClient;
import com.company.framework.notification.channel.sms.SmsNotificationSender;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 알림 모듈 오토컨피그.
 *
 * <ul>
 *   <li>master: {@code framework.notification.enabled=true} → {@link NotificationService}(등록된 sender 들을 채널별 라우팅).
 *   <li>mail: {@code channels.mail.enabled=true} + {@link JavaMailSender} 빈(=spring.mail.host) → {@link MailNotificationSender}.
 *   <li>sms: {@code channels.sms.enabled=true} → {@link SmsClient}(기본 {@link LoggingSmsClient}) + {@link SmsNotificationSender}.
 *   <li>alimtalk: {@code channels.alimtalk.enabled=true} → {@link AlimtalkClient}(기본 {@link LoggingAlimtalkClient}) + sender.
 * </ul>
 *
 * <p>SMS/알림톡 기본 구현은 로깅(실발송 X)이며, 서비스가 동일 타입의 벤더 빈을 등록하면 {@code @ConditionalOnMissingBean}
 * 으로 자동 교체된다. mail 은 Boot 의 메일 자동구성 이후 평가되도록 {@code afterName} 으로 순서를 잡는다.
 */
@AutoConfiguration(afterName = "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration")
@ConditionalOnProperty(prefix = "framework.notification", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NotificationService notificationService(List<NotificationSender> senders) {
        return new NotificationService(senders);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JavaMailSender.class)
    @ConditionalOnProperty(prefix = "framework.notification.channels.mail", name = "enabled", havingValue = "true")
    static class MailChannelConfiguration {

        @Bean
        @ConditionalOnBean(JavaMailSender.class)
        @ConditionalOnMissingBean(MailNotificationSender.class)
        public MailNotificationSender mailNotificationSender(
                JavaMailSender mailSender, NotificationProperties properties) {
            return new MailNotificationSender(
                    mailSender, properties.getChannels().getMail().getFrom());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "framework.notification.channels.sms", name = "enabled", havingValue = "true")
    static class SmsChannelConfiguration {

        @Bean
        @ConditionalOnMissingBean(SmsClient.class)
        public SmsClient loggingSmsClient() {
            return new LoggingSmsClient();
        }

        @Bean
        @ConditionalOnMissingBean(SmsNotificationSender.class)
        public SmsNotificationSender smsNotificationSender(SmsClient smsClient, NotificationProperties properties) {
            return new SmsNotificationSender(
                    smsClient, properties.getChannels().getSms().getFrom());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "framework.notification.channels.alimtalk", name = "enabled", havingValue = "true")
    static class AlimtalkChannelConfiguration {

        @Bean
        @ConditionalOnMissingBean(AlimtalkClient.class)
        public AlimtalkClient loggingAlimtalkClient() {
            return new LoggingAlimtalkClient();
        }

        @Bean
        @ConditionalOnMissingBean(AlimtalkNotificationSender.class)
        public AlimtalkNotificationSender alimtalkNotificationSender(
                AlimtalkClient alimtalkClient, NotificationProperties properties) {
            return new AlimtalkNotificationSender(
                    alimtalkClient, properties.getChannels().getAlimtalk().getSenderKey());
        }
    }
}
