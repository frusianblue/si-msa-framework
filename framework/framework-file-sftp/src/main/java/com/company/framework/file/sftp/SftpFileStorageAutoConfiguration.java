package com.company.framework.file.sftp;

import com.company.framework.file.config.FileStorageProperties;
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
 * <p>앱이 {@link FileStorage} 빈을 직접 정의하면 그쪽이 우선({@code @ConditionalOnMissingBean}). 빈은
 * {@link SftpFileStorage}({@code AutoCloseable})이므로 컨텍스트 종료 시 내부 {@code SshClient} 가 정리된다.
 */
@AutoConfiguration
@ConditionalOnClass(SftpClient.class)
@ConditionalOnProperty(prefix = "framework.file.storage", name = "type", havingValue = "sftp")
public class SftpFileStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FileStorage.class)
    public FileStorage sftpFileStorage(FileStorageProperties props) {
        FileStorageProperties.Sftp s = props.getStorage().getSftp();
        return new SftpFileStorage(
                s.getHost(),
                s.getPort(),
                s.getUsername(),
                s.getPassword(),
                s.getPrivateKeyPath(),
                s.getPrivateKeyPassphrase(),
                s.getBaseDir(),
                s.isStrictHostKeyChecking(),
                s.getKnownHostsPath(),
                s.getConnectTimeout(),
                s.getAuthTimeout());
    }
}
