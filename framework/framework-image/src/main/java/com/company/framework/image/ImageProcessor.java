package com.company.framework.image;

/**
 * 이미지 처리 SPI(교체 가능). 기본 구현 {@link DefaultImageProcessor}(ImageIO 기반).
 * 앱이 같은 타입 빈을 직접 정의하면 그쪽이 우선({@code @ConditionalOnMissingBean}).
 *
 * <p>모든 메서드는 실패 시 core {@code BusinessException}({@link ImageErrorCode})을 던진다.
 */
public interface ImageProcessor {

    /**
     * 원본 바이트를 명세대로 처리한다: (1) EXIF orientation 보정 → (2) 비율 유지 축소 →
     * (3) 화이트리스트 포맷으로 리인코딩(이 과정에서 EXIF/GPS 등 메타데이터 제거).
     *
     * @param source 원본 이미지 바이트
     * @param spec 리사이즈/인코딩 명세
     * @return 처리된 이미지 바이트(메타데이터 없음)
     */
    byte[] process(byte[] source, ResizeSpec spec);

    /**
     * 정사각 박스({@code maxEdge}) 썸네일 편의 메서드. 포맷/품질은 구현 기본값을 사용한다.
     *
     * @param source 원본 이미지 바이트
     * @param maxEdge 가장 긴 변의 상한(px)
     * @return 썸네일 바이트
     */
    byte[] thumbnail(byte[] source, int maxEdge);

    /**
     * 전체 디코드 없이 헤더만 읽어 원본 크기/포맷을 조회한다(검증용).
     *
     * @param source 원본 이미지 바이트
     * @return 원본 정보
     */
    ImageInfo probe(byte[] source);
}
