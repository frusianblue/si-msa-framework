package com.company.framework.file.sftp;

import com.company.framework.file.config.FileStorageProperties;
import com.company.framework.file.sftp.cred.ReloadingSftpCredentialProvider;
import com.company.framework.file.sftp.cred.SftpCredentialProvider;
import com.company.framework.file.storage.FileStorage;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * {@code framework.file.storage.type=sftp} 일 때 SFTP 저장소를 활성화한다. Apache MINA SSHD
 * ({@code sshd-core}/{@code sshd-sftp})가 클래스패스에 있을 때만 등록된다({@code @ConditionalOnClass}).
 *
 * <p>자격증명 공급자: {@code key-rotation.enabled=true} + 개인키 경로가 있으면 파일 변경 감지 재로드
 * ({@link ReloadingSftpCredentialProvider}), 아니면 기동 시 1회 로드 고정({@link SftpCredentialProvider#fixed}).
 * 세션 풀: {@code pool.enabled=true} 면 {@link SftpFileStorage.PoolSettings} 를 넘겨 인증 세션을 재사용한다.
 *
 * <p>앱이 {@link FileStorage} 빈을 직접 정의하면 그쪽이 우선({@code @ConditionalOnMissingBean}). 빈은
 * {@link SftpFileStorage}({@code AutoCloseable})이므로 컨텍스트 종료 시 풀/내부 {@code SshClient} 가 정리된다.
 */
@AutoConfiguration
@ConditionalOnClass(SftpClient.class)
@ConditionalOnProperty(prefix = "framework.file.storage", name = "type", havingValue = "sftp")
public class SftpFileStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FileStorage.class)
    public FileStorage sftpFileStorage(FileStorageProperties props) {
        FileStorageProperties.Sftp s = props.getStorage().getSftp();

        SftpCredentialProvider credentialProvider = buildCredentialProvider(s);
        SftpFileStorage.PoolSettings poolSettings = buildPoolSettings(s);

        return new SftpFileStorage(
                s.getHost(),
                s.getPort(),
                s.getUsername(),
                credentialProvider,
                s.getBaseDir(),
                s.isStrictHostKeyChecking(),
                s.getKnownHostsPath(),
                s.getConnectTimeout(),
                s.getAuthTimeout(),
                poolSettings);
    }

    private static SftpCredentialProvider buildCredentialProvider(FileStorageProperties.Sftp s) {
        String password = s.getPassword();
        String keyPath = s.getPrivateKeyPath();
        String passphrase = s.getPrivateKeyPassphrase();

        boolean rotate = s.getKeyRotation().isEnabled() && keyPath != null && !keyPath.isBlank();
        if (rotate) {
            long intervalNanos = s.getKeyRotation().getCheckInterval().toNanos();
            return new ReloadingSftpCredentialProvider(
                    () -> SftpKeyLoader.load(password, keyPath, passphrase),
                    () -> SftpKeyLoader.fingerprint(keyPath),
                    intervalNanos,
                    System::nanoTime);
        }
        // 기동 시 1회 로드 고정(기존 동작).
        return SftpCredentialProvider.fixed(SftpKeyLoader.load(password, keyPath, passphrase));
    }

    private static SftpFileStorage.PoolSettings buildPoolSettings(FileStorageProperties.Sftp s) {
        FileStorageProperties.Sftp.Pool p = s.getPool();
        if (!p.isEnabled()) {
            return null; // 풀 미사용 = 작업마다 세션 개폐
        }
        return new SftpFileStorage.PoolSettings(p.getMaxTotal(), p.getMaxWait(), p.getMaxIdle(), p.getMaxLifetime());
    }
}
