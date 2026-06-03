package com.company.framework.archive;

import com.company.framework.core.error.BusinessException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 아카이브 경로 안전 유틸 — <b>zip-slip</b>(경로조작) 차단.
 *
 * <p>악성 아카이브는 엔트리 이름에 {@code ../../etc/passwd} 나 절대경로/드라이브를 넣어 압축 해제 시
 * 대상 디렉토리 밖에 파일을 쓰려 한다. 본 유틸은 엔트리 이름을 **검증된 상대경로**로 정규화하거나 거부한다.
 */
public final class ArchiveSafety {

    private ArchiveSafety() {}

    /**
     * 엔트리 이름을 안전한 상대경로(슬래시 구분)로 정규화한다. 위험하면
     * {@link ArchiveErrorCode#UNSAFE_ENTRY_PATH} 로 던진다.
     *
     * <ul>
     *   <li>역슬래시는 슬래시로 통일(Windows 표기)</li>
     *   <li>절대경로(선두 {@code /}) 및 Windows 드라이브({@code C:}) 거부</li>
     *   <li>{@code ..} 상위경로 세그먼트 거부, {@code .}/빈 세그먼트는 제거</li>
     * </ul>
     */
    public static String sanitizeEntryName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ArchiveErrorCode.UNSAFE_ENTRY_PATH, "엔트리 이름이 비어 있습니다.");
        }
        String unified = raw.replace('\\', '/');

        if (unified.startsWith("/")) {
            throw unsafe(raw); // 절대경로
        }
        // Windows 드라이브(C:, c:\\...) 차단
        if (unified.length() >= 2 && unified.charAt(1) == ':') {
            throw unsafe(raw);
        }

        List<String> safe = new ArrayList<>();
        for (String seg : unified.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) {
                continue;
            }
            if (seg.equals("..")) {
                throw unsafe(raw); // 상위경로 탈출 시도
            }
            safe.add(seg);
        }
        if (safe.isEmpty()) {
            throw unsafe(raw);
        }
        return String.join("/", safe);
    }

    /**
     * {@code baseDir} 아래로 안전하게 해석된 절대경로를 돌려준다. 정규화 후에도 baseDir 를 벗어나면 거부한다
     * (심볼릭/이중 인코딩 등 대비, 최종 방어선).
     */
    public static Path resolveSafely(Path baseDir, String entryName) {
        Path base = baseDir.toAbsolutePath().normalize();
        Path resolved = base.resolve(sanitizeEntryName(entryName)).normalize();
        if (!resolved.startsWith(base)) {
            throw unsafe(entryName);
        }
        return resolved;
    }

    private static BusinessException unsafe(String raw) {
        // 원본 경로 전체를 메시지에 그대로 싣지 않는다(로그 인젝션/정보노출 방지) — 길이만 힌트.
        return new BusinessException(
                ArchiveErrorCode.UNSAFE_ENTRY_PATH, "엔트리 경로가 허용되지 않습니다(length=" + raw.length() + ").");
    }
}
