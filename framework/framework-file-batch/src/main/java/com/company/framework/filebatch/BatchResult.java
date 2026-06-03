package com.company.framework.filebatch;

import java.util.List;

/**
 * 일괄처리 집계 결과(불변). 호출자는 {@link #hasFailures()}/{@link #failures()} 로 부분 실패를 반드시 확인해야 한다
 * (조용히 성공으로 오인 금지). {@link #outcomes()} 는 항상 <b>입력 순서</b>로 정렬되어 있다.
 *
 * @param total     전체 건수.
 * @param succeeded 성공(OK) 건수(드라이런 계획 포함).
 * @param failed    실패 건수.
 * @param skipped   스킵 건수(fail-fast 로 실행되지 못한 건 등).
 * @param outcomes  개별 결과(입력 순서).
 */
public record BatchResult(int total, int succeeded, int failed, int skipped, List<ItemOutcome> outcomes) {

    public BatchResult {
        outcomes = (outcomes == null) ? List.of() : List.copyOf(outcomes);
    }

    /** 결과 목록에서 집계해 생성. */
    public static BatchResult of(List<ItemOutcome> outcomes) {
        int ok = 0;
        int fail = 0;
        int skip = 0;
        for (ItemOutcome o : outcomes) {
            switch (o.status()) {
                case OK -> ok++;
                case FAILED -> fail++;
                case SKIPPED -> skip++;
            }
        }
        return new BatchResult(outcomes.size(), ok, fail, skip, outcomes);
    }

    public boolean hasFailures() {
        return failed > 0;
    }

    /** 실패한 결과만. */
    public List<ItemOutcome> failures() {
        return outcomes.stream().filter(ItemOutcome::isFailed).toList();
    }
}
