package com.company.framework.notification;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 알림 발송 진입점(파사드). 등록된 {@link NotificationSender} 들을 채널별로 모아, 요청 채널에 맞는 sender 로 위임한다.
 *
 * <p>업무 코드는 이 빈만 주입해서 {@code notificationService.send(NotificationRequest.mail(...).build())} 처럼 쓴다.
 * 해당 채널 sender 가 없으면(=채널 미활성) {@link NotificationException} 을 던진다.
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Map<ChannelType, NotificationSender> senders = new EnumMap<>(ChannelType.class);

    public NotificationService(List<NotificationSender> senderBeans) {
        for (NotificationSender sender : senderBeans) {
            NotificationSender prev = senders.putIfAbsent(sender.channelType(), sender);
            if (prev != null) {
                log.warn(
                        "[notification] 채널 {} sender 가 둘 이상 — {} 유지, {} 무시",
                        sender.channelType(),
                        prev.getClass().getSimpleName(),
                        sender.getClass().getSimpleName());
            }
        }
        log.info("[notification] 활성 채널: {}", senders.keySet());
    }

    public NotificationResult send(NotificationRequest request) {
        NotificationSender sender = senders.get(request.channel());
        if (sender == null) {
            throw new NotificationException("발송 가능한 채널이 아닙니다(미활성/미구성): " + request.channel());
        }
        return sender.send(request);
    }

    /** 해당 채널이 활성(=sender 존재)인지. */
    public boolean supports(ChannelType channel) {
        return senders.containsKey(channel);
    }
}
