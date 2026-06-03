package com.company.framework.filebatch;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 파일 일괄처리 오케스트레이터(Spring 무의존 — JDK 단독 검증 가능).
 *
 * <p>"같은 작업을 여러 파일에 한꺼번에"를 (1) <b>부분 실패 격리</b>(한 건 실패가 전체를 멈추지 않음, 결과 수집),
 * (2) <b>병렬</b>(IO 바운드 → Java 21 가상스레드 + {@link Semaphore} 동시도 상한), (3) <b>드라이런</b>(IO 없이 계획),
 * (4) <b>순서 보존</b>(완료 순서와 무관하게 입력 순서로 결과 정렬)으로 수행한다.
 *
 * <p>작업이 {@link BatchPreflight} 를 구현하면 실제 IO 이전에 1회 사전검증을 호출한다(예: rename 이름 충돌).
 */
public class FileBatchProcessor {

    private final int defaultParallelism;

    public FileBatchProcessor() {
        this(BatchOptions.DEFAULT_PARALLELISM);
    }

    public FileBatchProcessor(int defaultParallelism) {
        this.defaultParallelism = Math.max(1, defaultParallelism);
    }

    /** 설정된 기본 동시도로 만든 기본 옵션(부분 실패 격리, 실제 실행). */
    public BatchOptions defaultOptions() {
        return new BatchOptions(defaultParallelism, true, false);
    }

    /** 기본 옵션으로 실행. */
    public BatchResult run(List<BatchItem> items, BatchFileOperation op) {
        return run(items, op, defaultOptions());
    }

    /** 옵션을 지정해 실행. */
    public BatchResult run(List<BatchItem> items, BatchFileOperation op, BatchOptions options) {
        Objects.requireNonNull(op, "op");
        BatchOptions opts = (options == null) ? defaultOptions() : options;
        if (items == null || items.isEmpty()) {
            return BatchResult.of(List.of());
        }

        // 1. 입력 순서대로 0-기반 인덱스 부여 — 연번 rename·결과 정렬의 안정적 기준.
        List<BatchItem> indexed = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            indexed.add(items.get(i).withIndex(i));
        }

        // 2. 사전검증(capability) — IO 전에 교차 검증(충돌 등). 실패 시 전체 중단(부분 손상 없음).
        if (op instanceof BatchPreflight preflight) {
            preflight.preflight(List.copyOf(indexed));
        }

        // 3. 드라이런: 실제 IO 없이 계획만.
        if (opts.dryRun()) {
            List<ItemOutcome> planned = new ArrayList<>(indexed.size());
            for (BatchItem item : indexed) {
                planned.add(ItemOutcome.planned(item, safePlan(op, item)));
            }
            return BatchResult.of(planned);
        }

        // 4. 병렬 실행 — 가상스레드/작업당 1, Semaphore 로 동시 활성 작업 수 제한.
        Semaphore gate = new Semaphore(opts.parallelism());
        AtomicBoolean abort = new AtomicBoolean(false);
        List<Future<ItemOutcome>> futures = new ArrayList<>(indexed.size());
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (BatchItem item : indexed) {
                futures.add(exec.submit(() -> processOne(item, op, opts, gate, abort)));
            }
        } // close(): 제출된 모든 작업이 끝날 때까지 블록(join)

        // 5. 결과 수집(이미 완료) 후 입력 인덱스 순서로 정렬.
        List<ItemOutcome> outcomes = new ArrayList<>(futures.size());
        for (Future<ItemOutcome> f : futures) {
            outcomes.add(join(f));
        }
        outcomes.sort(Comparator.comparingInt(o -> o.item().index()));
        return BatchResult.of(outcomes);
    }

    private ItemOutcome processOne(
            BatchItem item, BatchFileOperation op, BatchOptions opts, Semaphore gate, AtomicBoolean abort) {
        if (abort.get()) {
            return ItemOutcome.skipped(item, "이전 실패로 중단됨(fail-fast).");
        }
        try {
            gate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ItemOutcome.skipped(item, "동시도 대기 중 인터럽트.");
        }
        try {
            if (abort.get()) {
                return ItemOutcome.skipped(item, "이전 실패로 중단됨(fail-fast).");
            }
            BatchItem result = op.apply(item);
            return ItemOutcome.ok(item, result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!opts.continueOnError()) {
                abort.set(true);
            }
            return ItemOutcome.failed(item, FileBatchErrorCode.OPERATION_FAILED.code(), "작업 인터럽트.");
        } catch (BusinessException e) {
            if (!opts.continueOnError()) {
                abort.set(true);
            }
            ErrorCode ec = e.getErrorCode();
            String code = (ec == null) ? FileBatchErrorCode.OPERATION_FAILED.code() : ec.code();
            return ItemOutcome.failed(item, code, e.getMessage());
        } catch (Exception e) {
            if (!opts.continueOnError()) {
                abort.set(true);
            }
            return ItemOutcome.failed(item, FileBatchErrorCode.OPERATION_FAILED.code(), e.getMessage());
        } finally {
            gate.release();
        }
    }

    private static String safePlan(BatchFileOperation op, BatchItem item) {
        try {
            return op.plan(item);
        } catch (RuntimeException e) {
            return "(계획 산출 실패: " + e.getMessage() + ")";
        }
    }

    private static ItemOutcome join(Future<ItemOutcome> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(FileBatchErrorCode.OPERATION_FAILED, "결과 수집 인터럽트.");
        } catch (Exception e) {
            throw new BusinessException(FileBatchErrorCode.OPERATION_FAILED, "결과 수집 실패: " + e.getMessage());
        }
    }
}
