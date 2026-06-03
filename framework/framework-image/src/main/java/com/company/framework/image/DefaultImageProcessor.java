package com.company.framework.image;

import com.company.framework.core.error.BusinessException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * {@link ImageProcessor} 의 ImageIO(JDK 내장) 구현. 새 외부 의존성 없음.
 *
 * <p>처리 파이프라인
 *
 * <ol>
 *   <li><b>안전 검사</b>: 헤더만 읽어 (w×h) 가 {@code maxSourcePixels} 를 넘으면 디코드 전에 거부(디컴프레션 폭탄 방지).
 *   <li><b>EXIF orientation 보정</b>: 저장된 회전/반전을 실제 픽셀에 적용(요청 시).
 *   <li><b>비율 유지 축소</b>: {@code maxWidth/maxHeight} 박스 안으로(업스케일은 옵션).
 *   <li><b>리인코딩</b>: 화이트리스트 포맷으로 다시 쓴다 — 이 과정에서 EXIF/GPS 등 메타데이터가 보존되지 않아 자동 제거된다.
 * </ol>
 *
 * <p>JPEG 는 알파를 지원하지 않으므로 인코딩 직전 흰 배경으로 평탄화한다. AWT 오프스크린 렌더만 사용하므로
 * 헤드리스 환경(서버/컨테이너)에서 동작한다.
 */
public class DefaultImageProcessor implements ImageProcessor {

    /** 기본 안전 상한: 40MP(예: 약 7300×5500). 8K(33MP)도 통과. */
    public static final long DEFAULT_MAX_SOURCE_PIXELS = 40_000_000L;

    private static final int DEFAULT_THUMBNAIL_EDGE = 320;

    private final long maxSourcePixels;
    private final ImageFormat thumbnailFormat;
    private final float thumbnailQuality;

    public DefaultImageProcessor() {
        this(DEFAULT_MAX_SOURCE_PIXELS, ImageFormat.JPEG, 0.85f);
    }

    public DefaultImageProcessor(long maxSourcePixels, ImageFormat thumbnailFormat, float thumbnailQuality) {
        this.maxSourcePixels = maxSourcePixels > 0 ? maxSourcePixels : DEFAULT_MAX_SOURCE_PIXELS;
        this.thumbnailFormat = thumbnailFormat != null ? thumbnailFormat : ImageFormat.JPEG;
        this.thumbnailQuality = (thumbnailQuality >= 0f && thumbnailQuality <= 1f) ? thumbnailQuality : 0.85f;
    }

    @Override
    public byte[] process(byte[] source, ResizeSpec spec) {
        if (source == null || source.length == 0) {
            throw new BusinessException(ImageErrorCode.EMPTY_IMAGE);
        }
        if (spec == null) {
            throw new IllegalArgumentException("ResizeSpec 은 필수입니다.");
        }

        int orientation = spec.correctOrientation() ? ExifOrientation.read(source) : ExifOrientation.NORMAL;
        BufferedImage decoded = decodeWithGuard(source);

        BufferedImage oriented = applyOrientation(decoded, orientation);
        BufferedImage resized = resizeWithinBox(oriented, spec.maxWidth(), spec.maxHeight(), spec.allowUpscale());
        return encode(resized, spec.format(), spec.quality());
    }

    @Override
    public byte[] thumbnail(byte[] source, int maxEdge) {
        int edge = maxEdge > 0 ? maxEdge : DEFAULT_THUMBNAIL_EDGE;
        return process(source, ResizeSpec.thumbnail(edge, thumbnailFormat, thumbnailQuality));
    }

    @Override
    public ImageInfo probe(byte[] source) {
        if (source == null || source.length == 0) {
            throw new BusinessException(ImageErrorCode.EMPTY_IMAGE);
        }
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (iis == null) {
                throw new BusinessException(ImageErrorCode.DECODE_FAILED);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new BusinessException(ImageErrorCode.DECODE_FAILED);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return new ImageInfo(reader.getWidth(0), reader.getHeight(0), reader.getFormatName());
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BusinessException(ImageErrorCode.DECODE_FAILED, "이미지 정보 조회 실패: " + e.getMessage());
        }
    }

    /** 헤더로 픽셀 수를 먼저 확인(폭탄 방지)한 뒤 디코드한다. */
    private BufferedImage decodeWithGuard(byte[] source) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (iis == null) {
                throw new BusinessException(ImageErrorCode.DECODE_FAILED);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new BusinessException(ImageErrorCode.DECODE_FAILED);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                long pixels = (long) reader.getWidth(0) * (long) reader.getHeight(0);
                if (pixels > maxSourcePixels) {
                    throw new BusinessException(
                            ImageErrorCode.IMAGE_TOO_LARGE, "픽셀 수 초과: " + pixels + " > " + maxSourcePixels);
                }
                BufferedImage img = reader.read(0);
                if (img == null) {
                    throw new BusinessException(ImageErrorCode.DECODE_FAILED);
                }
                return img;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BusinessException(ImageErrorCode.DECODE_FAILED, "디코드 실패: " + e.getMessage());
        }
    }

    /** EXIF orientation(1..8)을 실제 픽셀에 적용한다. orientation<=1 이면 원본 그대로. */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= ExifOrientation.NORMAL || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = ExifOrientation.swapsDimensions(orientation);
        int dw = swap ? h : w;
        int dh = swap ? w : h;

        // AffineTransform(m00, m10, m01, m11, m02, m12) — 표준 EXIF 보정 행렬.
        AffineTransform t =
                switch (orientation) {
                    case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0); // 좌우반전
                    case 3 -> new AffineTransform(-1, 0, 0, -1, w, h); // 180°
                    case 4 -> new AffineTransform(1, 0, 0, -1, 0, h); // 상하반전
                    case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0); // 전치
                    case 6 -> new AffineTransform(0, 1, -1, 0, h, 0); // 시계90°
                    case 7 -> new AffineTransform(0, -1, -1, 0, h, w); // 역전치
                    case 8 -> new AffineTransform(0, -1, 1, 0, 0, w); // 반시계90°
                    default -> new AffineTransform();
                };

        BufferedImage dst = new BufferedImage(dw, dh, bufferedType(src));
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** 박스 안으로 비율 유지 축소. allowUpscale=false 면 원본보다 키우지 않는다. */
    static BufferedImage resizeWithinBox(BufferedImage src, int maxW, int maxH, boolean allowUpscale) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min((double) maxW / w, (double) maxH / h);
        if (!allowUpscale && scale >= 1.0) {
            return src; // 이미 박스 안 — 그대로(불필요한 재샘플 방지).
        }
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        if (tw == w && th == h) {
            return src;
        }
        return highQualityScale(src, tw, th);
    }

    /** 큰 축소는 단계적 절반 축소 후 마지막에 목표 크기로 — 단일 단계보다 에일리어싱이 적다. */
    private static BufferedImage highQualityScale(BufferedImage src, int targetW, int targetH) {
        BufferedImage current = src;
        int w = src.getWidth();
        int h = src.getHeight();
        // 2배 이상 축소가 남아있는 동안 절반씩.
        while (w > targetW * 2 && h > targetH * 2) {
            w = Math.max(targetW, w / 2);
            h = Math.max(targetH, h / 2);
            current = scaleOnce(current, w, h);
        }
        return scaleOnce(current, targetW, targetH);
    }

    private static BufferedImage scaleOnce(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, bufferedType(src));
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** 알파 존재 여부에 따라 캔버스 타입 선택(불필요한 알파 채널 방지). */
    private static int bufferedType(BufferedImage src) {
        return src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    /** 화이트리스트 포맷으로 인코딩. JPEG 는 알파 평탄화 + 품질 적용. 메타데이터는 쓰지 않는다(자동 제거). */
    static byte[] encode(BufferedImage img, ImageFormat format, float quality) {
        if (format == null) {
            throw new BusinessException(ImageErrorCode.UNSUPPORTED_FORMAT);
        }
        try {
            if (format == ImageFormat.JPEG) {
                return encodeJpeg(flattenForJpeg(img), quality);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (!ImageIO.write(img, format.imageIoName(), bos)) {
                throw new BusinessException(ImageErrorCode.ENCODE_FAILED, "writer 없음: " + format.imageIoName());
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ImageErrorCode.ENCODE_FAILED, e.getMessage());
        }
    }

    /** JPEG 는 알파 미지원 → 흰 배경으로 평탄화한 RGB 이미지로 변환. */
    private static BufferedImage flattenForJpeg(BufferedImage img) {
        if (!img.getColorModel().hasAlpha()) {
            return img;
        }
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.drawImage(img, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new BusinessException(ImageErrorCode.ENCODE_FAILED, "JPEG writer 없음");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }
}
