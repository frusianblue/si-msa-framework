package com.company.framework.logmask.mask;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 마스킹 규칙 1건(Spring 무의존, JDK 단독 검증 가능). 정규식으로 매칭된 토큰을 {@code maskFunction} 으로 치환한다.
 *
 * <p>{@code maskFunction} 은 "매칭된 부분문자열 → 마스킹된 부분문자열" 사상이다. 보통 core 의
 * {@code MaskingUtils} 메서드 레퍼런스를 넘겨 전사 마스킹 형식과 일치시킨다(예: {@code MaskingUtils::maskPhone}).
 *
 * <p>스레드 안전: 불변 + {@link Pattern}/{@link Function} 모두 무상태 사용. 컴파일된 패턴을 재사용한다.
 */
public final class MaskingRule {

    private final String name;
    private final Pattern pattern;
    private final Function<String, String> maskFunction;

    private MaskingRule(String name, Pattern pattern, Function<String, String> maskFunction) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 은 필수입니다.");
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern 은 필수입니다: " + name);
        }
        if (maskFunction == null) {
            throw new IllegalArgumentException("maskFunction 은 필수입니다: " + name);
        }
        this.name = name;
        this.pattern = pattern;
        this.maskFunction = maskFunction;
    }

    /** 컴파일된 패턴으로 규칙 생성. */
    public static MaskingRule of(String name, Pattern pattern, Function<String, String> maskFunction) {
        return new MaskingRule(name, pattern, maskFunction);
    }

    /** 정규식 문자열로 규칙 생성(컴파일은 1회). */
    public static MaskingRule of(String name, String regex, Function<String, String> maskFunction) {
        return new MaskingRule(name, Pattern.compile(regex), maskFunction);
    }

    /**
     * 매칭 전체를 같은 길이의 별표로 가리는 규칙(커스텀 패턴 기본 마스킹). 길이는 안전상 64자로 상한.
     */
    public static MaskingRule fullMask(String name, String regex) {
        return new MaskingRule(name, Pattern.compile(regex), token -> "*".repeat(Math.min(token.length(), 64)));
    }

    public String name() {
        return name;
    }

    /** 입력에서 이 규칙에 매칭되는 모든 부분을 마스킹해 반환. 매칭 없으면 원본을 그대로 반환. */
    public String apply(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher matcher = pattern.matcher(input);
        // replaceAll(Function) 의 반환 문자열은 $ \ 가 해석되므로 quoteReplacement 로 리터럴 보장.
        return matcher.replaceAll(mr -> Matcher.quoteReplacement(maskFunction.apply(mr.group())));
    }
}
