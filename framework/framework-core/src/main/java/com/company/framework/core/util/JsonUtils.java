package com.company.framework.core.util;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 직렬화/역직렬화 공통 유틸 (Spring Boot 4 = <b>Jackson 3, {@code tools.jackson.*}</b>).
 *
 * <p>전사 규칙을 {@code JacksonConfig} 와 동일하게 적용한 전용 {@link JsonMapper} 를 정적 공유한다
 * (알 수 없는 필드 무시, 날짜는 ISO-8601 문자열). Spring 빈 {@code ObjectMapper} 주입이 곤란한 정적 유틸·
 * 비(非)빈 컨텍스트에서 손쉽게 쓰기 위한 헬퍼이며, 직렬화 실패는 {@link BusinessException}(INTERNAL_ERROR)로 변환한다.
 *
 * <p><b>주의:</b> {@code com.fasterxml.jackson.*}(Jackson 2) 를 import 하지 말 것. 본 스택은 Jackson 3 전용이다.
 */
public final class JsonUtils {

    private JsonUtils() {}

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /** 공유 매퍼 접근자(고급 사용·커스텀 읽기/쓰기용). */
    public static JsonMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "JSON 직렬화 실패: " + e.getMessage());
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "JSON 역직렬화 실패: " + e.getMessage());
        }
    }

    /** 제네릭 타입(예: {@code List<Foo>}) 역직렬화: {@code fromJson(json, new TypeReference<List<Foo>>() {})}. */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "JSON 역직렬화 실패: " + e.getMessage());
        }
    }
}
