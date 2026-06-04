package com.company.framework.qr;

/**
 * QR 생성 SPI(교체 가능). 기본 구현 {@link ZxingQrGenerator}(ZXing 인코딩 + JDK ImageIO 렌더링).
 * 앱이 같은 타입 빈을 직접 정의하면 그쪽이 우선({@code @ConditionalOnMissingBean}).
 *
 * <p>모든 메서드는 실패 시 core {@code BusinessException}({@link QrErrorCode})을 던진다.
 *
 * <p>전형적 용도: MFA {@code otpauth://} URI(인증기 앱 등록), 결제/전자문서 검증 URL, 입장권 등.
 * (MFA 모듈은 의도적으로 QR 을 생성하지 않고 URI 만 돌려준다 — 서버측 QR 이 필요하면 이 모듈을 옵트인.)
 */
public interface QrGenerator {

    /**
     * 내용을 기본 스펙({@link QrSpec#defaults()})으로 QR PNG 바이트로 만든다.
     *
     * @param content 인코딩할 문자열/URI
     * @return PNG 바이트
     */
    byte[] toPng(String content);

    /**
     * 내용을 주어진 스펙대로 QR PNG 바이트로 만든다.
     *
     * @param content 인코딩할 문자열/URI
     * @param spec 렌더링 명세(크기/여백/ECC/문자셋/색)
     * @return PNG 바이트
     */
    byte[] generate(String content, QrSpec spec);
}
