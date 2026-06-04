package com.company.framework.file.config;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   file:
 *     enabled: true
 *     storage:
 *       type: local           # local | nas | s3
 *       base-path: ./uploads  # local/nas 공통(개발=로컬, 운영=/mnt/nas/... )
 *       max-size: 10485760    # 10MB
 *       allowed-extensions: [jpg, png, pdf, docx, xlsx, hwp, zip]
 *       s3:
 *         bucket: my-bucket
 *         region: ap-northeast-2
 *         endpoint:           # MinIO 등 호환 스토리지용(선택)
 */
@ConfigurationProperties(prefix = "framework.file")
public class FileStorageProperties {
    private boolean enabled = true;
    private Storage storage = new Storage();
    private Validation validation = new Validation();
    private Scan scan = new Scan();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Validation getValidation() {
        return validation;
    }

    public void setValidation(Validation validation) {
        this.validation = validation;
    }

    public Scan getScan() {
        return scan;
    }

    public void setScan(Scan scan) {
        this.scan = scan;
    }

    /**
     * 업로드 콘텐츠 검증 설정.
     *  - content-type-detection=true 면 Tika 로 실제 바이트(매직넘버) 기반 MIME 을 검출해 위장 업로드를 차단하고,
     *    메타에는 클라이언트가 보낸 값이 아닌 검출된 신뢰 MIME 을 기록한다. (tika-core 의존 필요 — 옵트인)
     */
    public static class Validation {
        private boolean contentTypeDetection = false;
        private boolean enforceExtensionMatch = false; // true 면 선언 확장자와 검출 MIME 의 계열 정합까지 강제(content-type-detection 필요)
        private Set<String> blockedContentTypes = new LinkedHashSet<>(List.of(
                "application/x-dosexec",
                "application/x-msdownload",
                "application/x-executable",
                "application/x-elf",
                "application/x-sharedlib",
                "application/x-mach-binary",
                "application/x-httpd-php",
                "application/x-php",
                "application/x-sh",
                "application/x-shellscript",
                "text/x-shellscript",
                "application/java-archive",
                "application/javascript",
                "text/javascript",
                "text/html",
                "application/xhtml+xml"));

        public boolean isContentTypeDetection() {
            return contentTypeDetection;
        }

        public void setContentTypeDetection(boolean contentTypeDetection) {
            this.contentTypeDetection = contentTypeDetection;
        }

        public boolean isEnforceExtensionMatch() {
            return enforceExtensionMatch;
        }

        public void setEnforceExtensionMatch(boolean enforceExtensionMatch) {
            this.enforceExtensionMatch = enforceExtensionMatch;
        }

        public Set<String> getBlockedContentTypes() {
            return blockedContentTypes;
        }

        public void setBlockedContentTypes(Set<String> blockedContentTypes) {
            this.blockedContentTypes = blockedContentTypes;
        }
    }

    public static class Storage {
        private String type = "local";
        private String basePath = "./uploads";
        private long maxSize = 10 * 1024 * 1024;
        private boolean encrypt = false; // true 면 저장소에 AES 암호화하여 보관(at-rest)
        private Duration presignedGetTtl = Duration.ofMinutes(5); // presigned GET 만료(S3)
        private Duration presignedPutTtl = Duration.ofMinutes(10); // presigned PUT 만료(S3)
        private List<String> allowedExtensions = List.of(
                "jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "hwp", "txt", "csv",
                "zip");
        private S3 s3 = new S3();
        private Sftp sftp = new Sftp();

        // 보안: 실행 가능/위험 확장자는 항상 차단
        public static final Set<String> BLOCKED = Set.of(
                "exe", "bat", "cmd", "sh", "jsp", "jspx", "php", "asp", "aspx", "js", "html", "htm", "war", "jar",
                "com", "msi", "dll");

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public boolean isEncrypt() {
            return encrypt;
        }

        public void setEncrypt(boolean encrypt) {
            this.encrypt = encrypt;
        }

        public Duration getPresignedGetTtl() {
            return presignedGetTtl;
        }

        public void setPresignedGetTtl(Duration presignedGetTtl) {
            this.presignedGetTtl = presignedGetTtl;
        }

        public Duration getPresignedPutTtl() {
            return presignedPutTtl;
        }

        public void setPresignedPutTtl(Duration presignedPutTtl) {
            this.presignedPutTtl = presignedPutTtl;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> v) {
            this.allowedExtensions = v;
        }

        public S3 getS3() {
            return s3;
        }

        public void setS3(S3 s3) {
            this.s3 = s3;
        }

        public Sftp getSftp() {
            return sftp;
        }

        public void setSftp(Sftp sftp) {
            this.sftp = sftp;
        }
    }

    public static class S3 {
        private String bucket;
        private String region = "ap-northeast-2";
        private String endpoint;

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }

    /**
     * SFTP(원격 SSH) 저장소 설정(framework-file-sftp 모듈, storage.type=sftp).
     * 인증은 password 또는 private-key-path 중 하나 이상(둘 다 주면 키 우선 시도 후 비번 폴백).
     * strict-host-key-checking=true(기본) 면 known-hosts 에 없는 서버를 거부(fail-closed) — 운영 권장.
     */
    public static class Sftp {
        private String host;
        private int port = 22;
        private String username;
        private String password;
        private String privateKeyPath;
        private String privateKeyPassphrase;
        private String baseDir = ""; // 비우면 서버 홈 상대. 예: /home/app/upload
        private boolean strictHostKeyChecking = true;
        private String knownHostsPath; // 비우면 ~/.ssh/known_hosts
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration authTimeout = Duration.ofSeconds(10);
        private Pool pool = new Pool();
        private KeyRotation keyRotation = new KeyRotation();

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
        }

        public String getPrivateKeyPassphrase() {
            return privateKeyPassphrase;
        }

        public void setPrivateKeyPassphrase(String privateKeyPassphrase) {
            this.privateKeyPassphrase = privateKeyPassphrase;
        }

        public String getBaseDir() {
            return baseDir;
        }

        public void setBaseDir(String baseDir) {
            this.baseDir = baseDir;
        }

        public boolean isStrictHostKeyChecking() {
            return strictHostKeyChecking;
        }

        public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
            this.strictHostKeyChecking = strictHostKeyChecking;
        }

        public String getKnownHostsPath() {
            return knownHostsPath;
        }

        public void setKnownHostsPath(String knownHostsPath) {
            this.knownHostsPath = knownHostsPath;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getAuthTimeout() {
            return authTimeout;
        }

        public void setAuthTimeout(Duration authTimeout) {
            this.authTimeout = authTimeout;
        }

        public Pool getPool() {
            return pool;
        }

        public void setPool(Pool pool) {
            this.pool = pool;
        }

        public KeyRotation getKeyRotation() {
            return keyRotation;
        }

        public void setKeyRotation(KeyRotation keyRotation) {
            this.keyRotation = keyRotation;
        }

        /**
         * SFTP 세션 풀(후속·옵트인). 기본 비활성 = 작업마다 세션 개폐(기존 동작). 켜면 인증된 세션을 재사용해
         * 고처리량에서 SSH/TCP/인증 핸드셰이크 비용을 줄인다. {@code max-lifetime} 은 키 회전 전파에 중요 —
         * 옛 키로 인증된 장수 세션을 강제 교체해 신규 세션이 현재 자격증명으로 재인증되게 한다.
         */
        public static class Pool {
            private boolean enabled = false;
            private int maxTotal = 8; // 동시 보유(대여+유휴) 상한
            private Duration maxWait = Duration.ofSeconds(10); // 풀 고갈 시 대여 대기 상한(초과 시 실패)
            private Duration maxIdle = Duration.ofMinutes(5); // 유휴 만료(누적 방지)
            private Duration maxLifetime = Duration.ofMinutes(30); // 생성 후 수명(키 회전 전파)

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getMaxTotal() {
                return maxTotal;
            }

            public void setMaxTotal(int maxTotal) {
                this.maxTotal = maxTotal;
            }

            public Duration getMaxWait() {
                return maxWait;
            }

            public void setMaxWait(Duration maxWait) {
                this.maxWait = maxWait;
            }

            public Duration getMaxIdle() {
                return maxIdle;
            }

            public void setMaxIdle(Duration maxIdle) {
                this.maxIdle = maxIdle;
            }

            public Duration getMaxLifetime() {
                return maxLifetime;
            }

            public void setMaxLifetime(Duration maxLifetime) {
                this.maxLifetime = maxLifetime;
            }
        }

        /**
         * SFTP 키 회전(후속·옵트인). 기본 비활성 = 기동 시 1회 로드(기존 동작). 켜면 {@code private-key-path} 파일의
         * 변경(mtime+size)을 {@code check-interval} 마다 감지해 자격증명을 다시 읽는다. 새 세션부터 새 키로 인증되며,
         * 풀을 함께 쓰면 {@code pool.max-lifetime} 으로 기존 세션도 점진 교체된다. (재로드 실패 시 기존 키 유지·다음 주기 재시도.)
         */
        public static class KeyRotation {
            private boolean enabled = false;
            private Duration checkInterval = Duration.ofSeconds(60); // 파일 변경 확인 최소 간격

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public Duration getCheckInterval() {
                return checkInterval;
            }

            public void setCheckInterval(Duration checkInterval) {
                this.checkInterval = checkInterval;
            }
        }
    }

    /**
     * 업로드 안티바이러스 스캔 설정.
     *  - enabled=false(기본) → NoOp(통과).
     *  - type=clamav → ClamAV {@code clamd} 에 INSTREAM 으로 검사(순수 소켓, 새 의존성 0).
     *  - fail-open=false(기본) → 데몬 장애 시 업로드 거부(fail-closed, 보안 우선).
     */
    public static class Scan {
        private boolean enabled = false;
        private String type = "none"; // none | clamav
        private String host = "localhost";
        private int port = 3310;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 30000;
        private int maxChunkSize = 1024 * 1024; // 1MB
        private boolean failOpen = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getMaxChunkSize() {
            return maxChunkSize;
        }

        public void setMaxChunkSize(int maxChunkSize) {
            this.maxChunkSize = maxChunkSize;
        }

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }
    }
}
