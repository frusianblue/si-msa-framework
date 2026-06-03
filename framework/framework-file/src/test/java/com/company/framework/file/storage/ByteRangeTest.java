package com.company.framework.file.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ByteRangeTest {

    private static final long TOTAL = 1000;

    @Test
    @DisplayName("bytes=0-499 → 0..499, length 500")
    void explicitRange() {
        Optional<ByteRange> r = ByteRange.parse("bytes=0-499", TOTAL);
        assertThat(r).isPresent();
        assertThat(r.get().start()).isZero();
        assertThat(r.get().endInclusive()).isEqualTo(499);
        assertThat(r.get().length()).isEqualTo(500);
        assertThat(r.get().contentRangeHeader()).isEqualTo("bytes 0-499/1000");
    }

    @Test
    @DisplayName("bytes=500- → 끝까지")
    void openEndedRange() {
        ByteRange r = ByteRange.parse("bytes=500-", TOTAL).orElseThrow();
        assertThat(r.start()).isEqualTo(500);
        assertThat(r.endInclusive()).isEqualTo(999);
    }

    @Test
    @DisplayName("bytes=-200 → 마지막 200바이트")
    void suffixRange() {
        ByteRange r = ByteRange.parse("bytes=-200", TOTAL).orElseThrow();
        assertThat(r.start()).isEqualTo(800);
        assertThat(r.endInclusive()).isEqualTo(999);
    }

    @Test
    @DisplayName("끝 오프셋이 전체를 넘으면 total-1 로 클램프")
    void endClamped() {
        ByteRange r = ByteRange.parse("bytes=990-5000", TOTAL).orElseThrow();
        assertThat(r.endInclusive()).isEqualTo(999);
    }

    @Test
    @DisplayName("헤더 없음/형식오류/다중범위/만족불가 → empty")
    void invalidOrUnsupported() {
        assertThat(ByteRange.parse(null, TOTAL)).isEmpty();
        assertThat(ByteRange.parse("items=0-1", TOTAL)).isEmpty();
        assertThat(ByteRange.parse("bytes=0-1,5-6", TOTAL)).isEmpty(); // 다중 범위 미지원
        assertThat(ByteRange.parse("bytes=1000-1100", TOTAL)).isEmpty(); // start>=total → 416 상황
        assertThat(ByteRange.parse("bytes=abc-def", TOTAL)).isEmpty();
        assertThat(ByteRange.parse("bytes=", TOTAL)).isEmpty();
    }
}
