package com.company.framework.file.s3;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.file.storage.FileStorage;
import com.company.framework.file.storage.StoredFile;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS S3(또는 endpoint 지정 시 MinIO 등 호환) 저장소.
 * 키: yyyy/MM/dd/{uuid}.{ext}
 */
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final String bucket;

    public S3FileStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public StoredFile store(InputStream content, String originalName, String contentType, long size) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String ext = extOf(originalName);
        String key = datePath + "/" + UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
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

    private String extOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return (dot < 0) ? "" : name.substring(dot + 1).toLowerCase();
    }
}
