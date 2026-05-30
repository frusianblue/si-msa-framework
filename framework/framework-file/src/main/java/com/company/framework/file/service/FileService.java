package com.company.framework.file.service;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.util.SecureUtils;
import com.company.framework.file.config.FileStorageProperties;
import com.company.framework.file.domain.FileMetadata;
import com.company.framework.file.dto.FileMetaDto;
import com.company.framework.file.mapper.FileMapper;
import com.company.framework.file.storage.FileStorage;
import com.company.framework.file.storage.StoredFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 업로드 검증(크기/확장자/위험확장자) → 저장소 저장 → 메타 기록.
 * 다운로드/삭제는 메타 조회 후 저장소 위임.
 */
public class FileService {

    private final FileStorage storage;
    private final FileMapper fileMapper;
    private final FileStorageProperties props;

    public FileService(FileStorage storage, FileMapper fileMapper, FileStorageProperties props) {
        this.storage = storage;
        this.fileMapper = fileMapper;
        this.props = props;
    }

    @Transactional
    public FileMetaDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "업로드할 파일이 없습니다.");
        }
        var cfg = props.getStorage();
        if (file.getSize() > cfg.getMaxSize()) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT,
                    "허용 크기를 초과했습니다(최대 " + (cfg.getMaxSize() / 1024 / 1024) + "MB).");
        }
        String safeName = SecureUtils.sanitizeFileName(file.getOriginalFilename());
        String ext = extOf(safeName);
        if (FileStorageProperties.Storage.BLOCKED.contains(ext)) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "허용되지 않는 파일 형식입니다: " + ext);
        }
        if (!cfg.getAllowedExtensions().contains(ext)) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "허용되지 않는 확장자입니다: " + ext);
        }

        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store(in, safeName, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("업로드 처리 실패", e);
        }

        FileMetadata meta = new FileMetadata();
        meta.setOriginalName(safeName);
        meta.setStoredPath(stored.storedPath());
        meta.setContentType(file.getContentType());
        meta.setSize(file.getSize());
        meta.setStorageType(storage.type());
        fileMapper.insert(meta);   // 감사필드 자동주입

        return toDto(meta);
    }

    @Transactional(readOnly = true)
    public FileMetadata getMeta(Long id) {
        return fileMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.NOT_FOUND, "파일 메타를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public InputStream download(FileMetadata meta) {
        return storage.load(meta.getStoredPath());
    }

    @Transactional
    public void delete(Long id) {
        FileMetadata meta = getMeta(id);
        storage.delete(meta.getStoredPath());
        fileMapper.delete(id);
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
