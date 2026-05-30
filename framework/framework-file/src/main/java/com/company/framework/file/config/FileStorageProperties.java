package com.company.framework.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

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

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public static class Storage {
        private String type = "local";
        private String basePath = "./uploads";
        private long maxSize = 10 * 1024 * 1024;
        private List<String> allowedExtensions =
                List.of("jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "hwp", "txt", "csv", "zip");
        private S3 s3 = new S3();

        // 보안: 실행 가능/위험 확장자는 항상 차단
        public static final Set<String> BLOCKED =
                Set.of("exe","bat","cmd","sh","jsp","jspx","php","asp","aspx","js","html","htm","war","jar","com","msi","dll");

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }
        public long getMaxSize() { return maxSize; }
        public void setMaxSize(long maxSize) { this.maxSize = maxSize; }
        public List<String> getAllowedExtensions() { return allowedExtensions; }
        public void setAllowedExtensions(List<String> v) { this.allowedExtensions = v; }
        public S3 getS3() { return s3; }
        public void setS3(S3 s3) { this.s3 = s3; }
    }

    public static class S3 {
        private String bucket;
        private String region = "ap-northeast-2";
        private String endpoint;
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    }
}
