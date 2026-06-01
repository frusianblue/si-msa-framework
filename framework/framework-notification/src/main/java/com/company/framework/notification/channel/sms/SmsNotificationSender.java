package com.company.framework.notification.channel.sms;

import com.company.framework.notification.ChannelType;
import com.company.framework.notification.NotificationRequest;
import com.company.framework.notification.NotificationResult;
import com.company.framework.notification.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SMS 채널 발송기: {@link NotificationRequest} 를 {@link SmsClient} 호출로 변환. 발신번호는 요청 override → 채널 기본값. */
public class SmsNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationSender.class);

    private final SmsClient smsClient;
    private final String defaultFrom;

    public SmsNotificationSender(SmsClient smsClient, String defaultFrom) {
        this.smsClient = smsClient;
        this.defaultFrom = defaultFrom;
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.SMS;
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        String from = request.from() != null ? request.from() : defaultFrom;
        try {
            smsClient.send(request.to(), request.content(), from);
            return NotificationResult.success(ChannelType.SMS, "sent");
        } catch (RuntimeException e) {
            log.warn("[notification] SMS 발송 실패 to={} : {}", request.to(), e.getMessage());
            return NotificationResult.failure(ChannelType.SMS, e.getMessage());
        }
    }
}
