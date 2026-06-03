package com.company.framework.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 신규 공통 유틸 6종(Io/Charset/Text/Collection/Csv/FixedWidth) 단위 테스트. */
class CoreUtilsExtraTest {

    @Nested
    @DisplayName("IoUtils")
    class Io {
        @Test
        @DisplayName("copy: 전부 흘려보내고 바이트 수를 반환한다")
        void copy() {
            byte[] src = "hello".getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long n = IoUtils.copy(new ByteArrayInputStream(src), out);
            assertThat(n).isEqualTo(5);
            assertThat(out.toByteArray()).isEqualTo(src);
        }

        @Test
        @DisplayName("copyLimited: 상한 초과 시 BusinessException(400)")
        void copyLimited() {
            ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
            assertThatThrownBy(() -> IoUtils.copyLimited(in, new ByteArrayOutputStream(), 10))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("copyAndSha256: 전송하며 동일 내용의 해시를 계산한다(tee)")
        void copyAndSha256() {
            byte[] src = "integrity payload".getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String hex = IoUtils.copyAndSha256(new ByteArrayInputStream(src), out);
            assertThat(out.toByteArray()).isEqualTo(src);
            assertThat(hex).hasSize(64).matches("[0-9a-f]+");
            // 동일 입력은 동일 해시(결정적)
            assertThat(IoUtils.copyAndSha256(new ByteArrayInputStream(src), new ByteArrayOutputStream()))
                    .isEqualTo(hex);
        }
    }

    @Nested
    @DisplayName("CharsetUtils")
    class Charsets {
        @Test
        @DisplayName("MS949 ↔ UTF-8 재인코딩 왕복")
        void convertRoundTrip() {
            String ko = "한글 테스트";
            byte[] ms949 = CharsetUtils.encode(ko, CharsetUtils.MS949);
            String viaUtf8 = CharsetUtils.convertToString(ms949, CharsetUtils.MS949);
            assertThat(viaUtf8).isEqualTo(ko);
            byte[] utf8 = CharsetUtils.convertBytes(ms949, CharsetUtils.MS949, CharsetUtils.UTF_8);
            assertThat(new String(utf8, StandardCharsets.UTF_8)).isEqualTo(ko);
        }

        @Test
        @DisplayName("decodeLenient: 깨진 바이트도 예외 없이 대체 문자로 디코딩")
        void lenient() {
            byte[] broken = {(byte) 0xFF, (byte) 0xFE, 0x41}; // 'A' 앞 깨진 바이트
            String s = CharsetUtils.decodeLenient(broken, CharsetUtils.UTF_8);
            assertThat(s).contains("A");
        }
    }

    @Nested
    @DisplayName("TextUtils")
    class Text {
        @Test
        @DisplayName("truncateByBytes: 한글 경계를 깨지 않고 바이트로 자른다")
        void truncateByBytes() {
            // "가나다" MS949 = 6바이트. 5바이트 상한 → "가나"(4바이트)
            String r = TextUtils.truncateByBytes("가나다", 5, CharsetUtils.MS949);
            assertThat(r).isEqualTo("가나");
            assertThat(CharsetUtils.byteLength(r, CharsetUtils.MS949)).isLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("truncateByBytes: 서로게이트(이모지)를 쪼개지 않는다")
        void truncateSurrogate() {
            String emoji = "🔐🔐"; // 각 UTF-8 4바이트
            String r = TextUtils.truncateByBytes(emoji, 6, StandardCharsets.UTF_8);
            assertThat(r).isEqualTo("🔐"); // 6바이트엔 하나만, 반쪽 금지
        }

        @Test
        @DisplayName("blank 헬퍼·패딩·케이스 변환")
        void misc() {
            assertThat(TextUtils.defaultIfBlank("  ", "x")).isEqualTo("x");
            assertThat(TextUtils.leftPad("7", 3, '0')).isEqualTo("007");
            assertThat(TextUtils.rightPad("a", 3, ' ')).isEqualTo("a  ");
            assertThat(TextUtils.toSnakeCase("userName")).isEqualTo("user_name");
            assertThat(TextUtils.toCamelCase("user_name")).isEqualTo("userName");
        }
    }

    @Nested
    @DisplayName("CollectionUtils")
    class Collections {
        @Test
        @DisplayName("chunk: 최대 size 청크로 분할(마지막은 더 작을 수 있음)")
        void chunk() {
            List<List<Integer>> c = CollectionUtils.chunk(List.of(1, 2, 3, 4, 5), 2);
            assertThat(c).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
        }

        @Test
        @DisplayName("null/빈 안전 헬퍼")
        void nullSafe() {
            assertThat(CollectionUtils.isEmpty((List<?>) null)).isTrue();
            assertThat(CollectionUtils.<String>emptyIfNull(null)).isEmpty();
            assertThat(CollectionUtils.<String>firstOrNull(List.of())).isNull();
            assertThat(CollectionUtils.firstOrNull(List.of("a", "b"))).isEqualTo("a");
        }

        @Test
        @DisplayName("chunk size<=0 은 거부")
        void chunkBadSize() {
            assertThatThrownBy(() -> CollectionUtils.chunk(List.of(1), 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("CsvUtils")
    class Csv {
        @Test
        @DisplayName("구분자/따옴표/줄바꿈 포함 필드 왕복(RFC 4180)")
        void roundTrip() {
            List<List<String>> rows =
                    List.of(List.of("name", "note"), List.of("김,철수", "그는 \"hi\" 라 함"), List.of("multi\nline", "ok"));
            String doc = CsvUtils.writeRows(rows);
            assertThat(CsvUtils.parse(doc)).isEqualTo(rows);
        }

        @Test
        @DisplayName("writeField 인용 규칙")
        void quoting() {
            assertThat(CsvUtils.writeField("a,b", ',')).isEqualTo("\"a,b\"");
            assertThat(CsvUtils.writeField("x\"y", ',')).isEqualTo("\"x\"\"y\"");
            assertThat(CsvUtils.writeField("plain", ',')).isEqualTo("plain");
        }

        @Test
        @DisplayName("빈 입력은 빈 리스트")
        void empty() {
            assertThat(CsvUtils.parse("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("FixedWidthUtils")
    class FixedWidth {
        @Test
        @DisplayName("숫자필드: '0' 좌측 패딩(우측 정렬)")
        void numberField() {
            assertThat(FixedWidthUtils.numberField("12345", 10, CharsetUtils.MS949))
                    .isEqualTo("0000012345");
        }

        @Test
        @DisplayName("문자필드: 공백 우측 패딩 + 한글 바이트 기준 폭")
        void textField() {
            String r = FixedWidthUtils.textField("홍길동", 10, CharsetUtils.MS949); // 6바이트 + 공백 4
            assertThat(r).isEqualTo("홍길동    ");
            assertThat(CharsetUtils.byteLength(r, CharsetUtils.MS949)).isEqualTo(10);
        }

        @Test
        @DisplayName("과길이는 문자 경계로 안전 절단")
        void truncate() {
            String r = FixedWidthUtils.fit("홍길동", 4, ' ', true, CharsetUtils.MS949); // 6바이트→4바이트
            assertThat(r).isEqualTo("홍길");
        }

        @Test
        @DisplayName("필드 추출: 바이트 오프셋/길이로 자른다")
        void field() {
            byte[] rec = "AB12345   ".getBytes(StandardCharsets.US_ASCII);
            assertThat(FixedWidthUtils.field(rec, 0, 2, StandardCharsets.US_ASCII))
                    .isEqualTo("AB");
            assertThat(FixedWidthUtils.field(rec, 2, 5, StandardCharsets.US_ASCII))
                    .isEqualTo("12345");
            assertThatThrownBy(() -> FixedWidthUtils.field(rec, 8, 5, StandardCharsets.US_ASCII))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
