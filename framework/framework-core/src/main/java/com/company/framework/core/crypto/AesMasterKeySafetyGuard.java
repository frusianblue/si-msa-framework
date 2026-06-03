package com.company.framework.core.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

/**
 * AES 마스터 키 운영 안전장치 (JWT/dev-auth/password 가드와 동일 패턴).
 *
 * <p>설정값 암호화({@code ENC(...)})의 신뢰 기반이 곧 이 마스터 키이므로, prod 프로파일에서 아래 중 하나라도
 * 해당하면 <b>부팅을 실패</b>시켜 사고를 차단한다.
 *
 * <ul>
 *   <li>키가 비어 있음</li>
 *   <li>레포 기본 placeholder 키({@code change-me-please-set-framework-crypto-aes-secret}) 그대로(또는
 *       "change-me"/"change-this" 류 미교체 흔적)</li>
 *   <li>엔트로피가 너무 낮음(16바이트 미만)</li>
 * </ul>
 *
 * <p>비-prod(local/dev)에서는 동일 조건이면 경고 배너만 남긴다(개발 편의 유지).
 *
 * <p>마스터 키는 EPP 복호화의 전제이고 빈 시점(컨텍스트 init)엔 이미 평문으로 해석돼 있으므로, 본 가드는
 * 그 평문 키의 강도를 한 겹 더 검증한다. (운영 yaml 은 {@code aes-secret: ${AES_SECRET}} 로 주입을 강제 →
 * 미주입 시엔 placeholder 해석 실패로 더 먼저 멈춘다.)
 */
public class AesMasterKeySafetyGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(AesMasterKeySafetyGuard.class);

    /** {@link CryptoProperties} 의 기본 placeholder 키. 운영에서 이 값이면 즉시 차단. */
    static final String DEFAULT_SECRET = "change-me-please-set-framework-crypto-aes-secret";

    /** 최소 권장 키 길이(바이트). SHA-256 으로 256bit 키를 파생하지만 입력 엔트로피가 낮으면 의미 없음. */
    static final int MIN_SECRET_BYTES = 16;

    private final CryptoProperties props;
    private final Environment env;

    public AesMasterKeySafetyGuard(CryptoProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        String secret = props.getAesSecret();
        boolean prod = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

        String problem = diagnose(secret);
        if (problem == null) {
            return; // 정상
        }

        if (prod) {
            throw new IllegalStateException("[보안 차단] prod 프로파일에서 AES 마스터 키가 안전하지 않습니다 — " + problem
                    + " 환경변수 AES_SECRET 에 16바이트 이상의 강한 마스터 키를 주입하세요.");
        }
        log.warn(
                "\n" + "============================================================\n"
                        + " ★★★  AES MASTER KEY 경고 — {}  ★★★\n"
                        + "   운영 배포 전 AES_SECRET 을 16바이트 이상의 강한 키로 교체하세요.\n"
                        + "   (이 키가 ENC(...) 설정 복호화의 신뢰 기반입니다.)\n"
                        + "============================================================",
                problem);
    }

    /** 문제가 있으면 사유 문자열, 없으면 null. */
    private String diagnose(String secret) {
        if (secret == null || secret.isBlank()) {
            return "마스터 키가 비어 있습니다.";
        }
        String normalized = secret.toLowerCase(Locale.ROOT);
        if (secret.equals(DEFAULT_SECRET) || normalized.contains("change-this") || normalized.contains("change-me")) {
            return "기본 placeholder 마스터 키가 교체되지 않았습니다.";
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_SECRET_BYTES) {
            return "마스터 키가 너무 짧습니다(" + bytes + "바이트 < " + MIN_SECRET_BYTES + "바이트).";
        }
        return null;
    }
}
