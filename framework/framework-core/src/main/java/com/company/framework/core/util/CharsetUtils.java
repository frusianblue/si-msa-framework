package com.company.framework.core.util;

import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 문자셋 변환 공통 유틸 — 레거시/대외기관 연계의 <b>CP949·EUC-KR ↔ UTF-8</b> 안전 변환.
 *
 * <p>국내 공공/금융 레거시는 EUC-KR(완성형) 또는 CP949/MS949(확장완성형)를 쓰는 경우가 많다. 연계 파일·전문을
 * UTF-8 시스템으로 받을 때 글자 깨짐을 막고, 깨진 바이트가 섞여도 예외 없이 처리(REPLACE)하는 헬퍼를 제공한다.
 */
public final class CharsetUtils {

    /** Windows 한국어 확장완성형(CP949 상위호환). 국내 레거시 연계의 사실상 표준. */
    public static final Charset MS949 = Charset.forName("MS949");

    /** 완성형(KS X 1001). */
    public static final Charset EUC_KR = Charset.forName("EUC-KR");

    public static final Charset UTF_8 = StandardCharsets.UTF_8;

    private CharsetUtils() {}

    /** 문자열 → 바이트(지정 charset). */
    public static byte[] encode(String s, Charset charset) {
        require(s != null, "문자열이 null 입니다.");
        require(charset != null, "charset 이 null 입니다.");
        return s.getBytes(charset);
    }

    /** 바이트 → 문자열(지정 charset). */
    public static String decode(byte[] bytes, Charset charset) {
        require(bytes != null, "바이트가 null 입니다.");
        require(charset != null, "charset 이 null 입니다.");
        return new String(bytes, charset);
    }

    /** {@code from} 으로 해석한 바이트를 {@code to} 바이트로 재인코딩한다(예: MS949 → UTF-8). */
    public static byte[] convertBytes(byte[] src, Charset from, Charset to) {
        return decode(src, from).getBytes(to);
    }

    /** {@code from} 으로 해석한 바이트를 문자열로 돌려준다(연계 파일 디코딩 단축). */
    public static String convertToString(byte[] src, Charset from) {
        return decode(src, from);
    }

    /**
     * 깨진/매핑불가 바이트가 있어도 예외 없이 {@code U+FFFD} 로 대체해 디코딩한다(불량 레거시 데이터 방어).
     * 정확한 왕복이 필요하면 {@link #decode}(예외/손실 가능)를 쓰고, 베스트에포트 표시·로깅에는 이 메서드를 쓴다.
     */
    public static String decodeLenient(byte[] bytes, Charset charset) {
        require(bytes != null, "바이트가 null 입니다.");
        require(charset != null, "charset 이 null 입니다.");
        var decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            // REPLACE 모드라 실질적으로 도달하지 않지만, 시그니처상 방어.
            return new String(bytes, charset);
        }
    }

    /** 지정 charset 기준 바이트 길이(고정폭 전문 길이 계산에 사용). */
    public static int byteLength(String s, Charset charset) {
        return encode(s, charset).length;
    }

    private static void require(boolean cond, String message) {
        if (!cond) {
            throw new IllegalArgumentException(message);
        }
    }
}
