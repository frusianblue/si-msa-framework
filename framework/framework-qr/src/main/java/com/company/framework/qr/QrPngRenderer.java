package com.company.framework.qr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * 흑/백 격자({@link PixelGrid})를 PNG 바이트로 렌더링한다 — JDK 내장 {@code javax.imageio} 만 사용(ZXing 무의존).
 *
 * <p>ZXing javase 의 {@code MatrixToImageWriter} 를 대신해 직접 렌더링함으로써 모듈 의존성을 {@code zxing-core} 1개로
 * 줄인다(framework-image 가 ImageIO 만 쓰는 것과 같은 결). 채움/배경 색은 0xRRGGBB.
 */
final class QrPngRenderer {

    private QrPngRenderer() {}

    /**
     * 격자를 PNG 로 인코딩한다.
     *
     * @param grid 흑/백 모듈 격자
     * @param darkRgb 채움 색(0xRRGGBB)
     * @param lightRgb 배경 색(0xRRGGBB)
     * @return PNG 바이트
     * @throws IOException ImageIO 가 PNG writer 를 못 찾거나 쓰기 실패 시
     */
    static byte[] renderPng(PixelGrid grid, int darkRgb, int lightRgb) throws IOException {
        int w = grid.width();
        int h = grid.height();
        // TYPE_INT_RGB: 알파 불필요(QR 은 불투명). 정수 RGB 직접 set 으로 빠르게 채운다.
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int dark = darkRgb & 0xFFFFFF;
        int light = lightRgb & 0xFFFFFF;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, grid.get(x, y) ? dark : light);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", baos)) {
            // PNG writer 가 등록돼 있지 않은 비정상 JRE — 표준 JDK 에서는 발생하지 않는다.
            throw new IOException("PNG writer 를 찾을 수 없습니다.");
        }
        return baos.toByteArray();
    }
}
