package com.company.framework.file.validator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 선언 확장자와 실제 검출 MIME 의 <b>정합성</b>을 판정하는 정책(순수 JDK).
 *
 * <p>목적은 ".png 인데 실제로는 PDF/실행파일" 같은 <b>명백한 위장/폴리글랏</b>을 차단하는 것이다.
 * Tika {@code tika-core} 는 매직넘버 기반이라 컨테이너(zip/OLE2) 수준까지만 정확하므로(예: docx·xlsx·pptx 가
 * 모두 zip 으로 검출될 수 있음), 정확한 1:1 MIME 강제 대신 <b>카테고리 단위 허용 집합</b>으로 검사한다 — 즉
 * 같은 컨테이너 계열 안의 미세 구분(docx↔xlsx)은 통과시키고, 계열이 어긋나는 경우만 거부한다.
 *
 * <p>허용 패턴은 정확 MIME(예: {@code application/pdf}) 또는 접두사(끝이 {@code /} 인 경우, 예 {@code image/})로
 * 표기한다. 규칙에 없는 확장자는 정책 미적용(통과) — 1차 확장자 화이트리스트가 이미 거른 뒤이기 때문.
 */
public final class ExtensionContentTypePolicy {

    /** 보수적 기본 규칙(오탐 최소화: 컨테이너 계열 MIME 을 폭넓게 허용). */
    public static final Map<String, Set<String>> DEFAULT_RULES = buildDefault();

    private final Map<String, Set<String>> rules;

    public ExtensionContentTypePolicy(Map<String, Set<String>> rules) {
        // 확장자 키는 소문자로 정규화
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        rules.forEach((ext, set) -> normalized.put(ext.toLowerCase(Locale.ROOT), Set.copyOf(set)));
        this.rules = Map.copyOf(normalized);
    }

    /** 기본 규칙으로 생성. */
    public static ExtensionContentTypePolicy withDefaults() {
        return new ExtensionContentTypePolicy(DEFAULT_RULES);
    }

    /**
     * 확장자와 검출 MIME 이 정합하는지.
     *
     * @return 규칙이 없거나(미적용) 허용 패턴 중 하나에 매칭하면 true, 계열이 어긋나면 false
     */
    public boolean isConsistent(String extension, String detectedMime) {
        if (extension == null || detectedMime == null) return true;
        Set<String> patterns = rules.get(extension.toLowerCase(Locale.ROOT));
        if (patterns == null || patterns.isEmpty()) return true; // 규칙 없음 → 통과
        String mime = detectedMime.toLowerCase(Locale.ROOT);
        for (String p : patterns) {
            if (p.endsWith("/")) {
                if (mime.startsWith(p)) return true; // 접두사(예: image/)
            } else if (mime.equals(p)) {
                return true; // 정확 일치
            }
        }
        return false;
    }

    /** 해당 확장자에 정책이 정의돼 있는지(없으면 검사 생략). */
    public boolean hasRule(String extension) {
        return extension != null && rules.containsKey(extension.toLowerCase(Locale.ROOT));
    }

    public Map<String, Set<String>> rules() {
        return rules;
    }

    private static Map<String, Set<String>> buildDefault() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        // 이미지: image/* 전반 허용
        for (String ext : List.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff")) {
            m.put(ext, Set.of("image/"));
        }
        // PDF
        m.put("pdf", Set.of("application/pdf"));
        // 텍스트(csv 는 보통 text/plain 으로 검출)
        m.put("txt", Set.of("text/"));
        m.put("csv", Set.of("text/", "application/csv"));
        // 압축
        m.put("zip", Set.of("application/zip", "application/x-zip-compressed", "application/x-tika-ooxml"));
        // OOXML(zip 컨테이너) — 계열 안에서는 서로 통과
        Set<String> ooxml = Set.of(
                "application/zip",
                "application/x-tika-ooxml",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        m.put("docx", ooxml);
        m.put("xlsx", ooxml);
        m.put("pptx", ooxml);
        // 신형 한글(hwpx, zip 기반)
        m.put("hwpx", Set.of("application/zip", "application/x-tika-ooxml", "application/hwp+zip"));
        // 구형 오피스(OLE2 컨테이너) — 계열 안에서는 서로 통과
        Set<String> ole2 = Set.of(
                "application/x-tika-msoffice",
                "application/x-tika-ole2",
                "application/msword",
                "application/vnd.ms-excel",
                "application/vnd.ms-powerpoint");
        m.put("doc", ole2);
        m.put("xls", ole2);
        m.put("ppt", ole2);
        // 구형 한글(OLE2 기반)
        m.put(
                "hwp",
                Set.of(
                        "application/x-tika-msoffice",
                        "application/x-tika-ole2",
                        "application/x-hwp",
                        "application/haansofthwp",
                        "application/vnd.hancom.hwp"));
        return Map.copyOf(m);
    }
}
