package com.company.authserver.jose;

import java.time.Instant;

/**
 * DB 에 저장되는 서명키 한 건(다중 파드 공유 + 회전 오버랩용).
 *
 * <p>{@code jwkJson} 은 Nimbus {@code RSAKey.toJSONString()} 형식(개인키 파라미터 포함). ⚠️ 개인키 원문이므로 운영에서는 <b>반드시 저장 시점
 * 암호화</b>해야 한다(컬럼 암호화/KMS/Vault). 본 골격은 평문 저장이며 TODO 로 남긴다.
 *
 * @param kid 키 식별자(JWS header kid · JWKS 의 kid). UUID.
 * @param jwkJson Nimbus RSAKey JSON(개인키 포함).
 * @param status ACTIVE(현재 서명용 후보) | RETIRED(검증만 — 회전 오버랩 기간).
 * @param createdAt 생성 시각(가장 최신 ACTIVE 가 서명에 사용된다).
 */
public record SigningKey(String kid, String jwkJson, String status, Instant createdAt) {

    public static final String ACTIVE = "ACTIVE";
    public static final String RETIRED = "RETIRED";

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
