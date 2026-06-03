package com.company.framework.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** zip-slip 방지 경로 정규화 단위 테스트. */
class ArchiveSafetyTest {

    @Test
    @DisplayName("정상 상대경로는 슬래시 통일·정규화되어 통과한다")
    void normalizesSafePaths() {
        assertThat(ArchiveSafety.sanitizeEntryName("a/b/c.txt")).isEqualTo("a/b/c.txt");
        assertThat(ArchiveSafety.sanitizeEntryName("a\\b\\c.txt")).isEqualTo("a/b/c.txt");
        assertThat(ArchiveSafety.sanitizeEntryName("./a/./b.txt")).isEqualTo("a/b.txt");
        assertThat(ArchiveSafety.sanitizeEntryName("dir//file.txt")).isEqualTo("dir/file.txt");
    }

    @Test
    @DisplayName("상위경로 탈출(..)·절대경로·드라이브는 거부한다")
    void rejectsUnsafePaths() {
        for (String bad : new String[] {"../etc/passwd", "a/../../b", "/etc/passwd", "C:\\win", "..", "  "}) {
            assertThatThrownBy(() -> ArchiveSafety.sanitizeEntryName(bad))
                    .as("bad=%s", bad)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("resolveSafely 는 baseDir 안으로 해석하고, 탈출 시 거부한다")
    void resolveSafelyStaysUnderBase(@org.junit.jupiter.api.io.TempDir Path base) {
        Path ok = ArchiveSafety.resolveSafely(base, "sub/file.txt");
        assertThat(ok.startsWith(base.toAbsolutePath().normalize())).isTrue();

        assertThatThrownBy(() -> ArchiveSafety.resolveSafely(base, "../escape.txt"))
                .isInstanceOf(BusinessException.class);
    }
}
