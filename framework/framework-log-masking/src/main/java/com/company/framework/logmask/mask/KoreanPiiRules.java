package com.company.framework.logmask.mask;

import com.company.framework.core.util.MaskingUtils;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 한국 개인정보(PII) 탐지용 내장 정규식 + 규칙 팩토리(Spring 무의존). 탐지된 토큰의 실제 마스킹은
 * core {@link MaskingUtils} 에 위임해 전사 마스킹 형식과 일치시킨다.
 *
 * <p><b>오탐(false positive) 정책</b>: 자유 로그에서 임의 숫자열을 과하게 가리면 운영 가독성이 떨어진다. 그래서
 * 경계 조건을 엄격히 둔다(앞뒤 숫자 경계 {@code (?<![0-9]) … (?![0-9])}, 카드는 16자리/그룹 형태로 한정).
 * 오탐 위험이 큰 <b>계좌번호</b>는 내장하되 <b>기본 비활성</b>(필요 시 프로퍼티로 on).
 */
public final class KoreanPiiRules {

    private KoreanPiiRules() {}

    /** 주민등록번호/외국인등록번호: 6자리-[성별1자리]+6자리. 성별 1~8(내국인 1·2/3·4, 외국인 5·6/7·8). */
    public static final Pattern RRN = Pattern.compile("(?<![0-9])\\d{6}-[1-8]\\d{6}(?![0-9])");

    /** 휴대전화: 010/011/016/017/018/019 + 3~4 + 4 (구분자 - . 공백 또는 없음). */
    public static final Pattern PHONE = Pattern.compile("(?<![0-9])01[016789][-. ]?\\d{3,4}[-. ]?\\d{4}(?![0-9])");

    /** 신용/체크카드: 4-4-4-4 그룹(구분자 - . 공백) 또는 16연속. (13~15 일반화는 오탐 커서 제외) */
    public static final Pattern CARD =
            Pattern.compile("(?<![0-9])(?:\\d{4}[-. ]\\d{4}[-. ]\\d{4}[-. ]\\d{4}|\\d{16})(?![0-9])");

    /** 이메일. */
    public static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 계좌번호(오탐 위험 → 기본 off): 2~6-2~6-2~6 숫자 그룹. */
    public static final Pattern ACCOUNT = Pattern.compile("(?<![0-9])\\d{2,6}-\\d{2,6}-\\d{2,6}(?![0-9])");

    public static MaskingRule rrn() {
        return MaskingRule.of("rrn", RRN, MaskingUtils::maskResidentNo);
    }

    public static MaskingRule phone() {
        return MaskingRule.of("phone", PHONE, MaskingUtils::maskPhone);
    }

    public static MaskingRule card() {
        return MaskingRule.of("card", CARD, MaskingUtils::maskCard);
    }

    public static MaskingRule email() {
        return MaskingRule.of("email", EMAIL, MaskingUtils::maskEmail);
    }

    public static MaskingRule account() {
        return MaskingRule.of("account", ACCOUNT, MaskingUtils::maskAccount);
    }

    /**
     * 기본 ON 규칙(오탐 적은 것): 카드 → 주민번호 → 휴대폰 → 이메일 순으로 적용. (계좌=오탐 위험으로 기본 off)
     *
     * <p>순서 메모: 카드(16자리/4-4-4-4)와 주민번호(6-7자리)·휴대폰(3-4-4)은 자릿수/그룹이 달라 상호 오삼킴이 없다.
     * 일관성을 위해 길이가 긴 카드를 먼저 둔다.
     */
    public static List<MaskingRule> defaults() {
        return List.of(card(), rrn(), phone(), email());
    }
}
