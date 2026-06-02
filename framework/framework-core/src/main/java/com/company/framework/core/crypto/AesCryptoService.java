package com.company.framework.core.crypto;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256 양방향 암호화. 동일 키(시크릿의 SHA-256)로 두 가지 용도를 제공한다.
 *  - 문자열: AES-GCM, 출력 Base64( IV(12B) || ciphertext+tag ) — 개인정보 컬럼 암호화 저장용.
 *  - 스트림: AES-CBC(PKCS5), 출력 = IV(16B) || ciphertext — 파일 본문 at-rest 암호화용
 *    (대용량 스트리밍 가능. 기밀성 제공; 무결성 태그는 없음 — 파일 메타는 DB로 별도 관리).
 */
public class AesCryptoService {

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private static final String STREAM_TRANSFORM = "AES/CBC/PKCS5Padding";
    private static final int BLOCK = 16; // AES block / CBC IV length

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesCryptoService(String secret) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("AES 키 초기화 실패", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] dec = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("복호화 실패", e);
        }
    }

    /**
     * 평문 입력 스트림을 암호화 스트림으로 감싼다. 출력 = IV(16B) || AES-CBC ciphertext.
     * 대용량 파일을 메모리에 올리지 않고 스트리밍 암호화한다.
     */
    public InputStream encryptingInputStream(InputStream plain) {
        try {
            byte[] iv = new byte[BLOCK];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(STREAM_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            return new SequenceInputStream(new ByteArrayInputStream(iv), new CipherInputStream(plain, cipher));
        } catch (Exception e) {
            throw new IllegalStateException("스트림 암호화 실패", e);
        }
    }

    /**
     * {@link #encryptingInputStream(InputStream)} 로 저장된 스트림을 복호화한다.
     * 선두 16바이트를 IV 로 읽고 나머지를 AES-CBC 복호화한다.
     */
    public InputStream decryptingInputStream(InputStream encrypted) {
        try {
            byte[] iv = encrypted.readNBytes(BLOCK);
            if (iv.length != BLOCK) {
                throw new IllegalStateException("암호화 헤더(IV)가 손상되었습니다.");
            }
            Cipher cipher = Cipher.getInstance(STREAM_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return new CipherInputStream(encrypted, cipher);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("스트림 복호화 실패", e);
        }
    }

    /**
     * 평문 길이에 대응하는 암호문 총 길이(IV + CBC/PKCS5 패딩 포함)를 계산한다.
     * S3 등 content-length 를 요구하는 백엔드에 정확한 크기를 넘기기 위해 사용.
     */
    public static long cbcEncryptedLength(long plainLength) {
        long padded = ((plainLength / BLOCK) + 1) * BLOCK; // PKCS5: 항상 1~16B 패딩
        return BLOCK + padded; // 선두 IV(16B) 포함
    }
}
