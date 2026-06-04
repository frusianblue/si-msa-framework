package com.company.framework.qr;

/**
 * 흑/백 모듈 격자의 최소 추상(렌더링 seam). ZXing {@code BitMatrix} 를 직접 노출하지 않기 위한 경계로,
 * 이 인터페이스 덕분에 {@link QrPngRenderer} 는 ZXing 무의존이 되어 JDK 단독으로 단위검증할 수 있다.
 *
 * <p>{@link #get(int, int)} 는 해당 좌표가 채움(dark) 모듈이면 {@code true}.
 */
interface PixelGrid {

    /** 격자 너비(px). */
    int width();

    /** 격자 높이(px). */
    int height();

    /** (x,y)가 채움(dark) 모듈이면 true. */
    boolean get(int x, int y);
}
