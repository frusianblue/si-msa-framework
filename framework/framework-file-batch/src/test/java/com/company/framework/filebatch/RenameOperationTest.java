package com.company.framework.filebatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import com.company.framework.filebatch.ops.RenameOperation;
import com.company.framework.filebatch.ops.RenameOperation.CollisionStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 이름 변경 정책/충돌/실파일 이동 단위 테스트(순수 JDK). */
class RenameOperationTest {

    private static BatchItem item(String name, int index) {
        return BatchItem.of(name, new byte[] {1}).withIndex(index);
    }

    @Nested
    @DisplayName("정책")
    class Policies {
        @Test
        @DisplayName("prefix/suffix 는 확장자 위치를 보존한다")
        void prefixSuffix() {
            assertThat(RenameOperation.prefix("new_").newName("a.txt", 0)).isEqualTo("new_a.txt");
            assertThat(RenameOperation.suffix("_v2").newName("a.txt", 0)).isEqualTo("a_v2.txt");
            assertThat(RenameOperation.suffix("_v2").newName("noext", 0)).isEqualTo("noext_v2");
        }

        @Test
        @DisplayName("regex 치환")
        void regex() {
            assertThat(RenameOperation.regex("\\s+", "_").newName("my file name.txt", 0))
                    .isEqualTo("my_file_name.txt");
        }

        @Test
        @DisplayName("sequence 는 입력 인덱스로 0패딩 연번을 매긴다")
        void sequence() {
            var policy = RenameOperation.sequence("img", 1, 3);
            assertThat(policy.newName("a.png", 0)).isEqualTo("img001.png");
            assertThat(policy.newName("b.jpg", 9)).isEqualTo("img010.jpg");
        }

        @Test
        @DisplayName("template 토큰 치환")
        void template() {
            var policy = RenameOperation.template("{base}-{n}{ext}");
            assertThat(policy.newName("photo.jpeg", 4)).isEqualTo("photo-5.jpeg");
        }
    }

    @Nested
    @DisplayName("충돌 검출(preflight)")
    class Collisions {
        @Test
        @DisplayName("FAIL: 결과 이름이 겹치면 NAME_COLLISION")
        void failOnCollision() {
            // 일부러 모두 같은 결과 이름을 내는 정책으로 충돌 유발
            var fixed = new RenameOperation((name, index) -> "dup.txt", CollisionStrategy.FAIL);
            assertThatThrownBy(() -> fixed.preflight(List.of(item("a.txt", 0), item("b.txt", 1))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("충돌");
        }

        @Test
        @DisplayName("SUFFIX: 충돌 시 -1, -2 연번 부여")
        void suffixOnCollision() throws Exception {
            var op = new RenameOperation((name, index) -> "dup.txt", CollisionStrategy.SUFFIX);
            var items = List.of(item("a.txt", 0), item("b.txt", 1), item("c.txt", 2));
            op.preflight(items);
            assertThat(op.apply(items.get(0)).name()).isEqualTo("dup.txt");
            assertThat(op.apply(items.get(1)).name()).isEqualTo("dup-1.txt");
            assertThat(op.apply(items.get(2)).name()).isEqualTo("dup-2.txt");
        }
    }

    @Nested
    @DisplayName("안전/실파일")
    class SafetyAndIo {
        @Test
        @DisplayName("경로 구분자가 든 결과 이름은 거부")
        void rejectsUnsafeName() {
            var op = new RenameOperation((name, index) -> "../escape.txt");
            assertThatThrownBy(() -> op.preflight(List.of(item("a.txt", 0)))).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("경로 아이템은 같은 디렉토리에서 실제로 이동한다")
        void movesRealFile(@TempDir Path dir) throws IOException {
            Path src = dir.resolve("old.txt");
            Files.writeString(src, "hello");
            var op = new RenameOperation(RenameOperation.prefix("new_"));
            BatchItem in = BatchItem.of(src).withIndex(0);
            op.preflight(List.of(in));
            BatchItem out = op.apply(in);
            assertThat(out.name()).isEqualTo("new_old.txt");
            assertThat(Files.exists(src)).isFalse();
            assertThat(Files.readString(dir.resolve("new_old.txt"))).isEqualTo("hello");
        }
    }
}
