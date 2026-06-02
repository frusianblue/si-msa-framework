package com.company.framework.core.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 날짜/시간 공통 유틸 — SI 에서 흔한 {@code yyyyMMdd} 문자열 변환, 만 나이, 기간 계산.
 *
 * <p>{@code java.time} 기반(스레드 안전). 포맷터는 상수로 공유한다. 입력 파싱은 8자리({@code yyyyMMdd})와
 * 하이픈형({@code yyyy-MM-dd})을 모두 허용한다.
 */
public final class DateUtils {

    private DateUtils() {}

    public static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final DateTimeFormatter YMD_DASH = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter YMDHMS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public static final DateTimeFormatter YMDHMS_DASH = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** {@code LocalDate → "yyyyMMdd"} */
    public static String format(LocalDate date) {
        return date == null ? null : date.format(YMD);
    }

    /** {@code LocalDate → "yyyy-MM-dd"} */
    public static String formatDash(LocalDate date) {
        return date == null ? null : date.format(YMD_DASH);
    }

    /** {@code LocalDateTime → "yyyyMMddHHmmss"} */
    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(YMDHMS);
    }

    /** {@code "yyyyMMdd"} 또는 {@code "yyyy-MM-dd"} → {@code LocalDate}. 형식 불일치 시 {@link java.time.format.DateTimeParseException}. */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        return t.contains("-") ? LocalDate.parse(t, YMD_DASH) : LocalDate.parse(t, YMD);
    }

    /** 기준일(오늘) 기준 만 나이. */
    public static int age(LocalDate birthDate) {
        return age(birthDate, LocalDate.now());
    }

    /** 임의 기준일 기준 만 나이. */
    public static int age(LocalDate birthDate, LocalDate baseDate) {
        if (birthDate == null || baseDate == null) {
            throw new IllegalArgumentException("birthDate/baseDate 는 null 일 수 없습니다.");
        }
        return Period.between(birthDate, baseDate).getYears();
    }

    /** 두 날짜 사이 일수(from→to, to 가 더 미래면 양수). */
    public static long daysBetween(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to);
    }

    /** 두 날짜 사이 개월수. */
    public static long monthsBetween(LocalDate from, LocalDate to) {
        return ChronoUnit.MONTHS.between(from, to);
    }

    public static LocalDate firstDayOfMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    public static LocalDate lastDayOfMonth(LocalDate date) {
        return date.withDayOfMonth(date.lengthOfMonth());
    }
}
