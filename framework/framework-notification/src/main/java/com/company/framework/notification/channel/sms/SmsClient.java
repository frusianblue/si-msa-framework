package com.company.framework.notification.channel.sms;

/**
 * SMS/LMS 발송 벤더 어댑터 SPI. 프레임워크는 로깅용 기본 구현({@link LoggingSmsClient})만 제공하므로,
 * 실제 발송은 서비스가 벤더(예: NHN Cloud, 네이버클라우드, CoolSMS 등)용 {@code SmsClient} 빈을 등록해 교체한다.
 *
 * <p>발송 실패는 런타임 예외로 던지면 된다({@code SmsNotificationSender} 가 잡아 결과로 변환).
 */
public interface SmsClient {

    /**
     * @param to 수신 번호
     * @param text 본문
     * @param from 발신 번호(널이면 벤더/채널 기본값)
     */
    void send(String to, String text, String from);
}
