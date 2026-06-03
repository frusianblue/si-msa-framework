package com.company.framework.core.util;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * IO/스트림 공통 유틸 — 대용량을 메모리에 적재하지 않는 <b>가드된 스트리밍 전송</b>.
 *
 * <p>표준 {@code transferTo} 위에, SI 에서 자주 필요한 (1) 크기 상한 가드(DoS/폭탄 방지),
 * (2) 전송하며 동시 무결성 해시(tee), (3) 안전 폐기/닫기를 더한다. IO 실패는 {@link UncheckedIOException},
 * 잘못된 인자는 {@link IllegalArgumentException}, 크기 상한 초과는 표준 {@link BusinessException}(400)으로 던진다.
 */
public final class IoUtils {

    private static final int BUFFER = 8192;

    private IoUtils() {}

    /** {@code in} 을 {@code out} 으로 전부 흘려보낸다. 반환=복사 바이트 수. 스트림은 닫지 않는다(호출자 소유). */
    public static long copy(InputStream in, OutputStream out) {
        require(in != null && out != null, "스트림이 null 입니다.");
        try {
            return in.transferTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException("스트림 복사 실패", e);
        }
    }

    /**
     * {@code maxBytes} 를 넘기면 즉시 {@link BusinessException}(400). 신뢰경계의 업로드/해제 본문에 상한을 강제한다.
     * 초과 시점까지만 읽고 멈추므로 무한/폭탄 입력으로부터 보호된다.
     */
    public static long copyLimited(InputStream in, OutputStream out, long maxBytes) {
        require(in != null && out != null, "스트림이 null 입니다.");
        require(maxBytes >= 0, "maxBytes 는 0 이상이어야 합니다.");
        byte[] buf = new byte[BUFFER];
        long total = 0;
        try {
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > maxBytes) {
                    throw new BusinessException(
                            ErrorCode.Common.INVALID_INPUT, "입력 크기가 상한(" + maxBytes + "B)을 초과했습니다.");
                }
                out.write(buf, 0, n);
            }
            return total;
        } catch (IOException e) {
            throw new UncheckedIOException("스트림 복사 실패", e);
        }
    }

    /** 전체를 바이트 배열로 읽는다(작은 입력 전용). 큰 입력은 {@link #toByteArrayLimited}. */
    public static byte[] toByteArray(InputStream in) {
        require(in != null, "스트림이 null 입니다.");
        try {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("스트림 읽기 실패", e);
        }
    }

    /** 상한을 둔 전체 읽기 — {@code maxBytes} 초과 시 {@link BusinessException}. */
    public static byte[] toByteArrayLimited(InputStream in, long maxBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copyLimited(in, out, maxBytes);
        return out.toByteArray();
    }

    /** 스트림을 문자열로 읽는다(지정 charset). */
    public static String toString(InputStream in, Charset charset) {
        require(charset != null, "charset 이 null 입니다.");
        return new String(toByteArray(in), charset);
    }

    /**
     * 전송하며 동시에 SHA-256 을 계산한다(tee) — 본문을 두 번 읽지 않고 무결성 해시를 얻는다.
     *
     * @return 소문자 hex SHA-256
     */
    public static String copyAndSha256(InputStream in, OutputStream out) {
        require(in != null && out != null, "스트림이 null 입니다.");
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "SHA-256 미지원");
        }
        byte[] buf = new byte[BUFFER];
        try {
            int n;
            while ((n = in.read(buf)) >= 0) {
                md.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("스트림 복사 실패", e);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** 스트림 내용을 버리며 끝까지 읽는다(연결 재사용 등). 반환=버린 바이트 수. */
    public static long drain(InputStream in) {
        require(in != null, "스트림이 null 입니다.");
        try {
            return in.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("스트림 비우기 실패", e);
        }
    }

    /** 예외를 삼키며 안전하게 닫는다(null 허용). finally 정리용. */
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // 의도적 무시 — 정리 단계
            }
        }
    }

    private static void require(boolean cond, String message) {
        if (!cond) {
            throw new IllegalArgumentException(message);
        }
    }
}
