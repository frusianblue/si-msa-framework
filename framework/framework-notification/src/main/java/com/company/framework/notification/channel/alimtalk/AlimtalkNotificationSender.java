package com.company.framework.notification.channel.alimtalk;

import com.company.framework.notification.ChannelType;
import com.company.framework.notification.NotificationRequest;
import com.company.framework.notification.NotificationResult;
import com.company.framework.notification.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 알림톡 채널 발송기: {@link NotificationRequest}(templateCode/variables)를 {@link AlimtalkClient} 호출로 변환. */
public class AlimtalkNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(AlimtalkNotificationSender.class);

    private final AlimtalkClient alimtalkClient;
    private final String defaultSenderKey;

    public AlimtalkNotificationSender(AlimtalkClient alimtalkClient, String defaultSenderKey) {
        this.alimtalkClient = alimtalkClient;
        this.defaultSenderKey = defaultSenderKey;
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.ALIMTALK;
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        if (request.templateCode() == null || request.templateCode().isBlank()) {
            return NotificationResult.failure(ChannelType.ALIMTALK, "templateCode 누락");
        }
        try {
            alimtalkClient.send(
                    request.to(), request.templateCode(), request.content(), request.variables(), defaultSenderKey);
            return NotificationResult.success(ChannelType.ALIMTALK, "sent");
        } catch (RuntimeException e) {
            log.warn(
                    "[notification] 알림톡 발송 실패 to={} template={} : {}",
                    request.to(),
                    request.templateCode(),
                    e.getMessage());
            return NotificationResult.failure(ChannelType.ALIMTALK, e.getMessage());
        }
    }
}
