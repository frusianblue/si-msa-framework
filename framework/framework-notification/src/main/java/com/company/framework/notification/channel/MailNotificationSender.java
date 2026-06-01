package com.company.framework.notification.channel;

import com.company.framework.notification.ChannelType;
import com.company.framework.notification.NotificationRequest;
import com.company.framework.notification.NotificationResult;
import com.company.framework.notification.NotificationSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * 메일 채널 발송기. Spring {@link JavaMailSender}(Boot 가 {@code spring.mail.host} 설정 시 자동구성)를 사용한다.
 * 텍스트/HTML 모두 지원({@link NotificationRequest#html()}). 발신자는 요청 override → 채널 기본값 순.
 */
public class MailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationSender.class);

    private final JavaMailSender mailSender;
    private final String defaultFrom;

    public MailNotificationSender(JavaMailSender mailSender, String defaultFrom) {
        this.mailSender = mailSender;
        this.defaultFrom = defaultFrom;
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.MAIL;
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(request.to());
            helper.setSubject(request.subject() == null ? "" : request.subject());
            helper.setText(request.content() == null ? "" : request.content(), request.html());

            String from = request.from() != null ? request.from() : defaultFrom;
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }

            mailSender.send(message);
            return NotificationResult.success(ChannelType.MAIL, "sent");
        } catch (MessagingException | MailException e) {
            log.warn("[notification] 메일 발송 실패 to={} : {}", request.to(), e.getMessage());
            return NotificationResult.failure(ChannelType.MAIL, e.getMessage());
        }
    }
}
