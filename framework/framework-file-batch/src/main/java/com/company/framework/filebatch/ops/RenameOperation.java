package com.company.framework.filebatch.ops;

import com.company.framework.core.error.BusinessException;
import com.company.framework.filebatch.BatchFileOperation;
import com.company.framework.filebatch.BatchItem;
import com.company.framework.filebatch.BatchPreflight;
import com.company.framework.filebatch.BatchSafety;
import com.company.framework.filebatch.FileBatchErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 이름 변경 작업(순수 JDK — 위임 모듈 불필요). 정책({@link NamingPolicy})으로 각 아이템의 새 이름을 계산하고,
 * 경로가 있으면 같은 디렉토리 안에서 실제로 이동(rename)한다(경로 없는 본문 아이템은 라벨만 변경).
 *
 * <p>이름은 항상 {@link BatchSafety#requireSimpleName(String)} 로 검증된 <b>단일 파일명</b>이어야 한다.
 * 개별 {@code apply} 는 다른 아이템을 못 보므로, <b>충돌 검출은 {@link BatchPreflight} 에서</b> 전체를 함께 보고 수행한다.
 */
public final class RenameOperation implements BatchFileOperation, BatchPreflight {

    /** (현재 이름, 입력 인덱스) → 새 이름. 인덱스 기반이라 병렬에서도 결정적(연번 안전). */
    @FunctionalInterface
    public interface NamingPolicy {
        String newName(String currentName, int index);
    }

    /** 충돌 처리 방식. */
    public enum CollisionStrategy {
        /** 충돌 시 전체 배치 실패(기본). */
        FAIL,
        /** 충돌 시 {@code stem-1.ext}, {@code stem-2.ext} ... 로 연번 부여. */
        SUFFIX
    }

    private final NamingPolicy policy;
    private final CollisionStrategy onCollision;
    // preflight 에서 확정한 인덱스→최종이름. 이후 apply 는 읽기만(스레드 안전).
    private final Map<Integer, String> resolved = new ConcurrentHashMap<>();

    public RenameOperation(NamingPolicy policy) {
        this(policy, CollisionStrategy.FAIL);
    }

    public RenameOperation(NamingPolicy policy, CollisionStrategy onCollision) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.onCollision = Objects.requireNonNull(onCollision, "onCollision");
    }

    @Override
    public void preflight(List<BatchItem> items) {
        resolved.clear();
        Set<String> used = new HashSet<>();
        for (BatchItem it : items) {
            String base = BatchSafety.requireSimpleName(policy.newName(it.name(), it.index()));
            String finalName = base;
            if (!used.add(finalName.toLowerCase(Locale.ROOT))) {
                if (onCollision == CollisionStrategy.FAIL) {
                    throw new BusinessException(FileBatchErrorCode.NAME_COLLISION, "이름 변경 결과가 충돌합니다: " + finalName);
                }
                finalName = disambiguate(base, used);
            }
            resolved.put(it.index(), finalName);
        }
    }

    private String disambiguate(String base, Set<String> used) {
        String stem = stem(base);
        String ext = ext(base);
        for (int n = 1; ; n++) {
            String candidate = stem + "-" + n + ext;
            if (used.add(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
    }

    private String targetFor(BatchItem item) {
        String pre = resolved.get(item.index());
        return (pre != null) ? pre : BatchSafety.requireSimpleName(policy.newName(item.name(), item.index()));
    }

    @Override
    public String plan(BatchItem item) {
        return item.name() + " -> " + targetFor(item);
    }

    @Override
    public BatchItem apply(BatchItem item) throws IOException {
        String target = targetFor(item);
        if (item.sourcePath() == null) {
            return item.withName(target); // 본문 아이템: 라벨만 변경
        }
        Path src = item.sourcePath();
        Path dst = src.resolveSibling(target);
        Files.move(src, dst); // 대상이 이미 존재하면 FileAlreadyExistsException → 부분 실패로 수집
        return item.withSourcePath(dst).withName(target);
    }

    // ---- 정책 팩토리 ----

    /** 접두사 부가. */
    public static NamingPolicy prefix(String prefix) {
        String p = (prefix == null) ? "" : prefix;
        return (name, index) -> p + name;
    }

    /** 확장자 앞에 접미사 부가(예: {@code a.txt} + {@code _v2} → {@code a_v2.txt}). */
    public static NamingPolicy suffix(String suffix) {
        String s = (suffix == null) ? "" : suffix;
        return (name, index) -> stem(name) + s + ext(name);
    }

    /** 정규식 치환(전체 이름 대상). */
    public static NamingPolicy regex(String regex, String replacement) {
        Pattern pattern = Pattern.compile(regex);
        String r = (replacement == null) ? "" : replacement;
        return (name, index) -> pattern.matcher(name).replaceAll(r);
    }

    /**
     * 연번 부여({@code baseName + 0패딩(start+index) + 원래확장자}).
     * 예: base="img", start=1, digits=3, 0번째 {@code a.png} → {@code img001.png}.
     */
    public static NamingPolicy sequence(String baseName, int start, int digits) {
        String b = (baseName == null) ? "" : baseName;
        int d = Math.max(1, digits);
        return (name, index) -> b + String.format(Locale.ROOT, "%0" + d + "d", start + index) + ext(name);
    }

    /**
     * 템플릿 렌더링 토큰: {@code {name}}(원래 전체 이름), {@code {base}}(확장자 제외), {@code {ext}}(점 포함 확장자),
     * {@code {n}}(1-기반 연번=index+1), {@code {index}}(0-기반).
     */
    public static NamingPolicy template(String tmpl) {
        String t = (tmpl == null) ? "" : tmpl;
        return (name, index) -> t.replace("{name}", name)
                .replace("{base}", stem(name))
                .replace("{ext}", ext(name))
                .replace("{n}", Integer.toString(index + 1))
                .replace("{index}", Integer.toString(index));
    }

    // ---- 이름 헬퍼 ----

    /** 확장자를 제외한 이름(마지막 점 앞). 점이 없거나 선두면 전체. */
    static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return (dot <= 0) ? name : name.substring(0, dot);
    }

    /** 점을 포함한 확장자(마지막 점부터). 없거나 선두면 빈 문자열. */
    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return (dot <= 0) ? "" : name.substring(dot);
    }
}
