package com.company.framework.qr;

import com.company.framework.core.error.BusinessException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * 기본 {@link QrGenerator} — ZXing {@code QRCodeWriter} 로 인코딩하고 {@link QrPngRenderer}(JDK ImageIO)로 PNG 렌더링.
 *
 * <p>실패는 모두 core {@code BusinessException}({@link QrErrorCode})으로 변환한다:
 * 빈 내용 → {@code EMPTY_CONTENT}, 설정 상한 초과 → {@code CONTENT_TOO_LONG}, ZXing 인코딩 실패(용량 초과 등) →
 * {@code ENCODE_FAILED}, PNG 쓰기 실패 → {@code RENDER_FAILED}.
 */
public class ZxingQrGenerator implements QrGenerator {

    private final QrSpec defaultSpec;
    private final int maxContentLength;

    /**
     * @param defaultSpec {@link #toPng(String)} 가 쓰는 기본 스펙
     * @param maxContentLength 인코딩 전 차단할 내용 길이 상한(문자 수). 0 이하면 길이 검사 생략(ZXing 용량 한계에만 의존).
     */
    public ZxingQrGenerator(QrSpec defaultSpec, int maxContentLength) {
        this.defaultSpec = (defaultSpec == null) ? QrSpec.defaults() : defaultSpec;
        this.maxContentLength = maxContentLength;
    }

    @Override
    public byte[] toPng(String content) {
        return generate(content, defaultSpec);
    }

    @Override
    public byte[] generate(String content, QrSpec spec) {
        if (content == null || content.isEmpty()) {
            throw new BusinessException(QrErrorCode.EMPTY_CONTENT);
        }
        if (maxContentLength > 0 && content.length() > maxContentLength) {
            throw new BusinessException(QrErrorCode.CONTENT_TOO_LONG);
        }
        QrSpec s = (spec == null) ? defaultSpec : spec;

        BitMatrix matrix;
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, toZxingEcc(s.eccLevel()));
            hints.put(EncodeHintType.MARGIN, s.margin());
            hints.put(EncodeHintType.CHARACTER_SET, s.charset());
            matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, s.sizePx(), s.sizePx(), hints);
        } catch (WriterException | IllegalArgumentException e) {
            // 용량 초과(데이터가 너무 큼)·미지원 문자셋 등. 내부 메시지는 싣지 않는다.
            throw new BusinessException(QrErrorCode.ENCODE_FAILED);
        }

        try {
            return QrPngRenderer.renderPng(new BitMatrixGrid(matrix), s.darkColor(), s.lightColor());
        } catch (IOException e) {
            throw new BusinessException(QrErrorCode.RENDER_FAILED);
        }
    }

    private static ErrorCorrectionLevel toZxingEcc(QrEccLevel level) {
        return switch (level) {
            case L -> ErrorCorrectionLevel.L;
            case M -> ErrorCorrectionLevel.M;
            case Q -> ErrorCorrectionLevel.Q;
            case H -> ErrorCorrectionLevel.H;
        };
    }

    /** ZXing {@link BitMatrix} 를 {@link PixelGrid} 로 어댑트(렌더러가 ZXing 을 모르도록 하는 경계). */
    private record BitMatrixGrid(BitMatrix matrix) implements PixelGrid {
        @Override
        public int width() {
            return matrix.getWidth();
        }

        @Override
        public int height() {
            return matrix.getHeight();
        }

        @Override
        public boolean get(int x, int y) {
            return matrix.get(x, y);
        }
    }
}
