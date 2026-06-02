package com.company.framework.core.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 한글 처리 공통 유틸 — 초성 추출/검색, 자모 분해/조합, 조사 선택, 한↔영 자판 변환.
 *
 * <p>검색·자동완성(초성), 메시지 조립(조사), 잘못된 IME 입력 복구(한영 변환) 등 SI 화면/검색에서 자주 쓰인다.
 * 완성형 한글(가~힣) 음절만 분해/조합 대상이며, 그 외 문자는 그대로 보존한다.
 */
public final class HangulUtils {

    private HangulUtils() {}

    private static final char HANGUL_BASE = 0xAC00;
    private static final char HANGUL_END = 0xD7A3;

    private static final char[] CHO = {
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };
    private static final char[] JUNG = {
        'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    };
    private static final char[] JONG = {
        0, 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ',
        'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    /** 완성형 한글 음절 여부. */
    public static boolean isSyllable(char c) {
        return c >= HANGUL_BASE && c <= HANGUL_END;
    }

    /** 받침 존재 여부(완성형 음절 기준). 비한글/모음 문자는 false. */
    public static boolean hasBatchim(char c) {
        return isSyllable(c) && ((c - HANGUL_BASE) % 28) != 0;
    }

    /** 문자열의 마지막 글자 받침 유무. */
    public static boolean endsWithBatchim(String word) {
        return word != null && !word.isEmpty() && hasBatchim(word.charAt(word.length() - 1));
    }

    /** 초성 추출: {@code "안녕하세요" → "ㅇㄴㅎㅅㅇ"}. 비한글은 그대로 유지. */
    public static String chosung(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (isSyllable(c)) {
                sb.append(CHO[(c - HANGUL_BASE) / 588]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 초성 검색: {@code text} 의 초성열이 {@code chosungQuery} 를 포함하는지(예: 텍스트 "사과", 질의 "ㅅㄱ" → true). */
    public static boolean matchesChosung(String text, String chosungQuery) {
        if (text == null || chosungQuery == null) {
            return false;
        }
        return chosung(text).contains(chosungQuery);
    }

    /**
     * 조사 선택: 마지막 글자의 받침 유무로 둘 중 하나를 붙인다.
     * (예: {@code josa("사과","을","를") → "사과를"}, {@code josa("사람","을","를") → "사람을"})
     */
    public static String josa(String word, String withBatchim, String withoutBatchim) {
        if (word == null || word.isEmpty()) {
            return word;
        }
        return word + (endsWithBatchim(word) ? withBatchim : withoutBatchim);
    }

    /** 자모 분해: 완성형 음절을 초/중/종성 호환자모로 풀어 이어붙인다. (예: "값" → "ㄱㅏㅂㅅ") */
    public static String decompose(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (isSyllable(c)) {
                int idx = c - HANGUL_BASE;
                sb.append(CHO[idx / 588]);
                sb.append(JUNG[(idx % 588) / 28]);
                int jong = idx % 28;
                if (jong != 0) {
                    appendDecomposedJong(sb, jong);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void appendDecomposedJong(StringBuilder sb, int jong) {
        int[] split = jongSplit(jong);
        if (split != null) {
            sb.append(JONG[split[0]]).append(CHO[split[1]]);
        } else {
            sb.append(JONG[jong]);
        }
    }

    // ----- 자모 조합 / 한↔영 자판 변환 -----

    private static int choIdx(char c) {
        for (int i = 0; i < CHO.length; i++) {
            if (CHO[i] == c) {
                return i;
            }
        }
        return -1;
    }

    private static int jungIdx(char c) {
        for (int i = 0; i < JUNG.length; i++) {
            if (JUNG[i] == c) {
                return i;
            }
        }
        return -1;
    }

    private static int jongIdx(char c) {
        for (int i = 1; i < JONG.length; i++) {
            if (JONG[i] == c) {
                return i;
            }
        }
        return -1;
    }

    /** 모음 결합(ㅗ+ㅏ=ㅘ 등). 결합 불가 시 -1. */
    private static int jungCombine(int a, int b) {
        int[][] m = {{8, 0, 9}, {8, 1, 10}, {8, 20, 11}, {13, 4, 14}, {13, 5, 15}, {13, 20, 16}, {18, 20, 19}};
        for (int[] r : m) {
            if (r[0] == a && r[1] == b) {
                return r[2];
            }
        }
        return -1;
    }

    /** 종성 결합(ㄱ+ㅅ=ㄳ 등). 결합 불가 시 -1. */
    private static int jongCombine(int cur, char add) {
        int ja = jongIdx(add);
        if (ja < 0) {
            return -1;
        }
        int[][] m = {
            {1, 19, 3},
            {4, 22, 5},
            {4, 27, 6},
            {8, 1, 9},
            {8, 16, 10},
            {8, 17, 11},
            {8, 19, 12},
            {8, 25, 13},
            {8, 26, 14},
            {8, 27, 15},
            {17, 19, 18}
        };
        for (int[] r : m) {
            if (r[0] == cur && r[1] == ja) {
                return r[2];
            }
        }
        return -1;
    }

    /** 겹받침 분해 → {남는 종성 index, 다음 음절로 넘길 초성 index}. 단일받침이면 null. */
    private static int[] jongSplit(int jong) {
        int[][] m = {
            {3, 1, 9},
            {5, 4, 12},
            {6, 4, 18},
            {9, 8, 0},
            {10, 8, 6},
            {11, 8, 7},
            {12, 8, 9},
            {13, 8, 16},
            {14, 8, 17},
            {15, 8, 18},
            {18, 17, 9}
        };
        for (int[] r : m) {
            if (r[0] == jong) {
                return new int[] {r[1], r[2]};
            }
        }
        return null;
    }

    private static char syllable(int cho, int jung, int jong) {
        return (char) (HANGUL_BASE + (cho * 21 + jung) * 28 + jong);
    }

    private static int migrateJongToCho(int jong) {
        int ci = choIdx(JONG[jong]);
        return ci >= 0 ? ci : 0;
    }

    /** 호환자모 스트림을 완성형 한글로 조합. (예: "ㅇㅏㄴㄴㅕㅇ" → "안녕") */
    public static String compose(String jamo) {
        if (jamo == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        int cho = -1;
        int jung = -1;
        int jong = 0;
        for (int i = 0; i < jamo.length(); i++) {
            char c = jamo.charAt(i);
            int v = jungIdx(c);
            if (v >= 0) {
                if (cho != -1 && jung == -1) {
                    jung = v;
                } else if (cho != -1 && jung != -1 && jong == 0) {
                    int comb = jungCombine(jung, v);
                    if (comb >= 0) {
                        jung = comb;
                    } else {
                        out.append(syllable(cho, jung, 0));
                        cho = -1;
                        jung = -1;
                        out.append(c);
                    }
                } else if (cho != -1 && jung != -1) {
                    int[] sp = jongSplit(jong);
                    if (sp != null) {
                        out.append(syllable(cho, jung, sp[0]));
                        cho = sp[1];
                        jung = v;
                        jong = 0;
                    } else {
                        out.append(syllable(cho, jung, 0));
                        out.append(syllable(migrateJongToCho(jong), v, 0));
                        cho = -1;
                        jung = -1;
                        jong = 0;
                    }
                } else {
                    out.append(c);
                }
            } else {
                int kc = choIdx(c);
                if (kc < 0) {
                    flush(out, cho, jung, jong);
                    cho = -1;
                    jung = -1;
                    jong = 0;
                    out.append(c);
                } else if (cho == -1 && jung == -1) {
                    cho = kc;
                } else if (cho != -1 && jung == -1) {
                    out.append(CHO[cho]);
                    cho = kc;
                } else if (jong == 0) {
                    int ji = jongIdx(c);
                    if (ji >= 0) {
                        jong = ji;
                    } else {
                        out.append(syllable(cho, jung, 0));
                        cho = kc;
                        jung = -1;
                    }
                } else {
                    int comb = jongCombine(jong, c);
                    if (comb >= 0) {
                        jong = comb;
                    } else {
                        out.append(syllable(cho, jung, jong));
                        cho = kc;
                        jung = -1;
                        jong = 0;
                    }
                }
            }
        }
        flush(out, cho, jung, jong);
        return out.toString();
    }

    private static void flush(StringBuilder out, int cho, int jung, int jong) {
        if (cho != -1 && jung != -1) {
            out.append(syllable(cho, jung, jong));
        } else if (cho != -1) {
            out.append(CHO[cho]);
        }
    }

    // 두벌식 자판: 영문 키 → 호환자모
    private static final Map<Character, Character> ENG_TO_JAMO = new HashMap<>();

    static {
        String[] pairs = {
            "qㅂ", "wㅈ", "eㄷ", "rㄱ", "tㅅ", "yㅛ", "uㅕ", "iㅑ", "oㅐ", "pㅔ", "aㅁ", "sㄴ", "dㅇ", "fㄹ", "gㅎ",
            "hㅗ", "jㅓ", "kㅏ", "lㅣ", "zㅋ", "xㅌ", "cㅊ", "vㅍ", "bㅠ", "nㅜ", "mㅡ", "Qㅃ", "Wㅉ", "Eㄸ", "Rㄲ",
            "Tㅆ", "Oㅒ", "Pㅖ"
        };
        for (String s : pairs) {
            ENG_TO_JAMO.put(s.charAt(0), s.charAt(1));
        }
    }

    /**
     * 한글 입력 의도로 영문 자판에 잘못 친 문자열을 한글로 변환(두벌식).
     * (예: {@code "dkssudgktpdy" → "안녕하세요"}) 매핑에 없는 문자는 그대로 둔다.
     */
    public static String engToKor(String eng) {
        if (eng == null) {
            return null;
        }
        StringBuilder jamo = new StringBuilder(eng.length());
        for (char c : eng.toCharArray()) {
            Character k = ENG_TO_JAMO.get(c);
            jamo.append(k != null ? k : c);
        }
        return compose(jamo.toString());
    }
}
