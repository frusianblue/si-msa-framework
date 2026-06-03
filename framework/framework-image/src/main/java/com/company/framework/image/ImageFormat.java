package com.company.framework.image;

import java.util.Locale;

/**
 * 출력 포맷 화이트리스트. ImageIO 가 코어 JDK 에서 신뢰성 있게 인코딩하는 포맷만 노출한다(임의 포맷 변환 방지).
 *
 * <ul>
 *   <li>{@link #JPEG} — 손실(품질 적용). 알파 미지원 → 인코딩 시 흰 배경으로 평탄화.
 *   <li>{@link #PNG} — 무손실(품질 무관). 알파 보존.
 * </ul>
 *
 * <p>입력 포맷은 ImageIO 가 읽을 수 있으면 무엇이든 허용(JPEG/PNG/GIF/BMP 등) — 화이트리스트는 <b>출력</b>에만 적용한다.
 */
public enum ImageFormat {
    /** JPEG(손실, 품질 적용, 알파 미지원). */
    JPEG("jpeg", "image/jpeg", "jpg", true),
    /** PNG(무손실, 알파 보존). */
    PNG("png", "image/png", "png", false);

    private final String imageIoName;
    private final String mimeType;
    private final String extension;
    private final boolean lossy;

    ImageFormat(String imageIoName, String mimeType, String extension, boolean lossy) {
        this.imageIoName = imageIoName;
        this.mimeType = mimeType;
        this.extension = extension;
        this.lossy = lossy;
    }

    /** ImageIO writer 조회용 포맷명(예: "jpeg", "png"). */
    public String imageIoName() {
        return imageIoName;
    }

    /** 출력 MIME 타입(예: image/jpeg). */
    public String mimeType() {
        return mimeType;
    }

    /** 권장 파일 확장자(점 없음, 예: "jpg"). */
    public String extension() {
        return extension;
    }

    /** 손실 압축 여부(true 면 품질값이 의미 있음). */
    public boolean isLossy() {
        return lossy;
    }

    /**
     * 느슨한 이름 매칭("jpg"/"jpeg"/"JPEG"/"png" 등 → enum). 매칭 실패 시 {@code null}.
     * (Spring 의 relaxed enum 바인딩과 별개로, 코드/입력값에서 직접 변환할 때 사용.)
     */
    public static ImageFormat fromName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "jpg", "jpeg" -> JPEG;
            case "png" -> PNG;
            default -> null;
        };
    }
}
