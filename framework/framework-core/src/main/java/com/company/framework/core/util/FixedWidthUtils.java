package com.company.framework.core.util;

import java.nio.charset.Charset;

/**
 * 고정폭(fixed-width) 전문 공통 유틸 — 국내 금융/공공 대외기관 연계의 <b>바이트 고정폭 레코드</b> 생성/파싱.
 *
 * <p>전문은 보통 CP949/EUC-KR 기준 <b>바이트 길이</b>로 필드 폭이 고정된다. 한글이 들어가면 문자 수와 바이트 수가
 * 달라 글자 깨짐·자리 어긋남이 나기 쉬우므로, 본 유틸은 {@link CharsetUtils}/{@link TextUtils} 로 바이트 기준
 * 절단·패딩을 처리한다. 패딩 문자는 단일 바이트(공백/{@code '0'} 등)를 전제한다.
 */
public final class FixedWidthUtils {

    private FixedWidthUtils() {}

    /**
     * 값을 {@code widthBytes} 바이트 폭에 맞춘다. 길면 문자 경계로 안전 절단, 짧으면 {@code pad} 로 채운다.
     *
     * @param leftAlign true=좌측정렬(우측 패딩, 문자열 필드) / false=우측정렬(좌측 패딩, 숫자 필드)
     */
    public static String fit(String value, int widthBytes, char pad, boolean leftAlign, Charset charset) {
        if (widthBytes < 0) {
            throw new IllegalArgumentException("widthBytes 는 0 이상이어야 합니다.");
        }
        if (charset == null) {
            throw new IllegalArgumentException("charset 이 null 입니다.");
        }
        String v = value == null ? "" : value;
        String fitted = TextUtils.truncateByBytes(v, widthBytes, charset);
        int used = CharsetUtils.byteLength(fitted, charset);
        int padCount = widthBytes - used; // pad 는 단일 바이트 전제
        if (padCount <= 0) {
            return fitted;
        }
        String padding = String.valueOf(pad).repeat(padCount);
        return leftAlign ? fitted + padding : padding + fitted;
    }

    /** 문자열 필드: 좌측정렬 + 공백 우측 패딩(바이트 기준). */
    public static String textField(String value, int widthBytes, Charset charset) {
        return fit(value, widthBytes, ' ', true, charset);
    }

    /** 숫자 필드: 우측정렬 + '0' 좌측 패딩(바이트 기준). */
    public static String numberField(String value, int widthBytes, Charset charset) {
        return fit(value, widthBytes, '0', false, charset);
    }

    /**
     * 고정폭 레코드(바이트 배열)에서 {@code [offset, offset+length)} 바이트를 잘라 디코딩한다.
     * 범위를 벗어나면 {@link IllegalArgumentException}. 후행 공백 제거는 호출자 몫({@link String#strip()}).
     */
    public static String field(byte[] record, int offset, int length, Charset charset) {
        if (record == null) {
            throw new IllegalArgumentException("record 가 null 입니다.");
        }
        if (charset == null) {
            throw new IllegalArgumentException("charset 이 null 입니다.");
        }
        if (offset < 0 || length < 0 || offset + length > record.length) {
            throw new IllegalArgumentException(
                    "필드 범위가 레코드를 벗어납니다(offset=" + offset + ", length=" + length + ", record=" + record.length + ").");
        }
        return new String(record, offset, length, charset);
    }
}
