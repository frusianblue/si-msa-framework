package com.company.framework.mfa.otp;

/**
 * 발송형 OTP 코드 전달 채널(SPI). 프로젝트가 구현해 등록한다(SMS/메일/알림톡 등). 빈이 없으면 OTP 방식은
 * 비활성되고 TOTP 만 사용 가능하다.
 *
 * <p>framework-notification 을 쓰는 프로젝트라면 그 발송 서비스를 호출하는 얇은 구현을 등록하면 된다(프레임워크는
 * 특정 채널에 강결합하지 않기 위해 기본 구현을 제공하지 않는다).
 */
public interface OtpSender {

    /**
     * 사용자에게 OTP 코드를 발송한다. 목적지(전화/이메일)는 구현이 userId 로 해석한다.
     *
     * @param userId 수신 사용자
     * @param code 발송할 일회성 코드(평문)
     */
    void send(String userId, String code);
}
