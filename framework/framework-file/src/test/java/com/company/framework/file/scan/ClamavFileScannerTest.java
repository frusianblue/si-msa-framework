package com.company.framework.file.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ClamAV INSTREAM 프로토콜을 가짜 clamd(로컬 {@link ServerSocket})로 왕복 검증한다 — 실제 데몬 불필요.
 * 청크 프레이밍([len][data]…[0])과 명령("zINSTREAM\0") 전송, 응답 파싱(OK/FOUND/ERROR), fail-closed/open 을 본다.
 */
class ClamavFileScannerTest {

    @Test
    @DisplayName("정상 응답(stream: OK) → clean, INSTREAM 프레이밍 정확")
    void cleanRoundTrip() throws Exception {
        byte[] payload = "harmless-content-payload".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean framingOk = new AtomicBoolean(false);
        try (MockClamd clamd = new MockClamd("stream: OK\0", payload, framingOk)) {
            // 작은 청크로 다중 프레이밍 강제
            ClamavFileScanner scanner = new ClamavFileScanner("127.0.0.1", clamd.port(), 1000, 1000, 8, false);
            ScanResult r = scanner.scan(new ByteArrayInputStream(payload), payload.length, "clean.txt");
            assertThat(r.clean()).isTrue();
            assertThat(r.scannerType()).isEqualTo("clamav");
        }
        assertThat(framingOk).isTrue();
    }

    @Test
    @DisplayName("감염 응답(... FOUND) → infected + 시그니처 추출")
    void infectedRoundTrip() throws Exception {
        byte[] payload = "eicar-like".getBytes(StandardCharsets.UTF_8);
        try (MockClamd clamd = new MockClamd("stream: Win.Test.EICAR_HDB-1 FOUND\0", payload, new AtomicBoolean())) {
            ClamavFileScanner scanner = new ClamavFileScanner("127.0.0.1", clamd.port(), 1000, 1000, 4096, false);
            ScanResult r = scanner.scan(new ByteArrayInputStream(payload), payload.length, "v.bin");
            assertThat(r.infected()).isTrue();
            assertThat(r.signature()).isEqualTo("Win.Test.EICAR_HDB-1");
        }
    }

    @Test
    @DisplayName("ERROR 응답 → fail-closed 면 BusinessException")
    void errorResponseFailClosed() throws Exception {
        byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
        try (MockClamd clamd = new MockClamd("INSTREAM size limit exceeded. ERROR\0", payload, new AtomicBoolean())) {
            ClamavFileScanner scanner = new ClamavFileScanner("127.0.0.1", clamd.port(), 1000, 1000, 4096, false);
            assertThatThrownBy(() -> scanner.scan(new ByteArrayInputStream(payload), payload.length, "e.bin"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("데몬 접속 불가 → fail-closed=거부, fail-open=통과")
    void connectionFailureModes() throws Exception {
        int deadPort;
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort();
        } // 닫힘 → 접속 거부

        ClamavFileScanner failClosed = new ClamavFileScanner("127.0.0.1", deadPort, 200, 200, 4096, false);
        assertThatThrownBy(() -> failClosed.scan(new ByteArrayInputStream(new byte[] {1}), 1, "f"))
                .isInstanceOf(BusinessException.class);

        ClamavFileScanner failOpen = new ClamavFileScanner("127.0.0.1", deadPort, 200, 200, 4096, true);
        assertThat(failOpen.scan(new ByteArrayInputStream(new byte[] {1}), 1, "f")
                        .clean())
                .isTrue();
    }

    @Test
    @DisplayName("maxChunkSize 범위 검증")
    void chunkSizeValidation() {
        assertThatThrownBy(() -> new ClamavFileScanner("h", 1, 1, 1, 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClamavFileScanner("h", 1, 1, 1, ClamavFileScanner.MAX_ALLOWED_CHUNK + 1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 가짜 clamd: zINSTREAM 명령 + 청크들을 수신·검증하고 지정 응답을 돌려준다. */
    private static final class MockClamd implements AutoCloseable {
        private final ServerSocket server;
        private final Thread thread;

        MockClamd(String response, byte[] expectedBody, AtomicBoolean framingOk) throws Exception {
            this.server = new ServerSocket(0);
            this.thread = new Thread(() -> {
                try (Socket s = server.accept();
                        DataInputStream in = new DataInputStream(s.getInputStream());
                        OutputStream out = s.getOutputStream()) {
                    ByteArrayOutputStream cmd = new ByteArrayOutputStream();
                    int b;
                    while ((b = in.read()) != -1 && b != 0) cmd.write(b);
                    boolean cmdOk = cmd.toString(StandardCharsets.US_ASCII).equals("zINSTREAM");
                    ByteArrayOutputStream body = new ByteArrayOutputStream();
                    while (true) {
                        int len = in.readInt();
                        if (len == 0) break;
                        byte[] chunk = new byte[len];
                        in.readFully(chunk);
                        body.write(chunk);
                    }
                    framingOk.set(cmdOk && Arrays.equals(body.toByteArray(), expectedBody));
                    out.write(response.getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (Exception ignored) {
                    // 테스트 종료 시 소켓 닫힘 등 — 무시
                }
            });
            this.thread.setDaemon(true);
            this.thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            thread.join(2000);
            server.close();
        }
    }
}
