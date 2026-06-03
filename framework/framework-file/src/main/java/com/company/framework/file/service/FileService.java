package com.company.framework.file.service;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.util.SecureUtils;
import com.company.framework.file.config.FileStorageProperties;
import com.company.framework.file.domain.FileMetadata;
import com.company.framework.file.dto.FileMetaDto;
import com.company.framework.file.mapper.FileMapper;
import com.company.framework.file.scan.FileScanner;
import com.company.framework.file.scan.ScanResult;
import com.company.framework.file.storage.FileStorage;
import com.company.framework.file.storage.PresignedUrlStorage;
import com.company.framework.file.storage.RangeReadableStorage;
import com.company.framework.file.storage.StoredFile;
import com.company.framework.file.validator.FileContentTypeValidator;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 검증(크기/확장자/위험확장자/콘텐츠/안티바이러스) → 저장소 저장 → 메타 기록.
 * 다운로드/삭제는 메타 조회 후 저장소 위임. Range·presigned 는 저장소 capability 에 위임.
 */
public class FileService {

    private final FileStorage storage;
    private final FileMapper fileMapper;
    private final FileStorageProperties props;
    private final FileContentTypeValidator contentTypeValidator;
    private final FileScanner scanner;

    public FileService(
            FileStorage storage,
            FileMapper fileMapper,
            FileStorageProperties props,
            FileContentTypeValidator contentTypeValidator,
            FileScanner scanner) {
        this.storage = storage;
        this.fileMapper = fileMapper;
        this.props = props;
        this.contentTypeValidator = contentTypeValidator;
        this.scanner = scanner;
    }

    @Transactional
    public FileMetaDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "업로드할 파일이 없습니다.");
        }
        var cfg = props.getStorage();
        if (file.getSize() > cfg.getMaxSize()) {
            throw new BusinessException(
                    ErrorCode.Common.INVALID_INPUT, "허용 크기를 초과했습니다(최대 " + (cfg.getMaxSize() / 1024 / 1024) + "MB).");
        }
        String safeName = SecureUtils.sanitizeFileName(file.getOriginalFilename());
        String ext = extOf(safeName);
        if (FileStorageProperties.Storage.BLOCKED.contains(ext)) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "허용되지 않는 파일 형식입니다: " + ext);
        }
        if (!cfg.getAllowedExtensions().contains(ext)) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "허용되지 않는 확장자입니다: " + ext);
        }

        // 실제 바이트 기반 콘텐츠 검증(옵트인) → 신뢰 가능한 contentType 획득(헤더 위조 무시)
        String resolvedContentType = contentTypeValidator.resolveAndValidate(file);

        // 안티바이러스 스캔(옵트인) — 저장 전 게이트. 별도 스트림으로 검사한다.
        scan(file, safeName);

        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store(in, safeName, resolvedContentType, file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("업로드 처리 실패", e);
        }

        FileMetadata meta = new FileMetadata();
        meta.setOriginalName(safeName);
        meta.setStoredPath(stored.storedPath());
        meta.setContentType(resolvedContentType);
        meta.setSize(file.getSize());
        meta.setStorageType(storage.type());
        fileMapper.insert(meta); // 감사필드 자동주입

        return toDto(meta);
    }

    private void scan(MultipartFile file, String safeName) {
        if (scanner == null) return;
        try (InputStream scanIn = file.getInputStream()) {
            ScanResult result = scanner.scan(scanIn, file.getSize(), safeName);
            if (result.infected()) {
                throw new BusinessException(
                        ErrorCode.Common.INVALID_INPUT, "악성코드가 탐지되어 업로드를 거부했습니다(" + result.signature() + ").");
            }
        } catch (IOException e) {
            throw new IllegalStateException("바이러스 검사 처리 실패", e);
        }
    }

    @Transactional(readOnly = true)
    public FileMetadata getMeta(Long id) {
        return fileMapper
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.NOT_FOUND, "파일 메타를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public InputStream download(FileMetadata meta) {
        return storage.load(meta.getStoredPath());
    }

    /** 저장소가 Range(부분) 읽기를 지원하는지. 암호화 저장소는 false. */
    public boolean supportsRange() {
        return storage instanceof RangeReadableStorage;
    }

    /** 부분 다운로드 — {@link #supportsRange()} 가 true 일 때만 호출. */
    @Transactional(readOnly = true)
    public InputStream downloadRange(FileMetadata meta, long start, long endInclusive) {
        return ((RangeReadableStorage) storage).loadRange(meta.getStoredPath(), start, endInclusive);
    }

    /** 실제 콘텐츠 길이(Range 지원 저장소는 저장된 바이트 기준, 아니면 메타의 평문 크기). */
    @Transactional(readOnly = true)
    public long contentLength(FileMetadata meta) {
        if (storage instanceof RangeReadableStorage r) {
            long len = r.contentLength(meta.getStoredPath());
            if (len >= 0) return len;
        }
        return meta.getSize();
    }

    /** presigned URL 발급 저장소(S3)면 반환, 아니면 null. */
    public PresignedUrlStorage presignedStorageOrNull() {
        return storage instanceof PresignedUrlStorage p ? p : null;
    }

    @Transactional
    public void delete(Long id) {
        FileMetadata meta = getMeta(id);
        storage.delete(meta.getStoredPath());
        fileMapper.delete(id);
    }

    /**
     * presigned PUT 으로 클라이언트가 저장소에 직접 올린 객체의 메타를 등록한다(서버 본문 비경유).
     *
     * <p><b>주의</b>: 서버가 본문 바이트를 보지 못하므로 콘텐츠 타입 검증·안티바이러스 스캔을 적용할 수 없다.
     * 파일명 기반 확장자 화이트리스트만 강제한다. 신뢰 경계 밖 업로드라면 비동기 후처리 스캔을 별도로 둘 것.
     */
    @Transactional
    public FileMetaDto registerExternalUpload(String storedPath, String originalName, String contentType, long size) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "저장 경로가 없습니다.");
        }
        String safeName = SecureUtils.sanitizeFileName(originalName);
        String ext = extOf(safeName);
        if (FileStorageProperties.Storage.BLOCKED.contains(ext)
                || !props.getStorage().getAllowedExtensions().contains(ext)) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "허용되지 않는 확장자입니다: " + ext);
        }
        FileMetadata meta = new FileMetadata();
        meta.setOriginalName(safeName);
        meta.setStoredPath(storedPath);
        meta.setContentType(contentType);
        meta.setSize(size);
        meta.setStorageType(storage.type());
        fileMapper.insert(meta);
        return toDto(meta);
    }

    public FileMetaDto toDto(FileMetadata m) {
        return new FileMetaDto(m.getId(), m.getOriginalName(), m.getContentType(), m.getSize(), m.getStorageType());
    }

    private String extOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return (dot < 0) ? "" : name.substring(dot + 1).toLowerCase();
    }
}
