package com.company.framework.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

/**
 * JWT 시크릿 운영 안전장치 (dev-auth/password 가드와 동일 패턴).
 *
 * <p>prod 프로파일에서 아래 중 하나라도 해당하면 <b>부팅을 실패</b>시켜 운영 사고를 차단한다.
 * <ul>
 *   <li>시크릿이 비어 있음</li>
 *   <li>레포 기본 placeholder 시크릿 그대로(또는 "change-this"/"change-me" 류 미교체 흔적)</li>
 *   <li>HS256 최소 키 길이(32바이트=256비트) 미만</li>
 * </ul>
 *
 * <p>비-prod(local/dev)에서는 동일 조건이면 경고 배너만 남긴다(개발 편의 유지).
 *
 * <p>prod 프로파일 자체는 {@code application-prod.yml} 에서 {@code secret: ${JWT_SECRET}} 로
 * 기본값 없이 주입을 강제하므로, 미주입 시엔 플레이스홀더 해석 실패로 더 먼저 기동이 멈춘다.
 * 본 가드는 "주입은 됐지만 값이 기본/약함"인 경우까지 한 겹 더 막는다.
 */
public class JwtSecretSafetyGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretSafetyGuard.class);

    /** 레포 전역에서 쓰이는 기본 placeholder 시크릿. 운영에서 이 값이면 즉시 차단. */
    static final String DEFAULT_SECRET = "change-this-to-a-very-long-secret-key-at-least-32-bytes!!";

    /** HS256 최소 키 길이(바이트). */
    static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties props;
    private final Environment env;

    public JwtSecretSafetyGuard(JwtProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        String secret = props.secret();
        boolean prod = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

        String problem = diagnose(secret);
        if (problem == null) {
            return; // 정상
        }

        if (prod) {
            throw new IllegalStateException("[보안 차단] prod 프로파일에서 JWT 시크릿이 안전하지 않습니다 — " + problem
                    + " 환경변수 JWT_SECRET 에 32바이트 이상의 강한 비밀키를 주입하세요.");
        }
        log.warn(
                "\n" + "============================================================\n"
                        + " ★★★  JWT SECRET 경고 — {}  ★★★\n"
                        + "   운영 배포 전 JWT_SECRET 을 32바이트 이상의 강한 키로 교체하세요.\n"
                        + "   (현재 값은 개발용으로만 허용됩니다.)\n"
                        + "============================================================",
                problem);
    }

    /** 문제가 있으면 사유 문자열, 없으면 null. */
    private String diagnose(String secret) {
        if (secret == null || secret.isBlank()) {
            return "시크릿이 비어 있습니다.";
        }
        String normalized = secret.toLowerCase(Locale.ROOT);
        if (secret.equals(DEFAULT_SECRET) || normalized.contains("change-this") || normalized.contains("change-me")) {
            return "기본 placeholder 시크릿이 교체되지 않았습니다.";
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_SECRET_BYTES) {
            return "시크릿이 너무 짧습니다(" + bytes + "바이트 < " + MIN_SECRET_BYTES + "바이트).";
        }
        return null;
    }
}
