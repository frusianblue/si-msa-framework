package com.company.framework.filebatch;

import java.util.List;

/**
 * 병렬 실행 전 1회 호출되는 <b>교차 아이템 사전검증</b> 능력(capability). {@link BatchFileOperation} 가
 * 함께 구현하면 {@link FileBatchProcessor} 가 실제 작업(IO) 이전에 {@link #preflight(List)} 를 호출한다.
 *
 * <p>대표 용도: rename 결과 이름의 <b>충돌 검출</b>(개별 {@code apply} 는 다른 아이템을 볼 수 없으므로,
 * 전체를 함께 봐야 하는 검증은 여기서 한다). 위반 시 표준 예외를 던져 전체 배치를 IO 전에 중단할 수 있다.
 */
public interface BatchPreflight {
    /** 입력 순서대로 인덱스가 부여된 전체 아이템에 대해 교차 검증/사전 계산을 수행한다. */
    void preflight(List<BatchItem> items);
}
