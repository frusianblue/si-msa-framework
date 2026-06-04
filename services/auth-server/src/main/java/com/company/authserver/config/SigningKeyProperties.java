package com.company.authserver.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 서명키 회전/암호화 설정. 기존 {@code auth-server.*} 네임스페이스 하위로 둔다({@code auth-server.signing-key.*}).
 *
 * <pre>{@code
 * auth-server:
 *   signing-key:
 *     rotation:
 *       enabled: ${SIGNING_KEY_ROTATION_ENABLED:false}   # 회전 스케줄러(기본 off — 의도적 on)
 *       cron: ${SIGNING_KEY_ROTATION_CRON:0 0 4 1 * *}    # 매월 1일 04:00
 *       retire-grace: ${SIGNING_KEY_RETIRE_GRACE:14d}     # RETIRED 보존(폐기 후) — ≥ access수명+캐시TTL+여유
 *       min-interval: ${SIGNING_KEY_ROTATION_MIN_INTERVAL:1h}  # 멱등 가드: 최근 ACTIVE 이내면 스킵
 *     encryption:
 *       enabled: ${SIGNING_KEY_ENCRYPTION_ENABLED:true}   # 개인키 컬럼 암호화(쓰기) — 기본 on(안전)
 * }</pre>
 *
 * <p>토글 컨벤션: <b>회전=기본 off</b>(운영에서 의도적 on), <b>암호화=기본 on</b>(개인키 보호는 안전쪽). 읽기 복호화는
 * 토글과 무관하게 항상 마커 인지({@code AesSigningKeyCipher#reveal})라 혼재/롤백 안전.
 */
@ConfigurationProperties(prefix = "auth-server.signing-key")
public record SigningKeyProperties(
        @DefaultValue Rotation rotation, @DefaultValue Encryption encryption) {

    public record Rotation(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("0 0 4 1 * *") String cron,
            @DefaultValue("14d") Duration retireGrace,
            @DefaultValue("1h") Duration minInterval) {}

    public record Encryption(@DefaultValue("true") boolean enabled) {}
}
