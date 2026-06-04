package com.company.authserver.jose;

import com.company.framework.core.crypto.AesCryptoService;

/**
 * {@link SigningKeyCipher} 의 AES-GCM 컬럼 암호화 구현. framework-core {@link AesCryptoService}(마스터키 = {@code
 * framework.crypto.aes-secret}/{@code AES_SECRET}) 재사용 — 새 외부 의존성 0.
 *
 * <p>저장형 = {@code "enc:" + Base64( IV(12) || ciphertext+tag )}. 마커 {@code enc:} 로 평문 JWK({@code {"kty":...}} 로 시작)와
 * 구분한다(평문은 절대 {@code enc:} 로 시작하지 않음).
 *
 * <ul>
 *   <li><b>쓰기</b>({@link #protect}): {@code encryptOnWrite=true} 면 마커+암호문, false 면 평문 그대로(토글 off).
 *   <li><b>읽기</b>({@link #reveal}): 마커가 있으면 복호화, 없으면 평문으로 간주(롤백/데모→운영 전환 안전).
 * </ul>
 */
public final class AesSigningKeyCipher implements SigningKeyCipher {

    /** 암호문 식별 접두. 설정값 암호화의 {@code ENC(...)} 와 의도적으로 다른 표기(혼동/EPP 오스캔 방지). */
    static final String MARKER = "enc:";

    private final AesCryptoService aes;
    private final boolean encryptOnWrite;

    public AesSigningKeyCipher(AesCryptoService aes, boolean encryptOnWrite) {
        this.aes = aes;
        this.encryptOnWrite = encryptOnWrite;
    }

    @Override
    public String protect(String jwkJson) {
        if (jwkJson == null) {
            return null;
        }
        if (!encryptOnWrite) {
            return jwkJson; // 토글 off — 평문 저장(읽기는 여전히 마커 인지)
        }
        return MARKER + aes.encrypt(jwkJson);
    }

    @Override
    public String reveal(String stored) {
        if (stored == null) {
            return null;
        }
        if (stored.startsWith(MARKER)) {
            return aes.decrypt(stored.substring(MARKER.length()));
        }
        return stored; // 마커 없음 = 평문(레거시/데모). 그대로 반환.
    }
}
