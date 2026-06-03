package com.company.framework.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ExifOrientation 순수 파서")
class ExifOrientationTest {

    @ParameterizedTest(name = "orientation={0} 읽기")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    @DisplayName("JPEG APP1/TIFF 에 심은 orientation(1..8)을 그대로 읽는다")
    void readsAllOrientations(int orientation) throws Exception {
        byte[] jpeg = withOrientation(baselineJpeg(40, 20), orientation);
        assertThat(ExifOrientation.read(jpeg)).isEqualTo(orientation);
    }

    @Test
    @DisplayName("JPEG 가 아니면(PNG) NORMAL")
    void nonJpegReturnsNormal() throws Exception {
        assertThat(ExifOrientation.read(pngBytes(8, 8))).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    @DisplayName("잘린/널 입력은 예외 없이 NORMAL")
    void truncatedOrNull() {
        assertThat(ExifOrientation.read(null)).isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.read(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.read(new byte[0])).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    @DisplayName("EXIF 없는 JPEG 은 NORMAL")
    void jpegWithoutExif() throws Exception {
        assertThat(ExifOrientation.read(baselineJpeg(16, 16))).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    @DisplayName("범위 밖 orientation 값은 무시하고 NORMAL")
    void outOfRangeIgnored() throws Exception {
        byte[] jpeg = withOrientation(baselineJpeg(20, 20), 99);
        assertThat(ExifOrientation.read(jpeg)).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    @DisplayName("swapsDimensions: 5~8 만 가로/세로 스왑")
    void swapsDimensions() {
        assertThat(ExifOrientation.swapsDimensions(5)).isTrue();
        assertThat(ExifOrientation.swapsDimensions(6)).isTrue();
        assertThat(ExifOrientation.swapsDimensions(7)).isTrue();
        assertThat(ExifOrientation.swapsDimensions(8)).isTrue();
        assertThat(ExifOrientation.swapsDimensions(1)).isFalse();
        assertThat(ExifOrientation.swapsDimensions(3)).isFalse();
    }

    // ----- 테스트 헬퍼 -----

    static byte[] baselineJpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", bos);
        return bos.toByteArray();
    }

    static byte[] pngBytes(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    /** SOI 직후에 big-endian TIFF 로 Orientation 태그를 담은 APP1(Exif) 세그먼트를 삽입한다. */
    static byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = new byte[] {
            'M',
            'M',
            0x00,
            0x2A, // big-endian, magic 42
            0x00,
            0x00,
            0x00,
            0x08, // IFD0 offset
            0x00,
            0x01, // entry count = 1
            0x01,
            0x12, // tag = Orientation
            0x00,
            0x03, // type = SHORT
            0x00,
            0x00,
            0x00,
            0x01, // count = 1
            (byte) ((orientation >> 8) & 0xFF),
            (byte) (orientation & 0xFF),
            0x00,
            0x00, // value
            0x00,
            0x00,
            0x00,
            0x00 // next IFD = 0
        };
        byte[] exif = new byte[6 + tiff.length];
        exif[0] = 'E';
        exif[1] = 'x';
        exif[2] = 'i';
        exif[3] = 'f';
        exif[4] = 0;
        exif[5] = 0;
        System.arraycopy(tiff, 0, exif, 6, tiff.length);
        int segLen = exif.length + 2; // 길이 2바이트 포함
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0xFF);
        bos.write(0xD8); // SOI
        bos.write(0xFF);
        bos.write(0xE1); // APP1
        bos.write((segLen >> 8) & 0xFF);
        bos.write(segLen & 0xFF);
        bos.write(exif, 0, exif.length);
        bos.write(jpeg, 2, jpeg.length - 2); // 원본 SOI 이후
        return bos.toByteArray();
    }
}
