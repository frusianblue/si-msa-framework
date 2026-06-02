package com.company.framework.saga;

import java.util.function.IntPredicate;

/**
 * saga 진행/보상 순서 결정(순수 함수). 영속/발행과 분리해 단독 검증 가능.
 */
public final class SagaPlanner {

    private SagaPlanner() {}

    /** 다음 정방향 액션 인덱스. 마지막이면 -1(완료). */
    public static int nextActionIndex(int currentIndex, int totalSteps) {
        int next = currentIndex + 1;
        return next < totalSteps ? next : -1;
    }

    /**
     * {@code fromExclusive} 미만에서 보상 대상(보상 정의 + 액션 완료)을 역순으로 찾는다.
     * @return 가장 큰(가장 최근) 보상 대상 인덱스, 없으면 -1.
     */
    public static int compensationTargetBelow(SagaDefinition def, IntPredicate actionDone, int fromExclusive) {
        int start = Math.min(fromExclusive, def.size()) - 1;
        for (int i = start; i >= 0; i--) {
            if (def.step(i).hasCompensation() && actionDone.test(i)) {
                return i;
            }
        }
        return -1;
    }
}
