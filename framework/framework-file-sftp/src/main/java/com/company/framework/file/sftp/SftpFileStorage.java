package com.company.framework.file.sftp;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.file.storage.FileStorage;
import com.company.framework.file.storage.RangeReadableStorage;
import com.company.framework.file.storage.StoredFile;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SFTP(SSH File Transfer Protocol) 원격 저장소. 실제 전송은 Apache MINA SSHD({@code sshd-core}/{@code sshd-sftp})에
 * 위임한다(순수 JDK SSH 가 없어 라이브러리 필수 — BOM 밖 의존성, framework-file-sftp 모듈에만 포함).
 *
 * <p>키: {@code yyyy/MM/dd/{uuid}.{ext}} (저장 결과의 storedPath = baseDir 기준 상대 키, load/delete 시 baseDir 재결합).
 *
 * <p><b>연결 모델</b>: SSH 는 연결 지향·상태형이라 S3 의 무상태 HTTP 와 다르다. {@link SshClient} 는 1회 start 후
 * 재사용하고, <b>작업마다 세션을 새로 열고 닫는다</b>(풀링 없음 — 예측 가능·stale 연결 버그 회피; 고처리량이 필요하면
 * 후속으로 풀 옵트인). {@link #load}/{@link #loadRange} 가 돌려주는 스트림은 세션을 물고 있다가 스트림 close 시
 * 세션까지 함께 정리한다({@link SessionBoundInputStream}).
 *
 * <p><b>호스트 키 검증</b>: 기본 strict — known_hosts 에 없는 서버는 거부(fail-closed). {@code strict-host-key-checking=false}
 * 로 끄면 모든 키 수용(개발 편의, 경고 로그). {@link RangeReadableStorage}(부분 다운로드) 구현 — SFTP 는 임의 오프셋 읽기를 지원한다.
 */
public class SftpFileStorage implements FileStorage, RangeReadableStorage, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SftpFileStorage.class);
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final SshClient client;
    private final String host;
    private final int port;
    private final String username;
    private final String password; // nullable
    private final List<KeyPair> keyPairs; // nullable/empty
    private final String baseDir;
    private final Duration connectTimeout;
    private final Duration authTimeout;

    public SftpFileStorage(
            String host,
            int port,
            String username,
            String password,
            String privateKeyPath,
            String privateKeyPassphrase,
            String baseDir,
            boolean strictHostKeyChecking,
            String knownHostsPath,
            Duration connectTimeout,
            Duration authTimeout) {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("framework.file.storage.sftp.host 는 필수입니다.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("framework.file.storage.sftp.username 는 필수입니다.");
        }
        this.host = host;
        this.port = port <= 0 ? 22 : port;
        this.username = username;
        this.password = password;
        this.baseDir = (baseDir == null) ? "" : baseDir.trim();
        this.connectTimeout = (connectTimeout == null) ? Duration.ofSeconds(10) : connectTimeout;
        this.authTimeout = (authTimeout == null) ? Duration.ofSeconds(10) : authTimeout;
        this.keyPairs = loadKeyPairs(privateKeyPath, privateKeyPassphrase);

        SshClient c = SshClient.setUpDefaultClient();
        c.setServerKeyVerifier(buildVerifier(strictHostKeyChecking, knownHostsPath));
        c.start();
        this.client = c;
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
            // 키 경로/암호 오류는 기동 단계에서 명확히 실패(메시지에 키/암호 평문 미포함)
            throw new IllegalStateException("SFTP 개인키 로드 실패(경로/패스프레이즈 확인): " + privateKeyPath);
        }
    }

    private static ServerKeyVerifier buildVerifier(boolean strict, String knownHostsPath) {
        if (!strict) {
            log.warn("framework.file.storage.sftp.strict-host-key-checking=false — 모든 호스트 키를 수용합니다(개발 전용, "
                    + "중간자 공격에 취약). 운영에서는 true + known_hosts 를 사용하세요.");
            return AcceptAllServerKeyVerifier.INSTANCE;
        }
        Path known = (knownHostsPath == null || knownHostsPath.isBlank())
                ? Path.of(System.getProperty("user.home", "."), ".ssh", "known_hosts")
                : Path.of(knownHostsPath);
        // known_hosts 에 없으면 거부(RejectAll 위임) — fail-closed.
        return new KnownHostsServerKeyVerifier(RejectAllServerKeyVerifier.INSTANCE, known);
    }

    // ---- FileStorage ----

    @Override
    public StoredFile store(InputStream content, String originalName, String contentType, long size) {
        String key = newKey(originalName);
        String remote = join(baseDir, key);
        withSftp(sftp -> {
            for (String dir : ancestorDirs(remote)) {
                mkdirIfAbsent(sftp, dir);
            }
            try (OutputStream out = sftp.write(
                    remote, SftpClient.OpenMode.Create, SftpClient.OpenMode.Write, SftpClient.OpenMode.Truncate)) {
                content.transferTo(out);
            }
            return null;
        });
        return new StoredFile(key, originalName, contentType, size);
    }

    @Override
    public InputStream load(String storedPath) {
        String remote = join(baseDir, storedPath);
        ClientSession session = openSession();
        SftpClient sftp = null;
        try {
            sftp = SftpClientFactory.instance().createSftpClient(session);
            InputStream raw = sftp.read(remote, SftpClient.OpenMode.Read);
            return new SessionBoundInputStream(raw, sftp, session);
        } catch (SftpException e) {
            closeQuietly(sftp, session);
            throw mapSftp(e);
        } catch (IOException e) {
            closeQuietly(sftp, session);
            throw wrap();
        }
    }

    @Override
    public void delete(String storedPath) {
        String remote = join(baseDir, storedPath);
        withSftp(sftp -> {
            try {
                sftp.remove(remote);
            } catch (SftpException e) {
                if (e.getStatus() != SftpConstants.SSH_FX_NO_SUCH_FILE) {
                    throw e; // 없는 파일 삭제는 멱등 성공으로 간주
                }
            }
            return null;
        });
    }

    @Override
    public String type() {
        return "sftp";
    }

    // ---- RangeReadableStorage ----

    @Override
    public long contentLength(String storedPath) {
        String remote = join(baseDir, storedPath);
        return withSftp(sftp -> {
            try {
                return sftp.stat(remote).getSize();
            } catch (SftpException e) {
                if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE) {
                    throw mapSftp(e);
                }
                return -1L;
            }
        });
    }

    @Override
    public InputStream loadRange(String storedPath, long start, long endInclusive) {
        String remote = join(baseDir, storedPath);
        ClientSession session = openSession();
        SftpClient sftp = null;
        try {
            sftp = SftpClientFactory.instance().createSftpClient(session);
            InputStream raw = sftp.read(remote, SftpClient.OpenMode.Read);
            skipFully(raw, start);
            long length = endInclusive - start + 1;
            return new SessionBoundInputStream(new BoundedInputStream(raw, length), sftp, session);
        } catch (SftpException e) {
            closeQuietly(sftp, session);
            throw mapSftp(e);
        } catch (IOException e) {
            closeQuietly(sftp, session);
            throw wrap();
        }
    }

    // ---- session / sftp plumbing ----

    @FunctionalInterface
    private interface SftpFn<T> {
        T apply(SftpClient sftp) throws IOException;
    }

    private <T> T withSftp(SftpFn<T> fn) {
        try (ClientSession session = openSession();
                SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            return fn.apply(sftp);
        } catch (SftpException e) {
            throw mapSftp(e);
        } catch (IOException e) {
            throw wrap();
        }
    }

    private ClientSession openSession() {
        try {
            ClientSession session =
                    client.connect(username, host, port).verify(connectTimeout).getSession();
            boolean authed = false;
            try {
                if (password != null && !password.isBlank()) {
                    session.addPasswordIdentity(password);
                }
                for (KeyPair kp : keyPairs) {
                    session.addPublicKeyIdentity(kp);
                }
                session.auth().verify(authTimeout);
                authed = true;
                return session;
            } finally {
                if (!authed) {
                    closeQuietly(null, session);
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "SFTP 연결/인증에 실패했습니다.");
        }
    }

    private static void mkdirIfAbsent(SftpClient sftp, String dir) throws IOException {
        try {
            sftp.stat(dir); // 존재하면 통과
        } catch (SftpException e) {
            if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE) {
                try {
                    sftp.mkdir(dir);
                } catch (SftpException me) {
                    // 동시 생성 등으로 이미 존재하면 무시
                    if (me.getStatus() != SftpConstants.SSH_FX_FILE_ALREADY_EXISTS) {
                        throw me;
                    }
                }
            } else {
                throw e;
            }
        }
    }

    private static void closeQuietly(SftpClient sftp, ClientSession session) {
        if (sftp != null) {
            try {
                sftp.close();
            } catch (IOException ignore) {
                // best-effort
            }
        }
        if (session != null) {
            try {
                session.close();
            } catch (IOException ignore) {
                // best-effort
            }
        }
    }

    private static BusinessException mapSftp(SftpException e) {
        if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE) {
            return new BusinessException(ErrorCode.Common.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        return new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "SFTP 작업에 실패했습니다.");
    }

    private static BusinessException wrap() {
        // 호스트/자격증명 등 민감정보를 메시지에 싣지 않는다.
        return new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "SFTP 작업에 실패했습니다.");
    }

    @Override
    public void close() {
        try {
            client.stop();
        } catch (Exception ignore) {
            // best-effort shutdown
        }
    }

    // ---- pure path helpers (JDK 단독 검증 대상) ----

    /** baseDir 과 키를 슬래시 1개로 결합(중복 슬래시 제거). baseDir 가 비면 키만(서버 홈 상대). */
    static String join(String baseDir, String key) {
        String b = (baseDir == null) ? "" : baseDir.trim();
        String k = (key == null) ? "" : key;
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        while (k.startsWith("/")) {
            k = k.substring(1);
        }
        return b.isEmpty() ? k : b + "/" + k;
    }

    /** 파일 경로의 부모 디렉토리(없으면 빈 문자열, 루트 직속이면 "/"). */
    static String parentOf(String path) {
        int i = path.lastIndexOf('/');
        if (i < 0) {
            return "";
        }
        return (i == 0) ? "/" : path.substring(0, i);
    }

    /** 파일 경로의 모든 조상 디렉토리를 얕은→깊은 순서로(파일 자신 제외). mkdir -p 대용. */
    static List<String> ancestorDirs(String fullFilePath) {
        String dir = parentOf(fullFilePath);
        if (dir.isBlank() || dir.equals("/")) {
            return List.of();
        }
        boolean absolute = dir.startsWith("/");
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String seg : dir.split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            cur.append('/').append(seg);
            out.add(absolute ? cur.toString() : cur.substring(1));
        }
        return out;
    }

    static String newKey(String originalName) {
        String datePath = LocalDate.now().format(DATE_PATH);
        String ext = extOf(originalName);
        return datePath + "/" + UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
    }

    static String extOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return (dot < 0) ? "" : name.substring(dot + 1).toLowerCase();
    }

    /** {@code n} 바이트를 확실히 건너뛴다(skip 이 0을 반환하면 read 로 버려가며 진행). */
    static void skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        byte[] buf = null;
        while (remaining > 0) {
            long s = in.skip(remaining);
            if (s > 0) {
                remaining -= s;
                continue;
            }
            if (buf == null) {
                buf = new byte[8192];
            }
            int r = in.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (r < 0) {
                break; // EOF
            }
            remaining -= r;
        }
    }

    /** 세션/SFTP 채널을 물고 있다가 스트림 close 시 함께 닫는 래퍼(load/loadRange 의 지연 스트리밍용). */
    private static final class SessionBoundInputStream extends FilterInputStream {
        private final SftpClient sftp;
        private final ClientSession session;

        SessionBoundInputStream(InputStream in, SftpClient sftp, ClientSession session) {
            super(in);
            this.sftp = sftp;
            this.session = session;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                closeQuietly(sftp, session);
            }
        }
    }

    /** 최대 {@code limit} 바이트까지만 읽도록 제한(Range 끝 경계). */
    static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        BoundedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = Math.max(0, limit);
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = super.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int n = super.read(b, off, toRead);
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }
    }
}
