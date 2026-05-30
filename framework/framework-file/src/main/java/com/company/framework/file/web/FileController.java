package com.company.framework.file.web;

import com.company.framework.core.aspect.AuditLog;
import com.company.framework.core.response.ApiResponse;
import com.company.framework.file.domain.FileMetadata;
import com.company.framework.file.dto.FileMetaDto;
import com.company.framework.file.service.FileService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditLog(action = "FILE_UPLOAD", target = "FILE")
    public ApiResponse<FileMetaDto> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(fileService.upload(file), "업로드되었습니다.");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FileMetadata meta = fileService.getMeta(id);
        Resource body = new InputStreamResource(fileService.download(meta));
        String encoded = URLEncoder.encode(meta.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String contentType =
                meta.getContentType() != null ? meta.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(body);
    }

    @GetMapping("/{id}/meta")
    public ApiResponse<FileMetaDto> meta(@PathVariable Long id) {
        return ApiResponse.ok(fileService.toDto(fileService.getMeta(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "FILE_DELETE", target = "FILE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ApiResponse.ok();
    }
}
