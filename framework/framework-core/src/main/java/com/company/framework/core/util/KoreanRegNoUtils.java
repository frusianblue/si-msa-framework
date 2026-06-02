package com.company.framework.core.util;

/**
 * 한국 공공/금융 SI 입력검증 공통 유틸 — 각종 등록번호의 체크섬/형식 검증.
 *
 * <p>모두 정적 메서드이며 상태가 없다. 하이픈/공백은 자동 제거 후 검증한다(저장 형식과 무관하게 동작).
 * 검증 실패 시 예외 대신 {@code false} 를 반환하므로, 호출측에서 {@code BusinessException}(INVALID_INPUT) 등으로
 * 가공하는 정책을 자유롭게 적용할 수 있다.
 */
public final class KoreanRegNoUtils {

    private KoreanRegNoUtils() {}

    private static String digitsOnly(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    /**
     * 주민등록번호(13자리) 검증번호 체크섬 검증. 가중치 {@code 2,3,4,5,6,7,8,9,2,3,4,5} 합의 mod 11 기반.
     *
     * <p>형식·자릿수·검증번호만 본다. 실재(주민센터 발급) 여부나 생년월일 유효성까지는 보장하지 않는다.
     */
    public static boolean isValidResidentNo(String rrn) {
        String d = digitsOnly(rrn);
        if (d.length() != 13) {
            return false;
        }
        int[] weights = {2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5};
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (d.charAt(i) - '0') * weights[i];
        }
        int check = (11 - (sum % 11)) % 10;
        return check == (d.charAt(12) - '0');
    }

    /**
     * 외국인등록번호(13자리) <b>형식</b> 검증 — 자릿수 + 성별자리(7번째)가 5~8.
     *
     * <p><b>주의:</b> 2020-10 부터 발급되는 외국인등록번호는 검증번호(체크섬) 규칙이 폐지되어
     * 체크섬으로는 진위를 검증할 수 없다. 따라서 이 메서드는 형식만 확인한다. 구(舊) 체크섬 검증이 꼭 필요하면
     * 발급일 기준으로 분기하되, 신규 발급분에는 적용하지 말 것.
     */
    public static boolean isValidForeignerNo(String no) {
        String d = digitsOnly(no);
        if (d.length() != 13) {
            return false;
        }
        char gender = d.charAt(6);
        return gender >= '5' && gender <= '8';
    }

    /**
     * 사업자등록번호(10자리) 검증. 가중치 {@code 1,3,7,1,3,7,1,3,5} + 9번째 자리×5 의 십의 자리 보정.
     * (예: 124-81-00998 → 유효)
     */
    public static boolean isValidBusinessNo(String bizNo) {
        String d = digitsOnly(bizNo);
        if (d.length() != 10) {
            return false;
        }
        int[] key = {1, 3, 7, 1, 3, 7, 1, 3, 5};
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (d.charAt(i) - '0') * key[i];
        }
        sum += ((d.charAt(8) - '0') * 5) / 10;
        int check = (10 - (sum % 10)) % 10;
        return check == (d.charAt(9) - '0');
    }

    /**
     * 법인등록번호(13자리) 검증. 앞 12자리에 {@code 1,2} 교대 가중치를 적용한 합의 mod 10 기반.
     * (예: 130111-0006246 → 유효)
     */
    public static boolean isValidCorporateNo(String corpNo) {
        String d = digitsOnly(corpNo);
        if (d.length() != 13) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int weight = (i % 2 == 0) ? 1 : 2;
            sum += (d.charAt(i) - '0') * weight;
        }
        int check = (10 - (sum % 10)) % 10;
        return check == (d.charAt(12) - '0');
    }
}
