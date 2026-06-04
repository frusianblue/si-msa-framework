package com.company.framework.file.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.file.sftp.cred.SftpCredentialProvider;
import com.company.framework.file.sftp.cred.SftpCredentials;
import com.company.framework.file.storage.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.subsystem.SubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 세션 풀(pool.enabled=true) 경로를 내장 MINA SFTP 서버로 검증. 순수 풀 로직은 standalone 하니스로 별도 검증하고,
 * 여기서는 실제 인증 세션이 풀에서 재사용·반납되며 다회/동시 작업이 정상 동작하는지(통합)를 본다.
 */
class SftpFileStoragePooledRoundTripTest {

    @TempDir
    Path serverRoot;

    private SshServer server;
    private SftpFileStorage storage;

    @BeforeEach
    void startServer() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator((u, p, s) -> "tester".equals(u) && "secret".equals(p));
        List<SubsystemFactory> subsystems = new ArrayList<>();
        subsystems.add(new SftpSubsystemFactory());
        server.setSubsystemFactories(subsystems);
        server.setFileSystemFactory(new VirtualFileSystemFactory(serverRoot));
        server.start();

        storage = new SftpFileStorage(
                "localhost",
                server.getPort(),
                "tester",
                SftpCredentialProvider.fixed(SftpCredentials.password("secret")),
                "",
                false,
                null,
                Duration.ofSeconds(15),
                Duration.ofSeconds(15),
                // 작은 풀: maxTotal=2 로 재사용/대기 경로를 확실히 태운다.
                new SftpFileStorage.PoolSettings(
                        2, Duration.ofSeconds(15), Duration.ofMinutes(5), Duration.ofMinutes(30)));
    }

    @AfterEach
    void stop() throws Exception {
        if (storage != null) {
            storage.close();
        }
        if (server != null) {
            server.stop(true);
        }
    }

    @Test
    @DisplayName("풀 활성 — 다회 store/load 가 세션 재사용으로 정상 동작")
    void sequentialReuse() throws Exception {
        for (int i = 0; i < 8; i++) {
            byte[] payload = ("payload-" + i).getBytes(StandardCharsets.UTF_8);
            StoredFile sf =
                    storage.store(new ByteArrayInputStream(payload), "f" + i + ".txt", "text/plain", payload.length);
            try (InputStream in = storage.load(sf.storedPath())) {
                assertThat(in.readAllBytes()).isEqualTo(payload);
            }
            assertThat(storage.contentLength(sf.storedPath())).isEqualTo(payload.length);
        }
    }

    @Test
    @DisplayName("풀 활성 — 동시 작업(스레드 4개)이 maxTotal=2 풀에서 안전하게 처리")
    void concurrentBorrow() throws Exception {
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int id = t;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 5; i++) {
                        byte[] payload = ("t" + id + "-" + i).getBytes(StandardCharsets.UTF_8);
                        StoredFile sf = storage.store(
                                new ByteArrayInputStream(payload),
                                "t" + id + "_" + i + ".txt",
                                "text/plain",
                                payload.length);
                        try (InputStream in = storage.load(sf.storedPath())) {
                            if (!java.util.Arrays.equals(in.readAllBytes(), payload)) {
                                return false;
                            }
                        }
                    }
                    return true;
                }));
            }
            for (Future<Boolean> f : futures) {
                assertThat(f.get()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
