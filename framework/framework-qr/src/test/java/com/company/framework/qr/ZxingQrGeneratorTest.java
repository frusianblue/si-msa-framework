package com.company.framework.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QR 생성 검증 — ZXing 인코딩 + ImageIO 렌더링을 실제로 돌리고, 만든 PNG 를 다시 ZXing 리더로 디코딩해
 * payload 가 보존됨을 왕복으로 입증한다(디코드도 zxing-core 만 사용 — RGBLuminanceSource).
 */
class ZxingQrGeneratorTest {

    private final QrGenerator gen = new ZxingQrGenerator(QrSpec.defaults(), 1024);

    private static String decode(byte[] png) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels)));
        Result result = new QRCodeReader().decode(bitmap);
        return result.getText();
    }

    @Test
    @DisplayName("toPng: 유효 PNG(매직바이트) + 디코딩하면 원문 복원")
    void roundTripPng() throws Exception {
        String content = "https://example.com/verify?id=abc123";
        byte[] png = gen.toPng(content);

        // PNG 시그니처(89 50 4E 47 0D 0A 1A 0A)
        assertThat(png.length).isGreaterThan(8);
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(png[1] & 0xFF).isEqualTo(0x50);
        assertThat(png[2] & 0xFF).isEqualTo(0x4E);
        assertThat(png[3] & 0xFF).isEqualTo(0x47);

        assertThat(decode(png)).isEqualTo(content);
    }

    @Test
    @DisplayName("UTF-8 한글/이모지 URL 도 왕복 보존")
    void roundTripUtf8() throws Exception {
        String content = "https://example.com/문서?값=한글-✓";
        byte[] png = gen.generate(content, QrSpec.builder().charset("UTF-8").build());
        assertThat(decode(png)).isEqualTo(content);
    }

    @Test
    @DisplayName("otpauth:// URI(MFA 등록용) 왕복 보존")
    void roundTripOtpauth() throws Exception {
        String uri =
                "otpauth://totp/si-msa:alice?secret=JBSWY3DPEHPK3PXP&issuer=si-msa&algorithm=SHA1&digits=6&period=30";
        byte[] png = gen.toPng(uri);
        assertThat(decode(png)).isEqualTo(uri);
    }

    @Test
    @DisplayName("출력 PNG 한 변은 요청 sizePx 이상(ZXing 이 모듈 배수로 올림)")
    void sizeAtLeastRequested() throws Exception {
        byte[] png = gen.generate("size-check", QrSpec.builder().sizePx(300).build());
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getWidth()).isGreaterThanOrEqualTo(300);
        assertThat(img.getWidth()).isEqualTo(img.getHeight());
    }

    @Test
    @DisplayName("빈 내용 → BusinessException(EMPTY_CONTENT)")
    void emptyContent() {
        assertThatThrownBy(() -> gen.toPng(""))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(QrErrorCode.EMPTY_CONTENT);
        assertThatThrownBy(() -> gen.toPng(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("길이 상한 초과 → BusinessException(CONTENT_TOO_LONG)")
    void tooLong() {
        QrGenerator tiny = new ZxingQrGenerator(QrSpec.defaults(), 8);
        assertThatThrownBy(() -> tiny.toPng("123456789"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(QrErrorCode.CONTENT_TOO_LONG);
    }

    @Test
    @DisplayName("길이 상한 0 이하면 검사 생략(ZXing 용량 한계에만 의존)")
    void noLengthGuard() {
        QrGenerator unlimited = new ZxingQrGenerator(QrSpec.defaults(), 0);
        assertThat(unlimited.toPng("a".repeat(200))).isNotEmpty();
    }
}
