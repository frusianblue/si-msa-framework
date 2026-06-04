package com.company.framework.file.sftp.cred;

import java.security.KeyPair;
import java.util.List;

/**
 * SFTP 인증 자격증명 스냅샷(불변) — 비밀번호와/또는 공개키 인증용 키쌍 목록. {@code java.security.KeyPair} 만
 * 쓰므로 SSHD 무의존(키 회전 결정 로직을 JDK 단독으로 검증 가능).
 *
 * <p>둘 다 있으면 세션은 키 우선 시도 후 비밀번호로 폴백(MINA 기본 동작).
 */
public record SftpCredentials(String password, List<KeyPair> keyPairs) {

    public SftpCredentials {
        keyPairs = (keyPairs == null) ? List.of() : List.copyOf(keyPairs);
    }

    /** 비밀번호만. */
    public static SftpCredentials password(String password) {
        return new SftpCredentials(password, List.of());
    }

    /** 키쌍만. */
    public static SftpCredentials keys(List<KeyPair> keyPairs) {
        return new SftpCredentials(null, keyPairs);
    }

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    public boolean hasKeys() {
        return !keyPairs.isEmpty();
    }
}
