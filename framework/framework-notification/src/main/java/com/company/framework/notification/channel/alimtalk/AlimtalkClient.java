package com.company.framework.notification.channel.alimtalk;

import java.util.Map;

/**
 * 카카오 알림톡 발송 벤더 어댑터 SPI(템플릿 기반). 프레임워크는 로깅용 기본 구현({@link LoggingAlimtalkClient})만 제공하며,
 * 실제 발송은 서비스가 비즈메시지 사업자(예: NHN Cloud, 카카오 비즈니스 파트너)용 {@code AlimtalkClient} 빈으로 교체한다.
 *
 * <p>발송 실패는 런타임 예외로 던지면 된다({@code AlimtalkNotificationSender} 가 잡아 결과로 변환).
 */
public interface AlimtalkClient {

    /**
     * @param to 수신 번호
     * @param templateCode 사전 등록된 알림톡 템플릿 코드
     * @param content 본문(템플릿이 본문을 결정하면 널 가능)
     * @param variables 템플릿 치환 변수
     * @param senderKey 발신 프로필 키(널이면 벤더/채널 기본값)
     */
    void send(String to, String templateCode, String content, Map<String, String> variables, String senderKey);
}
