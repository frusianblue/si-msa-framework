package com.company.framework.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultImageProcessor 파이프라인")
class DefaultImageProcessorTest {

    private final ImageProcessor processor = new DefaultImageProcessor();

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("박스 안으로 비율 유지 축소(800x400 → 200 박스 → 200x100)")
    void resizesPreservingAspect() throws Exception {
        byte[] out = processor.process(
                png(800, 400, false),
                ResizeSpec.builder().maxEdge(200).format(ImageFormat.JPEG).build());
        BufferedImage r = decode(out);
        assertThat(r.getWidth()).isEqualTo(200);
        assertThat(r.getHeight()).isEqualTo(100);
    }

    @Test
    @DisplayName("기본은 업스케일 금지 — 작은 원본은 그대로")
    void noUpscaleByDefault() throws Exception {
        BufferedImage r = decode(processor.process(
                png(50, 50, false),
                ResizeSpec.builder().maxEdge(500).format(ImageFormat.PNG).build()));
        assertThat(r.getWidth()).isEqualTo(50);
        assertThat(r.getHeight()).isEqualTo(50);
    }

    @Test
    @DisplayName("allowUpscale=true 면 확대")
    void upscaleWhenAllowed() throws Exception {
        BufferedImage r = decode(processor.process(
                png(50, 50, false),
                ResizeSpec.builder()
                        .maxEdge(500)
                        .allowUpscale(true)
                        .format(ImageFormat.PNG)
                        .build()));
        assertThat(r.getWidth()).isEqualTo(500);
        assertThat(r.getHeight()).isEqualTo(500);
    }

    @Test
    @DisplayName("EXIF orientation=6 보정 → 가로/세로 스왑 후 축소(300x150 → 50x100), 출력 메타 제거")
    void correctsOrientationAndStripsMetadata() throws Exception {
        byte[] src = ExifOrientationTest.withOrientation(ExifOrientationTest.baselineJpeg(300, 150), 6);
        assertThat(ExifOrientation.read(src)).isEqualTo(6);

        byte[] out = processor.process(
                src,
                ResizeSpec.builder()
                        .maxEdge(100)
                        .format(ImageFormat.JPEG)
                        .correctOrientation(true)
                        .build());

        // 리인코딩으로 EXIF(GPS 포함)가 제거된다 → 다시 읽으면 NORMAL.
        assertThat(ExifOrientation.read(out)).isEqualTo(ExifOrientation.NORMAL);
        BufferedImage r = decode(out);
        assertThat(r.getWidth()).isEqualTo(50);
        assertThat(r.getHeight()).isEqualTo(100);
    }

    @Test
    @DisplayName("correctOrientation=false 면 스왑 안 함(300x150 → 100x50)")
    void orientationOffKeepsAxes() throws Exception {
        byte[] src = ExifOrientationTest.withOrientation(ExifOrientationTest.baselineJpeg(300, 150), 6);
        BufferedImage r = decode(processor.process(
                src,
                ResizeSpec.builder()
                        .maxEdge(100)
                        .format(ImageFormat.JPEG)
                        .correctOrientation(false)
                        .build()));
        assertThat(r.getWidth()).isEqualTo(100);
        assertThat(r.getHeight()).isEqualTo(50);
    }

    @Test
    @DisplayName("알파 PNG → JPEG 는 흰 배경으로 평탄화(알파 없음)")
    void flattensAlphaForJpeg() throws Exception {
        BufferedImage r = decode(processor.process(
                png(120, 60, true),
                ResizeSpec.builder().maxEdge(60).format(ImageFormat.JPEG).build()));
        assertThat(r.getColorModel().hasAlpha()).isFalse();
    }

    @Test
    @DisplayName("PNG 출력은 알파 보존")
    void keepsAlphaForPng() throws Exception {
        BufferedImage r = decode(processor.process(
                png(40, 40, true),
                ResizeSpec.builder().maxEdge(20).format(ImageFormat.PNG).build()));
        assertThat(r.getColorModel().hasAlpha()).isTrue();
    }

    @Test
    @DisplayName("thumbnail(maxEdge): 가장 긴 변이 상한 이하")
    void thumbnail() throws Exception {
        BufferedImage r = decode(processor.thumbnail(png(640, 320, false), 100));
        assertThat(Math.max(r.getWidth(), r.getHeight())).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("probe: 디코드 없이 크기/포맷 조회")
    void probe() throws Exception {
        ImageInfo info = processor.probe(png(640, 480, false));
        assertThat(info.width()).isEqualTo(640);
        assertThat(info.height()).isEqualTo(480);
        assertThat(info.formatName()).isNotBlank();
    }

    @Test
    @DisplayName("maxSourcePixels 초과 → IMAGE_TOO_LARGE")
    void rejectsDecompressionBomb() throws Exception {
        DefaultImageProcessor tiny = new DefaultImageProcessor(1000L, ImageFormat.JPEG, 0.8f);
        assertThatThrownBy(() -> tiny.process(
                        png(100, 100, false),
                        ResizeSpec.builder()
                                .maxEdge(50)
                                .format(ImageFormat.JPEG)
                                .build()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);
    }

    @Test
    @DisplayName("빈 입력 → EMPTY_IMAGE")
    void rejectsEmpty() {
        assertThatThrownBy(() ->
                        processor.process(new byte[0], ResizeSpec.builder().build()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ImageErrorCode.EMPTY_IMAGE);
    }

    @Test
    @DisplayName("해석 불가 바이트 → DECODE_FAILED")
    void rejectsGarbage() {
        assertThatThrownBy(() -> processor.process(
                        new byte[] {1, 2, 3, 4, 5, 6, 7, 8},
                        ResizeSpec.builder().build()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ImageErrorCode.DECODE_FAILED);
    }

    // ----- 헬퍼 -----

    static byte[] png(int w, int h, boolean alpha) throws Exception {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(alpha ? new Color(0, 128, 255, 128) : Color.CYAN);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    static BufferedImage decode(byte[] b) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(b));
    }
}
