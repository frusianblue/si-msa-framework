package com.company.framework.core.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * 공휴일/영업일 계산 유틸.
 *
 * <p><b>설계 원칙(중요):</b> 양력 <b>고정</b> 공휴일(신정·삼일절·어린이날·현충일·광복절·개천절·한글날·성탄절)과
 * 주말은 자동 판정한다. 그러나 <b>음력 기반 공휴일(설날·추석·부처님오신날)과 대체공휴일</b>은 해마다 양력 날짜가
 * 달라지고 음양력 변환 표가 필요하므로 <b>여기서 임의로 계산하지 않는다</b>(틀린 날짜를 박는 것보다 주입이 안전).
 * 해당 날짜들은 {@code extraHolidays} 집합으로 주입한다 — 운영에서는 공공데이터포털 "특일정보" API 또는
 * 사내 휴일 테이블/설정(yaml)에서 연 단위로 적재해 넘기는 것을 권장한다.
 *
 * <p>모든 메서드는 {@code extraHolidays} 가 {@code null} 이어도 동작한다(고정공휴일+주말만 적용).
 */
public final class HolidayUtils {

    private HolidayUtils() {}

    /** 토/일 여부. */
    public static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    /** 매년 동일한 양력 고정 공휴일 여부(음력·대체공휴일 제외). */
    public static boolean isFixedSolarHoliday(LocalDate date) {
        int m = date.getMonthValue();
        int d = date.getDayOfMonth();
        return (m == 1 && d == 1) // 신정
                || (m == 3 && d == 1) // 삼일절
                || (m == 5 && d == 5) // 어린이날
                || (m == 6 && d == 6) // 현충일
                || (m == 8 && d == 15) // 광복절
                || (m == 10 && d == 3) // 개천절
                || (m == 10 && d == 9) // 한글날
                || (m == 12 && d == 25); // 성탄절
    }

    /** 휴일(주말 ∪ 고정공휴일 ∪ 주입된 음력/대체공휴일) 여부. */
    public static boolean isHoliday(LocalDate date, Set<LocalDate> extraHolidays) {
        return isWeekend(date) || isFixedSolarHoliday(date) || (extraHolidays != null && extraHolidays.contains(date));
    }

    /** 영업일(=휴일이 아닌 날) 여부. */
    public static boolean isBusinessDay(LocalDate date, Set<LocalDate> extraHolidays) {
        return !isHoliday(date, extraHolidays);
    }

    /** 지정일 다음 영업일(지정일 자신은 제외). */
    public static LocalDate nextBusinessDay(LocalDate date, Set<LocalDate> extraHolidays) {
        LocalDate d = date.plusDays(1);
        while (isHoliday(d, extraHolidays)) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** 지정일 이전 영업일(지정일 자신은 제외). */
    public static LocalDate previousBusinessDay(LocalDate date, Set<LocalDate> extraHolidays) {
        LocalDate d = date.minusDays(1);
        while (isHoliday(d, extraHolidays)) {
            d = d.minusDays(1);
        }
        return d;
    }

    /**
     * 영업일 기준 가감. {@code n}>0 이면 미래 방향으로 n 영업일 뒤, {@code n}<0 이면 과거 방향. {@code n}==0 이면 그대로 반환.
     */
    public static LocalDate plusBusinessDays(LocalDate date, int n, Set<LocalDate> extraHolidays) {
        if (n == 0) {
            return date;
        }
        int step = n > 0 ? 1 : -1;
        int remaining = Math.abs(n);
        LocalDate d = date;
        while (remaining > 0) {
            d = d.plusDays(step);
            if (isBusinessDay(d, extraHolidays)) {
                remaining--;
            }
        }
        return d;
    }

    /** {@code from}(포함)부터 {@code toInclusive}(포함)까지의 영업일 수. */
    public static long businessDaysBetween(LocalDate from, LocalDate toInclusive, Set<LocalDate> extraHolidays) {
        if (from.isAfter(toInclusive)) {
            return 0;
        }
        long count = 0;
        for (LocalDate d = from; !d.isAfter(toInclusive); d = d.plusDays(1)) {
            if (isBusinessDay(d, extraHolidays)) {
                count++;
            }
        }
        return count;
    }
}
