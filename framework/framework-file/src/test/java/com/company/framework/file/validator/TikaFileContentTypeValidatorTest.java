package com.company.framework.file.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import com.company.framework.file.config.FileStorageProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Tika 매직넘버 기반 콘텐츠 검증 단위 테스트. */
class TikaFileContentTypeValidatorTest {

    private final TikaFileContentTypeValidator validator =
            new TikaFileContentTypeValidator(new FileStorageProperties.Validation().getBlockedContentTypes());

    // PNG 8바이트 시그니처(매직넘버)
    private static final byte[] PNG_MAGIC = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    @Test
    @DisplayName("실제 PNG → image/png 로 검출되어 통과하고 신뢰 MIME 을 반환한다")
    void allowsRealPng() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", PNG_MAGIC);
        assertThat(validator.resolveAndValidate(file)).isEqualTo("image/png");
    }

    @Test
    @DisplayName("HTML 본문을 .png 로 위장 → 실제 콘텐츠(text/html) 검출되어 차단된다")
    void blocksHtmlDisguisedAsPng() {
        byte[] html = "<html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", html);

        assertThatThrownBy(() -> validator.resolveAndValidate(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("text/html");
    }
}
