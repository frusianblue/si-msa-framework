package com.company.framework.idgen.sequence;

/** 키별 단조 증가 카운터. 업무코드 채번의 백엔드. */
public interface SequenceStore {
    /** 주어진 키의 다음 값(최초 호출 시 1부터). */
    long next(String key);
}
