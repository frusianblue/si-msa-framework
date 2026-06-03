package com.company.framework.core.crypto;

/**
 * 개발자가 yaml 에 넣을 {@code ENC(...)} 토큰을 만드는 CLI(암호화 엔드포인트 노출 금지 → 빈/HTTP 대신 CLI).
 *
 * <p>사용:
 *
 * <pre>{@code
 * AES_SECRET=프로젝트마스터키 \
 *   java -cp <classpath> com.company.framework.core.crypto.CryptoCli encrypt 'sipass'
 * # → ENC(Qk9k...) 를 application*.yml 에 붙여넣기
 * }</pre>
 *
 * <p>또는 루트 {@code build.gradle} 에 {@code JavaExec} 태스크({@code encryptSecret})로 노출. GCM 은 IV 가
 * 매번 랜덤이라 같은 평문도 매번 다른 암호문이 나온다(정상).
 *
 * <p>마스터 키는 {@code AES_SECRET} 환경변수로만 받는다(인자/로그에 키 노출 방지).
 */
public final class CryptoCli {

    private CryptoCli() {}

    public static void main(String[] args) {
        if (args.length != 2 || !"encrypt".equals(args[0])) {
            System.err.println("usage: CryptoCli encrypt <plaintext>");
            System.err.println("  (마스터 키는 환경변수 AES_SECRET 로 주입)");
            System.exit(2);
            return;
        }
        String key = System.getenv("AES_SECRET");
        if (key == null || key.isBlank()) {
            System.err.println("환경변수 AES_SECRET 가 필요합니다(마스터 키).");
            System.exit(2);
            return;
        }
        AesCryptoService aes = new AesCryptoService(key);
        System.out.println("ENC(" + aes.encrypt(args[1]) + ")");
    }
}
