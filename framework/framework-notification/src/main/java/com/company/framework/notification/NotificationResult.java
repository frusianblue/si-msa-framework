package com.company.framework.notification;

/**
 * 알림 발송 결과. 알림은 보통 best-effort 라, 발송 실패도 예외 대신 결과로 돌려준다(호출부가 정책 결정).
 *
 * @param channel 채널
 * @param success 성공 여부
 * @param detail 메시지 ID 또는 실패 사유
 */
public record NotificationResult(ChannelType channel, boolean success, String detail) {

    public static NotificationResult success(ChannelType channel, String detail) {
        return new NotificationResult(channel, true, detail);
    }

    public static NotificationResult failure(ChannelType channel, String detail) {
        return new NotificationResult(channel, false, detail);
    }
}
