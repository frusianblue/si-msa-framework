package com.company.framework.file.web;

import com.company.framework.core.aspect.AuditLog;
import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.response.ApiResponse;
import com.company.framework.file.config.FileStorageProperties;
import com.company.framework.file.domain.FileMetadata;
import com.company.framework.file.dto.FileMetaDto;
import com.company.framework.file.dto.PresignedCompleteRequest;
import com.company.framework.file.service.FileService;
import com.company.framework.file.storage.ByteRange;
import com.company.framework.file.storage.PresignedUrl;
import com.company.framework.file.storage.PresignedUrlStorage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;
    private final FileStorageProperties props;

    public FileController(FileService fileService, FileStorageProperties props) {
        this.fileService = fileService;
        this.props = props;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditLog(action = "FILE_UPLOAD", target = "FILE")
    public ApiResponse<FileMetaDto> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(fileService.upload(file), "업로드되었습니다.");
    }

    /**
     * 다운로드. 저장소가 Range(부분) 읽기를 지원하고 유효한 {@code Range} 헤더가 오면 206 Partial Content 로
     * 응답하고(영상/대용량 스트리밍), 아니면 200 전체로 응답한다. 암호화 저장소는 Range 미지원 → 항상 전체.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(
            @PathVariable Long id, @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        FileMetadata meta = fileService.getMeta(id);
        String encoded = URLEncoder.encode(meta.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String disposition = "attachment; filename*=UTF-8''" + encoded;
        String contentType =
                meta.getContentType() != null ? meta.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        if (fileService.supportsRange()) {
            long total = fileService.contentLength(meta);
            Optional<ByteRange> range = ByteRange.parse(rangeHeader, total);
            if (range.isPresent()) {
                ByteRange r = range.get();
                Resource partial =
                        new InputStreamResource(fileService.downloadRange(meta, r.start(), r.endInclusive()));
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_RANGE, r.contentRangeHeader())
                        .header(HttpHeaders.CONTENT_LENGTH, Long.toString(r.length()))
                        .body(partial);
            }
            // Range 헤더가 왔지만 만족 불가 → 416
            if (rangeHeader != null && rangeHeader.trim().startsWith("bytes=") && total > 0) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                        .build();
            }
            // 전체 응답(부분 읽기 지원 표시)
            Resource body = new InputStreamResource(fileService.download(meta));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, Long.toString(total))
                    .body(body);
        }

        Resource body = new InputStreamResource(fileService.download(meta));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(body);
    }

    @GetMapping("/{id}/meta")
    public ApiResponse<FileMetaDto> meta(@PathVariable Long id) {
        return ApiResponse.ok(fileService.toDto(fileService.getMeta(id)));
    }

    /** 기존 객체의 presigned GET URL 발급(S3 류 저장소만). */
    @GetMapping("/{id}/presigned")
    public ApiResponse<PresignedUrl> presignedDownload(@PathVariable Long id) {
        PresignedUrlStorage presigner = requirePresigner();
        FileMetadata meta = fileService.getMeta(id);
        return ApiResponse.ok(
                presigner.presignGet(meta.getStoredPath(), props.getStorage().getPresignedGetTtl()));
    }

    /** 신규 업로드용 presigned PUT URL 발급. 클라이언트가 이 URL 로 직접 PUT 한 뒤 complete 로 메타 등록. */
    @PostMapping("/presigned-upload")
    @AuditLog(action = "FILE_PRESIGN_UPLOAD", target = "FILE")
    public ApiResponse<PresignedUrl> presignedUpload(
            @RequestParam("filename") String filename,
            @RequestParam(value = "contentType", required = false) String contentType) {
        PresignedUrlStorage presigner = requirePresigner();
        String ct =
                (contentType == null || contentType.isBlank()) ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType;
        return ApiResponse.ok(
                presigner.presignPut(filename, ct, props.getStorage().getPresignedPutTtl()));
    }

    /** presigned PUT 업로드 완료 후 메타 등록(서버 본문 비경유 — 콘텐츠/AV 검증 미적용). */
    @PostMapping("/presigned-complete")
    @AuditLog(action = "FILE_PRESIGN_COMPLETE", target = "FILE")
    public ApiResponse<FileMetaDto> presignedComplete(@RequestBody PresignedCompleteRequest req) {
        FileMetaDto dto =
                fileService.registerExternalUpload(req.storedPath(), req.originalName(), req.contentType(), req.size());
        return ApiResponse.ok(dto, "등록되었습니다.");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "FILE_DELETE", target = "FILE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ApiResponse.ok();
    }

    private PresignedUrlStorage requirePresigner() {
        PresignedUrlStorage presigner = fileService.presignedStorageOrNull();
        if (presigner == null) {
            throw new BusinessException(
                    ErrorCode.Common.INVALID_INPUT, "현재 저장소는 presigned URL 을 지원하지 않습니다(S3 에서만 가능).");
        }
        return presigner;
    }
}
