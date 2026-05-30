package com.company.framework.core.crypto;

/**
 * 스프링 빈 주입이 어려운 곳(예: MyBatis TypeHandler)에서 AES 서비스를 참조하기 위한 정적 홀더.
 * CryptoAutoConfiguration 이 기동 시 1회 설정한다.
 */
public final class CryptoHolder {
    private static volatile AesCryptoService aes;
    private CryptoHolder() {}
    public static void set(AesCryptoService service) { aes = service; }
    public static AesCryptoService aes() {
        if (aes == null) throw new IllegalStateException("AES 미초기화: framework.crypto.enabled=true 확인");
        return aes;
    }
}
