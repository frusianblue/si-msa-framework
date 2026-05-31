package com.company.framework.idgen.code;

/** 업무용 코드 채번(접두사 + [일자] + 0패딩 순번). DataSource 가 있을 때만 빈 등록됨. */
public interface CodeGenerator {
    /** prefix + 0패딩순번 (예: next("ORD") -> "ORD000123"). 카운터 키 = prefix. */
    String next(String prefix);

    /** prefix + 일자 + 0패딩순번 (예: next("ORD","yyyyMMdd",6) -> "ORD20260531000123").
     *  카운터 키 = prefix+일자 → 일자가 바뀌면 자동으로 1부터 재시작. */
    String next(String prefix, String datePattern, int pad);
}
