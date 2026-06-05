package com.company.framework.webauthn.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

/**
 * WebAuthn rpId/origin 정합 안전장치 (jwt-secret/session-store 가드와 동일 패턴). 멀티서비스에서 서비스마다 이
 * 가드가 돌아 rpId/origin <b>일원화 정책</b> 위반을 기동 시 잡아낸다(한 서비스만 잘못 설정해도 그 파드가 멈춤/경고).
 *
 * <p>WebAuthn 명세상 자격증명은 rpId(등록 가능 도메인)에 바인딩되고, ceremony 의 origin host 는 rpId 와 같거나 그
 * <b>하위 도메인</b>이어야 한다(아니면 브라우저/RP 가 ceremony 거부). 또 SecureContext(HTTPS) 에서만 동작한다
 * (localhost 예외). 멀티서비스 정합을 위해 전 서비스가 <b>같은 rp-id</b> 와 <b>같은 allowed-origins</b> 를 공유해야
 * 한 사용자의 패스키가 서비스 간 일관되게 동작한다(상세: docs/guide/WEBAUTHN_RPID_ORIGIN_POLICY.md).
 *
 * <p>prod 프로파일에서 아래 중 하나면 <b>부팅 실패</b>로 운영 사고를 차단한다:
 * <ul>
 *   <li>rp-id 가 비었거나 {@code localhost}</li>
 *   <li>allowed-origins 가 비어 있음(멀티서비스에서 origin 추론은 부정확 — 명시 필요)</li>
 *   <li>origin 형식 오류 / host 없음</li>
 *   <li>origin 이 https 가 아님(localhost 예외)</li>
 *   <li>origin host 가 rp-id 의 등록가능 도메인에 속하지 않음(rpId 와 무관한 호스트 — ceremony 거부 사유)</li>
 * </ul>
 *
 * <p>비-prod(local/dev)에서는 동일 조건이면 경고 배너만 남긴다(개발 편의 유지).
 */
public class WebAuthnRpSafetyGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(WebAuthnRpSafetyGuard.class);

    private final WebAuthnProperties props;
    private final Environment env;

    public WebAuthnRpSafetyGuard(WebAuthnProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        boolean prod = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

        String problem = diagnose(props.getRpId(), props.getAllowedOrigins(), prod);
        if (problem == null) {
            return; // 정합
        }

        if (prod) {
            throw new IllegalStateException("[보안 차단] prod 프로파일에서 WebAuthn rpId/origin 설정이 정합하지 않습니다 — "
                    + problem + " 전 서비스가 같은 framework.webauthn.rp-id 와 allowed-origins 를 공유하도록 일원화하세요"
                    + "(docs/guide/WEBAUTHN_RPID_ORIGIN_POLICY.md).");
        }
        log.warn(
                "\n" + "============================================================\n"
                        + " ★★★  WebAuthn rpId/origin 경고 — {}  ★★★\n"
                        + "   멀티서비스 배포 전 rp-id/allowed-origins 를 일원화하세요.\n"
                        + "   (현재 설정은 개발용으로만 안전합니다.)\n"
                        + "============================================================",
                problem);
    }

    /** 문제가 있으면 사유 문자열, 없으면 null. (가시성: 단위테스트) */
    static String diagnose(String rpId, List<String> origins, boolean prod) {
        if (rpId == null || rpId.isBlank()) {
            return "rp-id 가 비어 있습니다.";
        }
        boolean rpLocalhost = rpId.equalsIgnoreCase("localhost");
        if (prod && rpLocalhost) {
            return "prod 에서 rp-id 가 localhost 입니다(운영 등록가능 도메인으로 설정 필요).";
        }
        if (origins == null || origins.isEmpty()) {
            // 비-prod 단일 서비스는 DSL 기본 추론으로 동작 가능 → 허용. prod·멀티서비스는 명시 강제.
            return prod ? "prod 에서 allowed-origins 가 비어 있습니다(멀티서비스 origin 명시 필요)." : null;
        }
        for (String raw : origins) {
            if (raw == null || raw.isBlank()) {
                return "allowed-origin 에 빈 값이 있습니다.";
            }
            URI uri;
            try {
                uri = URI.create(raw.trim());
            } catch (IllegalArgumentException ex) {
                return "allowed-origin 형식 오류: " + raw;
            }
            String host = uri.getHost();
            String scheme = uri.getScheme();
            if (host == null || scheme == null) {
                return "allowed-origin 에 scheme/host 가 없습니다: " + raw + " (scheme://host[:port] 형식 필요).";
            }
            boolean localhost = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1");
            if (prod && !"https".equalsIgnoreCase(scheme) && !localhost) {
                return "prod 에서 origin 이 https 가 아닙니다: " + raw + " (WebAuthn 은 SecureContext 필수).";
            }
            if (!localhost && !hostMatchesRpId(host, rpId)) {
                return "origin host(" + host + ")가 rp-id(" + rpId + ")의 등록가능 도메인에 속하지 않습니다: " + raw
                        + " — ceremony 거부 사유(host 는 rp-id 와 같거나 하위 도메인이어야 함).";
            }
        }
        return null;
    }

    /** host 가 rpId 와 같거나 그 하위 도메인인지(WebAuthn effective domain 규칙의 보수적 근사). */
    private static boolean hostMatchesRpId(String host, String rpId) {
        String h = host.toLowerCase(Locale.ROOT);
        String r = rpId.toLowerCase(Locale.ROOT);
        return h.equals(r) || h.endsWith("." + r);
    }
}
