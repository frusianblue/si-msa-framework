package com.company.framework.idgen.core;

/** 키 없는 분산 고유 ID(주로 엔티티 PK). 기본 구현은 Snowflake. */
public interface IdGenerator {
    long nextLong();

    /** 문자열이 필요할 때 편의 메서드. */
    default String nextString() {
        return Long.toString(nextLong());
    }
}
