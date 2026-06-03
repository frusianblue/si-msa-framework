package com.company.framework.file.storage;

import java.util.Optional;

/**
 * HTTP {@code Range} 단일 바이트 범위(RFC 7233). 다중 범위는 지원하지 않는다(첫 범위만/전체 폴백).
 *
 * @param start 시작 오프셋(포함)
 * @param endInclusive 끝 오프셋(포함)
 * @param totalLength 리소스 전체 길이
 */
public record ByteRange(long start, long endInclusive, long totalLength) {

    public ByteRange {
        if (totalLength < 0) throw new IllegalArgumentException("totalLength < 0");
        if (start < 0 || endInclusive < start || endInclusive >= totalLength) {
            throw new IllegalArgumentException("잘못된 범위: " + start + "-" + endInclusive + "/" + totalLength);
        }
    }

    /** 이 범위가 가리키는 바이트 수. */
    public long length() {
        return endInclusive - start + 1;
    }

    /** {@code Content-Range: bytes start-end/total} 헤더 값. */
    public String contentRangeHeader() {
        return "bytes " + start + "-" + endInclusive + "/" + totalLength;
    }

    /**
     * {@code Range} 헤더를 단일 범위로 파싱. 지원 형식: {@code bytes=START-END}, {@code bytes=START-}(끝까지),
     * {@code bytes=-SUFFIX}(마지막 SUFFIX 바이트). 헤더가 없거나 형식이 맞지 않거나 만족 불가하면 empty 를 돌려
     * 호출 측이 전체(200) 응답하도록 한다.
     *
     * @param header Range 헤더 값(null 허용)
     * @param totalLength 리소스 전체 길이(>0)
     */
    public static Optional<ByteRange> parse(String header, long totalLength) {
        if (header == null || totalLength <= 0) return Optional.empty();
        String h = header.trim();
        if (!h.startsWith("bytes=")) return Optional.empty();
        String spec = h.substring("bytes=".length()).trim();
        if (spec.isEmpty() || spec.indexOf(',') >= 0) return Optional.empty(); // 다중 범위 미지원
        int dash = spec.indexOf('-');
        if (dash < 0) return Optional.empty();

        String startStr = spec.substring(0, dash).trim();
        String endStr = spec.substring(dash + 1).trim();
        try {
            long start;
            long end;
            if (startStr.isEmpty()) {
                // suffix: 마지막 N 바이트
                if (endStr.isEmpty()) return Optional.empty();
                long suffix = Long.parseLong(endStr);
                if (suffix <= 0) return Optional.empty();
                start = Math.max(0, totalLength - suffix);
                end = totalLength - 1;
            } else {
                start = Long.parseLong(startStr);
                end = endStr.isEmpty() ? totalLength - 1 : Long.parseLong(endStr);
            }
            if (start < 0 || start >= totalLength) return Optional.empty(); // 416 상황은 호출 측 판단
            if (end >= totalLength) end = totalLength - 1;
            if (end < start) return Optional.empty();
            return Optional.of(new ByteRange(start, end, totalLength));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
