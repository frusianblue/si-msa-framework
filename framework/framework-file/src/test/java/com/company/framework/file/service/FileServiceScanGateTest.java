package com.company.framework.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.framework.core.error.BusinessException;
import com.company.framework.file.config.FileStorageProperties;
import com.company.framework.file.mapper.FileMapper;
import com.company.framework.file.scan.FileScanner;
import com.company.framework.file.scan.NoOpFileScanner;
import com.company.framework.file.scan.ScanResult;
import com.company.framework.file.storage.FileStorage;
import com.company.framework.file.storage.StoredFile;
import com.company.framework.file.validator.FileContentTypeValidator;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 안티바이러스 게이트 검증: 감염 시 저장/메타기록을 차단하고, 정상 시 진행한다.
 */
class FileServiceScanGateTest {

    private final FileStorage storage = Mockito.mock(FileStorage.class);
    private final FileMapper mapper = Mockito.mock(FileMapper.class);
    private final FileContentTypeValidator validator = Mockito.mock(FileContentTypeValidator.class);
    private final FileStorageProperties props = new FileStorageProperties();

    private MultipartFile pngFile() throws Exception {
        MultipartFile f = Mockito.mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(false);
        when(f.getSize()).thenReturn(10L);
        when(f.getOriginalFilename()).thenReturn("a.png");
        when(f.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(new byte[] {1, 2, 3}));
        return f;
    }

    @Test
    @DisplayName("감염 탐지 → BusinessException, 저장/메타기록 안 함")
    void infectedRejected() throws Exception {
        when(validator.resolveAndValidate(any())).thenReturn("image/png");
        FileScanner infected = new FileScanner() {
            @Override
            public ScanResult scan(java.io.InputStream c, long s, String n) {
                return ScanResult.infected("Test.Sig", "clamav");
            }

            @Override
            public String type() {
                return "clamav";
            }
        };
        FileService service = new FileService(storage, mapper, props, validator, infected);

        assertThatThrownBy(() -> service.upload(pngFile())).isInstanceOf(BusinessException.class);
        verify(storage, never()).store(any(), anyString(), anyString(), anyLong());
        verify(mapper, never()).insert(any());
    }

    @Test
    @DisplayName("정상(NoOp) → 저장·메타기록 진행")
    void cleanProceeds() throws Exception {
        when(validator.resolveAndValidate(any())).thenReturn("image/png");
        when(storage.store(any(), anyString(), anyString(), anyLong()))
                .thenReturn(new StoredFile("2026/01/01/uuid.png", "a.png", "image/png", 10));
        when(storage.type()).thenReturn("local");
        FileService service = new FileService(storage, mapper, props, validator, new NoOpFileScanner());

        assertThat(service.upload(pngFile())).isNotNull();
        verify(storage).store(any(), anyString(), anyString(), anyLong());
        verify(mapper).insert(any());
    }
}
