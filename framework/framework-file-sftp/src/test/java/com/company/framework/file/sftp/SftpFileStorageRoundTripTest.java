package com.company.framework.file.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import com.company.framework.file.storage.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * 내장 Apache MINA SFTP 서버에 대해 store/load/delete/contentLength/loadRange 실제 왕복 검증.
 * 서버의 가상 파일시스템 루트를 {@code @TempDir} 로 두어 디스크에 실제 파일이 생기는지까지 본다.
 * 호스트 키는 매 기동 임의 생성이므로 클라이언트는 strict-host-key-checking=false.
 */
class SftpFileStorageRoundTripTest {

    @TempDir
    Path serverRoot;

    private SshServer server;
    private SftpFileStorage storage;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setPort(0); // ephemeral
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider()); // in-memory host key
        server.setPasswordAuthenticator((u, p, s) -> "tester".equals(u) && "secret".equals(p));
        List<SubsystemFactory> subsystems = new ArrayList<>();
        subsystems.add(new SftpSubsystemFactory());
        server.setSubsystemFactories(subsystems);
        server.setFileSystemFactory(new VirtualFileSystemFactory(serverRoot));
        server.start();
        port = server.getPort();

        // baseDir="" → 서버 홈(가상 루트=serverRoot) 상대로 키가 저장된다.
        storage = new SftpFileStorage(
                "localhost",
                port,
                "tester",
                "secret",
                null,
                null,
                "",
                false,
                null,
                Duration.ofSeconds(15),
                Duration.ofSeconds(15));
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

    private static byte[] seq(int n) {
        byte[] d = new byte[n];
        for (int i = 0; i < n; i++) {
            d[i] = (byte) i;
        }
        return d;
    }

    @Test
    @DisplayName("store → 디스크에 키 경로로 파일 생성 + storedPath 형식")
    void storesToDisk() {
        byte[] payload = seq(50);
        StoredFile sf = storage.store(new ByteArrayInputStream(payload), "report.bin", "application/octet-stream", 50);
        assertThat(sf.storedPath()).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.bin");
        assertThat(Files.exists(serverRoot.resolve(sf.storedPath()))).isTrue();
    }

    @Test
    @DisplayName("store → load 바이트 라운드트립 + contentLength")
    void loadRoundTrip() throws Exception {
        byte[] payload = "안녕하세요 SFTP round-trip 12345".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StoredFile sf = storage.store(new ByteArrayInputStream(payload), "memo.txt", "text/plain", payload.length);

        try (InputStream in = storage.load(sf.storedPath())) {
            assertThat(in.readAllBytes()).isEqualTo(payload);
        }
        assertThat(storage.contentLength(sf.storedPath())).isEqualTo(payload.length);
    }

    @Test
    @DisplayName("loadRange — 임의 구간만 정확히 읽는다")
    void rangeRead() throws Exception {
        byte[] payload = seq(50);
        StoredFile sf = storage.store(new ByteArrayInputStream(payload), "data.bin", "application/octet-stream", 50);

        try (InputStream in = storage.loadRange(sf.storedPath(), 7, 11)) {
            assertThat(in.readAllBytes()).isEqualTo(Arrays.copyOfRange(payload, 7, 12)); // 7..11 inclusive
        }
    }

    @Test
    @DisplayName("delete → 파일 제거 + 이후 load 는 NOT_FOUND")
    void deleteThenNotFound() {
        byte[] payload = seq(10);
        StoredFile sf = storage.store(new ByteArrayInputStream(payload), "tmp.bin", "application/octet-stream", 10);
        assertThat(Files.exists(serverRoot.resolve(sf.storedPath()))).isTrue();

        storage.delete(sf.storedPath());
        assertThat(Files.exists(serverRoot.resolve(sf.storedPath()))).isFalse();
        assertThatThrownBy(() -> storage.load(sf.storedPath())).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("없는 파일 삭제는 멱등(예외 없음)")
    void deleteMissingIsIdempotent() {
        storage.delete("2099/01/01/doesnotexist.bin"); // 예외 없이 통과해야 함
    }
}
