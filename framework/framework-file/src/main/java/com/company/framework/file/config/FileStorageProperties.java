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
