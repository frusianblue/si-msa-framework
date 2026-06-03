package com.company.framework.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.file.scan.ClamavFileScanner;
import com.company.framework.file.scan.FileScanner;
import com.company.framework.file.scan.NoOpFileScanner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 안티바이러스 스캐너 빈 선택(백오프) 검증: 기본 NoOp, scan.type=clamav 옵트인 시 ClamAV.
 */
class FileScannerFeaturesTest {

    private final FileStorageAutoConfiguration config = new FileStorageAutoConfiguration();

    @Test
    @DisplayName("scan.enabled=false(기본) → NoOp 스캐너")
    void noOpByDefault() {
        FileScanner s = config.fileScanner(new FileStorageProperties());
        assertThat(s).isInstanceOf(NoOpFileScanner.class);
        assertThat(s.type()).isEqualTo("none");
    }

    @Test
    @DisplayName("scan.enabled=true + type=clamav → ClamAV 스캐너")
    void clamavWhenOptedIn() {
        FileStorageProperties props = new FileStorageProperties();
        props.getScan().setEnabled(true);
        props.getScan().setType("clamav");
        FileScanner s = config.fileScanner(props);
        assertThat(s).isInstanceOf(ClamavFileScanner.class);
        assertThat(s.type()).isEqualTo("clamav");
    }

    @Test
    @DisplayName("scan.enabled=true + type=none → NoOp(명시적 비활성)")
    void noOpWhenTypeNone() {
        FileStorageProperties props = new FileStorageProperties();
        props.getScan().setEnabled(true);
        props.getScan().setType("none");
        assertThat(config.fileScanner(props)).isInstanceOf(NoOpFileScanner.class);
    }
}
