package com.company.framework.notification;

/**
 * 알림 채널 종류. 채널별 {@link NotificationSender} 가 하나씩 매핑된다.
 */
public enum ChannelType {
    /** 이메일(SMTP, Spring JavaMailSender). */
    MAIL,
    /** 문자(SMS/LMS) — 벤더 {@code SmsClient} 어댑터로 발송. */
    SMS,
    /** 카카오 알림톡 — 벤더 {@code AlimtalkClient} 어댑터로 발송(템플릿 기반). */
    ALIMTALK
}
