package com.company.framework.notification;

/**
 * 알림 구성/디스패치 오류(예: 해당 채널 sender 미구성). 발송 실패(벤더 오류 등)는 {@link NotificationResult} 로 돌려주고,
 * 이 예외는 "보낼 수단 자체가 없는" 프로그래밍/구성 오류에만 사용한다.
 */
public class NotificationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
