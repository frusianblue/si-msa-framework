package com.company.framework.file.scan;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClamAV {@code clamd} 데몬과 <b>INSTREAM</b> 프로토콜로 통신하는 안티바이러스 스캐너.
 *
 * <p>외부 라이브러리 없이 <b>순수 JDK 소켓</b>으로 구현한다(새 의존성 0). 프로토콜:
 * <pre>
 *   → "zINSTREAM\0"                       (명령, null 종결)
 *   → [chunkLen(4B, big-endian)][chunk]   (본문을 청크로 반복 전송)
 *   → [0(4B)]                             (길이 0 = 전송 종료)
 *   ← "stream: OK\0"  또는  "stream: &lt;Signature&gt; FOUND\0"
 * </pre>
 *
 * <p>대용량도 청크 스트리밍이라 메모리에 본문 전체를 올리지 않는다(청크 크기 = {@code maxChunkSize}).
 * 데몬 접속/통신 실패 시 동작은 {@code failOpen} 에 따른다 — 기본 {@code false}(fail-closed: 스캔 불가 시 거부,
 * ISMS-P 관점 안전). 가용성을 우선해야 하면 {@code true} 로 통과시킬 수 있다.
 */
public class ClamavFileScanner implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(ClamavFileScanner.class);

    /** INSTREAM 청크 상한(데몬 {@code StreamMaxLength} 이하 권장, 최상위비트 회피). */
    static final int MAX_ALLOWED_CHUNK = 0x3FFFFFFF;

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxChunkSize;
    private final boolean failOpen;

    public ClamavFileScanner(
            String host, int port, int connectTimeoutMs, int readTimeoutMs, int maxChunkSize, boolean failOpen) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        if (maxChunkSize <= 0 || maxChunkSize > MAX_ALLOWED_CHUNK) {
            throw new IllegalArgumentException("maxChunkSize 는 1.." + MAX_ALLOWED_CHUNK + " 범위여야 합니다: " + maxChunkSize);
        }
        this.maxChunkSize = maxChunkSize;
        this.failOpen = failOpen;
    }

    @Override
    public ScanResult scan(InputStream content, long size, String filename) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            try (OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream()) {
                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                streamChunks(content, out);
                out.flush();
                String response = readResponse(in);
                return parse(response);
            }
        } catch (BusinessException be) {
            throw be; // 감염 판정은 그대로 전파
        } catch (IOException | RuntimeException e) {
            if (failOpen) {
                log.warn("ClamAV 스캔 실패(fail-open: 통과 처리) host={}:{} file={} - {}", host, port, filename, e.toString());
                return ScanResult.clean("clamav");
            }
            log.error("ClamAV 스캔 실패(fail-closed: 거부) host={}:{} file={}", host, port, filename, e);
            throw new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "바이러스 검사를 수행할 수 없어 업로드를 거부했습니다.");
        }
    }

    /** 본문을 [len][data] 청크로 프레이밍해 전송하고, 마지막에 길이 0 으로 종료를 알린다. */
    private void streamChunks(InputStream content, OutputStream out) throws IOException {
        byte[] buf = new byte[maxChunkSize];
        int read;
        while ((read = readFully(content, buf)) > 0) {
            writeInt(out, read);
            out.write(buf, 0, read);
        }
        writeInt(out, 0); // 종료 마커
    }

    /** 버퍼가 가득 차거나 스트림이 끝날 때까지 읽는다(부분 read 방어). */
    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int r = in.read(buf, total, buf.length - total);
            if (r < 0) break;
            total += r;
        }
        return total;
    }

    private static void writeInt(OutputStream out, int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
        int b;
        // clamd 는 응답을 null 또는 개행으로 종결한다. 둘 중 먼저 오는 것까지 읽는다.
        while ((b = in.read()) != -1) {
            if (b == 0 || b == '\n') break;
            bos.write(b);
        }
        return bos.toString(StandardCharsets.US_ASCII).trim();
    }

    /** {@code stream: OK} / {@code stream: <sig> FOUND} / 그 외(에러)를 해석. */
    ScanResult parse(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("ClamAV 응답이 비었습니다.");
        }
        if (response.endsWith("OK")) {
            return ScanResult.clean("clamav");
        }
        if (response.endsWith("FOUND")) {
            // 형식: "stream: <Signature> FOUND"
            String sig = response;
            int colon = response.indexOf(':');
            if (colon >= 0) sig = response.substring(colon + 1).trim();
            if (sig.endsWith("FOUND"))
                sig = sig.substring(0, sig.length() - "FOUND".length()).trim();
            return ScanResult.infected(sig.isEmpty() ? "UNKNOWN" : sig, "clamav");
        }
        // ERROR 등 비정상 응답
        throw new IllegalStateException("ClamAV 비정상 응답: " + response);
    }

    @Override
    public String type() {
        return "clamav";
    }
}
