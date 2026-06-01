package com.company.framework.notification;

/**
 * 채널별 발송 구현 SPI. 채널 1종당 빈 1개를 등록하면 {@link NotificationService} 가 자동으로 라우팅한다.
 * 프레임워크가 mail/sms/alimtalk 기본 구현을 제공하며, 서비스는 동일 채널의 빈을 정의해 교체할 수 있다.
 */
public interface NotificationSender {

    /** 이 sender 가 담당하는 채널. */
    ChannelType channelType();

    /** 발송. 벤더 오류는 예외 대신 {@link NotificationResult#failure} 로 돌려주는 것을 권장. */
    NotificationResult send(NotificationRequest request);
}
