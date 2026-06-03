package com.company.framework.image;

/**
 * JPEG 의 EXIF Orientation 태그(0x0112) 만 외부 라이브러리 없이 직접 읽는다.
 *
 * <p>설계 의도
 *
 * <ul>
 *   <li>메타데이터 라이브러리(metadata-extractor 등) 의존을 피하려고 JPEG 마커 → APP1("Exif\0\0") →
 *       TIFF 헤더(엔디안) → IFD0 → 태그 0x0112 만 최소 파싱한다.
 *   <li><b>방어적</b>: 모든 경계를 검사하고, JPEG 가 아니거나 EXIF/태그가 없거나 파싱이 어긋나면
 *       예외 없이 {@link #NORMAL}(=1, 회전 없음) 을 반환한다. 보정은 "있으면 적용, 없으면 원본 유지"가 안전.
 * </ul>
 *
 * <p>값 의미(EXIF 표준):
 *
 * <pre>
 * 1 = 정상            2 = 좌우반전
 * 3 = 180° 회전       4 = 상하반전
 * 5 = 전치(transpose) 6 = 시계90°
 * 7 = 역전치          8 = 반시계90°(=시계270°)
 * </pre>
 */
public final class ExifOrientation {

    /** 회전/반전 없음(기본값). */
    public static final int NORMAL = 1;

    private static final int JPEG_SOI = 0xD8;
    private static final int JPEG_EOI = 0xD9;
    private static final int JPEG_SOS = 0xDA;
    private static final int MARKER_APP1 = 0xE1;
    private static final int TAG_ORIENTATION = 0x0112;

    private ExifOrientation() {}

    /**
     * 이미지 바이트에서 EXIF orientation(1..8)을 읽는다. 읽을 수 없으면 {@link #NORMAL}.
     *
     * @param data 원본 이미지 바이트(JPEG 가 아니면 즉시 NORMAL)
     * @return 1..8 (실패/부재 시 1)
     */
    public static int read(byte[] data) {
        if (data == null || data.length < 4) {
            return NORMAL;
        }
        // SOI: FF D8
        if (u8(data, 0) != 0xFF || u8(data, 1) != JPEG_SOI) {
            return NORMAL;
        }
        int p = 2;
        while (p + 1 < data.length) {
            if (u8(data, p) != 0xFF) {
                // 마커 정렬이 어긋남 — 다음 0xFF 로 재동기화 시도.
                p++;
                continue;
            }
            int marker = u8(data, p + 1);
            p += 2;
            // 길이 없는 마커들.
            if (marker == JPEG_SOI || marker == JPEG_EOI || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue;
            }
            if (marker == JPEG_SOS) {
                break; // 이미지 데이터 시작 — 이후 메타 없음.
            }
            if (p + 2 > data.length) {
                break;
            }
            int len = (u8(data, p) << 8) | u8(data, p + 1); // 자기 2바이트 포함
            if (len < 2) {
                break;
            }
            int segStart = p + 2;
            int segEnd = p + len;
            if (segEnd > data.length) {
                break;
            }
            if (marker == MARKER_APP1 && isExifHeader(data, segStart, segEnd)) {
                int o = readFromTiff(data, segStart + 6, segEnd);
                if (o != 0) {
                    return o;
                }
            }
            p = segEnd;
        }
        return NORMAL;
    }

    /** 5/6/7/8 은 90/270° 회전이라 가로·세로가 바뀐다. */
    public static boolean swapsDimensions(int orientation) {
        return orientation >= 5 && orientation <= 8;
    }

    /** APP1 세그먼트 시작이 "Exif\0\0" 인지. */
    private static boolean isExifHeader(byte[] d, int start, int end) {
        if (end - start < 6) {
            return false;
        }
        return d[start] == 'E'
                && d[start + 1] == 'x'
                && d[start + 2] == 'i'
                && d[start + 3] == 'f'
                && d[start + 4] == 0
                && d[start + 5] == 0;
    }

    /**
     * TIFF 헤더(tiffStart)부터 IFD0 의 Orientation 태그를 읽는다. 실패 시 0(=미발견).
     * 모든 오프셋은 TIFF 헤더 기준 → 절대 인덱스로 환산하며 경계 검사.
     */
    private static int readFromTiff(byte[] d, int tiffStart, int end) {
        if (tiffStart + 8 > end) {
            return 0;
        }
        boolean little;
        int b0 = u8(d, tiffStart);
        int b1 = u8(d, tiffStart + 1);
        if (b0 == 0x49 && b1 == 0x49) {
            little = true; // "II"
        } else if (b0 == 0x4D && b1 == 0x4D) {
            little = false; // "MM"
        } else {
            return 0;
        }
        if (read16(d, tiffStart + 2, little) != 0x2A) {
            return 0; // TIFF 매직 42 아님
        }
        long ifdOffset = read32(d, tiffStart + 4, little);
        if (ifdOffset < 8 || tiffStart + ifdOffset + 2 > end) {
            return 0;
        }
        int ifd = tiffStart + (int) ifdOffset;
        int count = read16(d, ifd, little);
        int entry = ifd + 2;
        for (int i = 0; i < count; i++) {
            if (entry + 12 > end) {
                break;
            }
            int tag = read16(d, entry, little);
            if (tag == TAG_ORIENTATION) {
                // SHORT(type=3, count=1): 값은 value 필드(entry+8)의 첫 2바이트.
                int val = read16(d, entry + 8, little);
                return (val >= 1 && val <= 8) ? val : 0;
            }
            entry += 12;
        }
        return 0;
    }

    private static int u8(byte[] d, int i) {
        return d[i] & 0xFF;
    }

    private static int read16(byte[] d, int i, boolean little) {
        int a = u8(d, i);
        int b = u8(d, i + 1);
        return little ? (b << 8) | a : (a << 8) | b;
    }

    private static long read32(byte[] d, int i, boolean little) {
        long a = u8(d, i);
        long b = u8(d, i + 1);
        long c = u8(d, i + 2);
        long e = u8(d, i + 3);
        return little ? (e << 24) | (c << 16) | (b << 8) | a : (a << 24) | (b << 16) | (c << 8) | e;
    }
}
