package com.company.framework.file.sftp;

import com.company.framework.file.sftp.cred.SftpCredentials;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;

/**
 * SFTP 자격증명 로딩 + 키 파일 변경 지문 산출. 키 읽기는 MINA {@code FileKeyPairProvider} 에 위임하므로 이 클래스는
 * SSHD 의존(반면 {@code cred} 패키지의 자격증명/회전 로직은 JDK 단독 검증 대상).
 *
 * <p>{@code SftpCredentialProvider} 구현들이 이 헬퍼로 자격증명을 만든다:
 * 고정 공급자는 1회 호출, 회전 공급자는 변경 감지 시마다 호출한다.
 */
final class SftpKeyLoader {

    private SftpKeyLoader() {}

    /**
     * 비밀번호 + (선택)개인키 파일에서 자격증명 스냅샷을 만든다. 키 경로/암호 오류는 명확한 메시지로 실패
     * (키/암호 평문은 메시지에 포함하지 않는다).
     */
    static SftpCredentials load(String password, String privateKeyPath, String passphrase) {
        List<KeyPair> keyPairs = loadKeyPairs(privateKeyPath, passphrase);
        return new SftpCredentials(password, keyPairs);
    }

    private static List<KeyPair> loadKeyPairs(String privateKeyPath, String passphrase) {
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            return List.of();
        }
        try {
            FileKeyPairProvider provider = new FileKeyPairProvider(Path.of(privateKeyPath));
            if (passphrase != null && !passphrase.isBlank()) {
                provider.setPasswordFinder(FilePasswordProvider.of(passphrase));
            }
            List<KeyPair> kps = new ArrayList<>();
            for (KeyPair kp : provider.loadKeys(null)) {
                kps.add(kp);
            }
            if (kps.isEmpty()) {
                throw new IllegalStateException("SFTP 개인키를 읽었으나 키가 비어 있습니다: " + privateKeyPath);
            }
            return List.copyOf(kps);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("SFTP 개인키 로드 실패(경로/패스프레이즈 확인): " + privateKeyPath);
        }
    }

    /**
     * 키 파일 변경 감지 지문(마지막수정시각 + 크기). 경로가 없으면 {@code null}(비밀번호 인증만이면 회전 대상 없음).
     * 읽기 실패 시 {@code null} 을 반환해 회전 공급자가 재로드를 시도하지 않게 한다(다음 주기 재확인).
     */
    static Object fingerprint(String privateKeyPath) {
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            return null;
        }
        try {
            Path p = Path.of(privateKeyPath);
            long mtime = Files.getLastModifiedTime(p).toMillis();
            long size = Files.size(p);
            return mtime + ":" + size;
        } catch (Exception e) {
            return null;
        }
    }
}
