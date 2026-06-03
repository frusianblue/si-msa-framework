package com.company.framework.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ImageFormat 출력 화이트리스트")
class ImageFormatTest {

    @Test
    @DisplayName("느슨한 이름 매칭")
    void fromName() {
        assertThat(ImageFormat.fromName("jpg")).isEqualTo(ImageFormat.JPEG);
        assertThat(ImageFormat.fromName("JPEG")).isEqualTo(ImageFormat.JPEG);
        assertThat(ImageFormat.fromName(" Png ")).isEqualTo(ImageFormat.PNG);
        assertThat(ImageFormat.fromName("webp")).isNull();
        assertThat(ImageFormat.fromName(null)).isNull();
    }

    @Test
    @DisplayName("메타 속성(mime/확장자/손실)")
    void attributes() {
        assertThat(ImageFormat.JPEG.mimeType()).isEqualTo("image/jpeg");
        assertThat(ImageFormat.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageFormat.JPEG.imageIoName()).isEqualTo("jpeg");
        assertThat(ImageFormat.JPEG.isLossy()).isTrue();

        assertThat(ImageFormat.PNG.mimeType()).isEqualTo("image/png");
        assertThat(ImageFormat.PNG.isLossy()).isFalse();
    }
}
