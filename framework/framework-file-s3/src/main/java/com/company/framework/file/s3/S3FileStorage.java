package com.company.framework.file.s3;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.file.storage.FileStorage;
import com.company.framework.file.storage.PresignedUrl;
import com.company.framework.file.storage.PresignedUrlStorage;
import com.company.framework.file.storage.RangeReadableStorage;
import com.company.framework.file.storage.StoredFile;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * AWS S3(또는 endpoint 지정 시 MinIO 등 호환) 저장소.
 * 키: yyyy/MM/dd/{uuid}.{ext}
 *
 * <p>{@link RangeReadableStorage}(부분 다운로드)와 {@link PresignedUrlStorage}(사전서명 URL)를 구현한다.
 * presigner 가 주입되지 않으면 presigned 발급은 비활성(NPE 대신 명확한 예외).
 */
public class S3FileStorage implements FileStorage, RangeReadableStorage, PresignedUrlStorage {

    private final S3Client s3;
    private final S3Presigner presigner; // null 가능(presigned 미사용 환경)
    private final String bucket;

    public S3FileStorage(S3Client s3, String bucket) {
        this(s3, null, bucket);
    }

    public S3FileStorage(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public StoredFile store(InputStream content, String originalName, String contentType, long size) {
        String key = newKey(originalName);
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build();
        s3.putObject(req, RequestBody.fromInputStream(content, size));
        return new StoredFile(key, originalName, contentType, size);
    }

    @Override
    public InputStream load(String storedPath) {
        try {
            return s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(storedPath).build());
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.Common.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
    }

    @Override
    public void delete(String storedPath) {
        s3.deleteObject(
                DeleteObjectRequest.builder().bucket(bucket).key(storedPath).build());
    }

    @Override
    public String type() {
        return "s3";
    }

    // --- RangeReadableStorage ---

    @Override
    public long contentLength(String storedPath) {
        try {
            HeadObjectRequest head =
                    HeadObjectRequest.builder().bucket(bucket).key(storedPath).build();
            return s3.headObject(head).contentLength();
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.Common.NOT_FOUND, "파일을 찾을 수 없습니다.");
        } catch (RuntimeException e) {
            return -1;
        }
    }

    @Override
    public InputStream loadRange(String storedPath, long start, long endInclusive) {
        try {
            GetObjectRequest req = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(storedPath)
                    .range("bytes=" + start + "-" + endInclusive)
                    .build();
            return s3.getObject(req);
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.Common.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
    }

    // --- PresignedUrlStorage ---

    @Override
    public PresignedUrl presignGet(String storedPath, Duration ttl) {
        S3Presigner p = requirePresigner();
        GetObjectRequest get =
                GetObjectRequest.builder().bucket(bucket).key(storedPath).build();
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(get)
                .build();
        var presigned = p.presignGetObject(presign);
        return new PresignedUrl(
                "GET", presigned.url().toString(), storedPath, Instant.now().plus(ttl), Map.of());
    }

    @Override
    public PresignedUrl presignPut(String originalName, String contentType, Duration ttl) {
        S3Presigner p = requirePresigner();
        String key = newKey(originalName);
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presign = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(put)
                .build();
        var presigned = p.presignPutObject(presign);
        return new PresignedUrl(
                "PUT", presigned.url().toString(), key, Instant.now().plus(ttl), Map.of("Content-Type", contentType));
    }

    private S3Presigner requirePresigner() {
        if (presigner == null) {
            throw new BusinessException(
                    ErrorCode.Common.INTERNAL_ERROR, "S3Presigner 가 설정되지 않아 presigned URL 을 발급할 수 없습니다.");
        }
        return presigner;
    }

    private String newKey(String originalName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String ext = extOf(originalName);
        return datePath + "/" + UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
    }

    private String extOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return (dot < 0) ? "" : name.substring(dot + 1).toLowerCase();
    }
}
