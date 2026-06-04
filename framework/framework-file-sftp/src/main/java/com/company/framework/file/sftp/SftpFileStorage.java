package com.company.framework.file.sftp;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.file.sftp.cred.SftpCredentialProvider;
import com.company.framework.file.sftp.cred.SftpCredentials;
import com.company.framework.file.sftp.pool.BoundedObjectPool;
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
import java.util.Objects;
import java.util.UUID;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
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
 * <p><b>연결 모델</b>: SSH 는 연결 지향·상태형이라 S3 의 무상태 HTTP 와 다르다. {@link SshClient} 는 1회 start 후 재사용.
 * 세션은 두 모드:
 * <ul>
 *   <li><b>풀 비활성(기본)</b>: 작업마다 세션을 새로 열고 닫는다(예측 가능·stale 회피).
 *   <li><b>풀 활성(옵트인)</b>: 인증된 세션을 {@link BoundedObjectPool} 로 재사용한다 — 고처리량에서 SSH/TCP/인증
 *       핸드셰이크 비용 절감. 대여 직전 {@code isOpen()} 검증, 유휴/수명 만료 시 교체. 수명(maxLifetime)은 키 회전
 *       전파에 중요(옛 키로 인증된 장수 세션 강제 교체).
 * </ul>
 *
 * <p><b>자격증명</b>: 매 세션 생성 시 {@link SftpCredentialProvider#current()} 로 해석한다 — 키 회전(파일 변경 감지
 * 재로드) 시 <b>새 세션</b>부터 새 키로 인증된다. {@link #load}/{@link #loadRange} 가 돌려주는 스트림은 세션을 물고
 * 있다가 스트림 close 시 세션을 풀에 반납(또는 닫음)한다({@link SessionBoundInputStream}).
 *
 * <p><b>호스트 키 검증</b>: 기본 strict — known_hosts 에 없는 서버는 거부(fail-closed). {@code strict-host-key-checking=false}
 * 로 끄면 모든 키 수용(개발 편의, 경고 로그). {@link RangeReadableStorage}(부분 다운로드) 구현 — SFTP 는 임의 오프셋 읽기를 지원한다.
 */
public class SftpFileStorage implements FileStorage, RangeReadableStorage, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SftpFileStorage.class);
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 세션 풀 설정(옵트인). null 이면 풀 미사용 = 작업마다 세션 개폐(기존 동작). */
    public record PoolSettings(int maxTotal, Duration maxWait, Duration maxIdle, Duration maxLifetime) {}

    private final SshClient client;
    private final String host;
    private final int port;
    private final String username;
    private final SftpCredentialProvider credentialProvider;
    private final String baseDir;
    private final Duration connectTimeout;
    private final Duration authTimeout;
    private final BoundedObjectPool<ClientSession> pool; // nullable

    public SftpFileStorage(
            String host,
            int port,
            String username,
            SftpCredentialProvider credentialProvider,
            String baseDir,
            boolean strictHostKeyChecking,
            String knownHostsPath,
            Duration connectTimeout,
            Duration authTimeout,
            PoolSettings poolSettings) {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("framework.file.storage.sftp.host 는 필수입니다.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("framework.file.storage.sftp.username 는 필수입니다.");
        }
        this.host = host;
        this.port = port <= 0 ? 22 : port;
        this.username = username;
        this.credentialProvider = Objects.requireNonNull(credentialProvider, "credentialProvider");
        this.baseDir = (baseDir == null) ? "" : baseDir.trim();
        this.connectTimeout = (connectTimeout == null) ? Duration.ofSeconds(10) : connectTimeout;
        this.authTimeout = (authTimeout == null) ? Duration.ofSeconds(10) : authTimeout;

        SshClient c = SshClient.setUpDefaultClient();
        c.setServerKeyVerifier(buildVerifier(strictHostKeyChecking, knownHostsPath));
        c.start();
        this.client = c;

        this.pool = (poolSettings == null)
                ? null
                : new BoundedObjectPool<>(
                        poolSettings.maxTotal(),
                        toNanos(poolSettings.maxWait()),
                        toNanos(poolSettings.maxIdle()),
                        toNanos(poolSettings.maxLifetime()),
                        this::openSession, // creator: 연결 + 현재 자격증명으로 인증
                        ClientSession::isOpen, // validate-on-borrow: 끊긴 세션 회피
                        session -> closeQuietly(null, session), // destroyer
                        System::nanoTime);
    }

    private static long toNanos(Duration d) {
        return (d == null || d.isNegative()) ? 0L : d.toNanos();
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
        ClientSession session = acquireSession();
        SftpClient sftp = null;
        try {
            sftp = SftpClientFactory.instance().createSftpClient(session);
            InputStream raw = sftp.read(remote, SftpClient.OpenMode.Read);
            return new SessionBoundInputStream(raw, sftp, session, this);
        } catch (SftpException e) {
            // 파일 없음 등 프로토콜 오류 — 세션은 정상 → 재사용 가능(ok=true)
            closeChannelRecycle(sftp, session, true);
            throw mapSftp(e);
        } catch (IOException e) {
            closeChannelRecycle(sftp, session, false);
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
        ClientSession session = acquireSession();
        SftpClient sftp = null;
        try {
            sftp = SftpClientFactory.instance().createSftpClient(session);
            InputStream raw = sftp.read(remote, SftpClient.OpenMode.Read);
            skipFully(raw, start);
            long length = endInclusive - start + 1;
            return new SessionBoundInputStream(new BoundedInputStream(raw, length), sftp, session, this);
        } catch (SftpException e) {
            closeChannelRecycle(sftp, session, true);
            throw mapSftp(e);
        } catch (IOException e) {
            closeChannelRecycle(sftp, session, false);
            throw wrap();
        }
    }

    // ---- session / sftp plumbing ----

    @FunctionalInterface
    private interface SftpFn<T> {
        T apply(SftpClient sftp) throws IOException;
    }

    private <T> T withSftp(SftpFn<T> fn) {
        ClientSession session = acquireSession();
        SftpClient sftp = null;
        boolean sessionOk = false;
        try {
            sftp = SftpClientFactory.instance().createSftpClient(session);
            T result = fn.apply(sftp);
            sessionOk = true;
            return result;
        } catch (SftpException e) {
            sessionOk = true; // 프로토콜 오류 — 세션 자체는 정상
            throw mapSftp(e);
        } catch (IOException e) {
            sessionOk = false; // 전송 오류 — 세션 의심
            throw wrap();
        } finally {
            if (sftp != null) {
                try {
                    sftp.close();
                } catch (IOException ignore) {
                    // best-effort
                }
            }
            recycleSession(session, sessionOk);
        }
    }

    /** 세션 확보: 풀이 있으면 대여, 없으면 새로 열고 인증. */
    private ClientSession acquireSession() {
        if (pool != null) {
            try {
                return pool.borrow();
            } catch (BoundedObjectPool.PoolTimeoutException e) {
                throw new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "SFTP 연결 풀이 가득 찼습니다.");
            }
        }
        return openSession();
    }

    /** 세션 정리: 풀이 있으면 반납(ok)/폐기(!ok), 없으면 닫는다. */
    private void recycleSession(ClientSession session, boolean ok) {
        if (session == null) {
            return;
        }
        if (pool != null) {
            if (ok) {
                pool.release(session);
            } else {
                pool.invalidate(session);
            }
        } else {
            closeQuietly(null, session);
        }
    }

    /** SFTP 채널을 닫고 세션을 정리(스트림 열기 실패 경로·스트림 close 경로 공통). */
    private void closeChannelRecycle(SftpClient sftp, ClientSession session, boolean ok) {
        if (sftp != null) {
            try {
                sftp.close();
            } catch (IOException ignore) {
                // best-effort
            }
        }
        recycleSession(session, ok);
    }

    /** 새 세션을 열고 현재 자격증명으로 인증한다(풀의 creator 이자 비풀 모드의 직접 경로). */
    private ClientSession openSession() {
        try {
            ClientSession session =
                    client.connect(username, host, port).verify(connectTimeout).getSession();
            boolean authed = false;
            try {
                SftpCredentials creds = credentialProvider.current();
                if (creds.hasPassword()) {
                    session.addPasswordIdentity(creds.password());
                }
                for (KeyPair kp : creds.keyPairs()) {
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
        if (pool != null) {
            try {
                pool.close(); // 유휴 세션 일괄 정리
            } catch (Exception ignore) {
                // best-effort
            }
        }
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

    /** 세션/SFTP 채널을 물고 있다가 스트림 close 시 세션을 풀에 반납(또는 닫음)하는 래퍼(load/loadRange 지연 스트리밍용). */
    private static final class SessionBoundInputStream extends FilterInputStream {
        private final SftpClient sftp;
        private final ClientSession session;
        private final SftpFileStorage owner;

        SessionBoundInputStream(InputStream in, SftpClient sftp, ClientSession session, SftpFileStorage owner) {
            super(in);
            this.sftp = sftp;
            this.session = session;
            this.owner = owner;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                // 스트림 정상 소비/조기 종료 모두 세션은 재사용 가능(다음 대여 시 isOpen 으로 재검증).
                owner.closeChannelRecycle(sftp, session, true);
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
