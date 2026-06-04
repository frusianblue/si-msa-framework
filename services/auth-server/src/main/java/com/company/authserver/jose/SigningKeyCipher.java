package com.company.authserver.jose;

/**
 * 서명키(개인키 포함 JWK JSON) 저장 보호 추상. 1차 구현은 {@link AesSigningKeyCipher}(AES-GCM 컬럼 암호화, 새 외부 의존성 0).
 * KMS/Vault 백엔드는 이 인터페이스만 구현해 교체한다(결정 ①: AES 시작 · KMS 후속).
 *
 * <p><b>읽기는 항상 마커 인지</b>: {@link #reveal} 은 평문/암호문을 마커로 분기해 둘 다 처리한다 → 평문(데모/롤백) ↔ 암호문 혼재여도 안전.
 * <b>쓰기 토글</b>: {@code encryption.enabled=false} 면 {@link #protect} 가 평문을 그대로 둔다(읽기는 여전히 양쪽 처리).
 */
public interface SigningKeyCipher {

    /** 저장 직전 호출 — 개인키 포함 JWK JSON 을 저장형(암호화 등)으로 변환. */
    String protect(String jwkJson);

    /** 읽기 직후 호출 — 저장형에서 평문 JWK JSON 복원(마커 없으면 평문으로 간주해 그대로 반환). */
    String reveal(String stored);
}
