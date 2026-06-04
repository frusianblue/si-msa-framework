package com.company.authserver.jose;

import java.time.Instant;

/**
 * DB 에 저장되는 서명키 한 건(다중 파드 공유 + 회전 오버랩용).
 *
 * <p>{@code jwkJson} 은 Nimbus {@code RSAKey.toJSONString()} 형식(개인키 파라미터 포함). ⚠️ 개인키 원문이므로 운영에서는 <b>반드시 저장 시점
 * 암호화</b>해야 한다(컬럼 암호화/KMS/Vault). 회전 모듈은 {@link SigningKeyCipher} 로 보호한다(마커 {@code enc:} 접두).
 *
 * @param kid 키 식별자(JWS header kid · JWKS 의 kid). UUID.
 * @param jwkJson Nimbus RSAKey JSON(개인키 포함). 저장형(암호화 시 {@code enc:} 마커 접두).
 * @param status ACTIVE(현재 서명용 후보) | RETIRED(검증만 — 회전 오버랩 기간).
 * @param createdAt 생성 시각(가장 최신 ACTIVE 가 서명에 사용된다).
 * @param retiredAt RETIRE 된 시각(ACTIVE 면 null). ⚠️ grace 정리 기준은 <b>이 값</b>이다(생성 시각 아님).
 *     생성 시각 기준으로 정리하면 grace &lt; 회전주기일 때 직전 키가 RETIRE 즉시 삭제돼 오버랩이 깨진다.
 */
public record SigningKey(String kid, String jwkJson, String status, Instant createdAt, Instant retiredAt) {

    public static final String ACTIVE = "ACTIVE";
    public static final String RETIRED = "RETIRED";

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    /** 새 ACTIVE 키(아직 RETIRE 전이라 retiredAt=null) 생성 편의. */
    public static SigningKey active(String kid, String jwkJson, Instant createdAt) {
        return new SigningKey(kid, jwkJson, ACTIVE, createdAt, null);
    }
}
