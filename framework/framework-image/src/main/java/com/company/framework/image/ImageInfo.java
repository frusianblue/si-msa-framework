package com.company.framework.image;

/** 디코드 없이 헤더에서 읽은 원본 정보. {@code width}/{@code height} 는 EXIF 보정 <b>전</b> 저장 픽셀 기준. */
public record ImageInfo(int width, int height, String formatName) {}
