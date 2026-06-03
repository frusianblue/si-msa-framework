package com.company.framework.filebatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 오케스트레이터: 부분 실패 격리 / fail-fast / 드라이런(IO 0) / 순서 보존 / 동시도 상한 / preflight 전파. */
class FileBatchProcessorTest {

    private final FileBatchProcessor processor = new FileBatchProcessor();

    private static List<BatchItem> items(int n) {
        List<BatchItem> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(BatchItem.of("f" + i + ".txt", new byte[] {(byte) i}));
        }
        return list;
    }

    /** 이름을 대문자로 바꾸는(=처리됨 표시) 단순 작업. */
    private static BatchFileOperation upper() {
        return item -> item.withName(item.name().toUpperCase());
    }

    @Test
    @DisplayName("전부 성공 — 결과는 입력 순서, 모두 OK")
    void allSucceed() {
        BatchResult r = processor.run(items(5), upper());
        assertThat(r.total()).isEqualTo(5);
        assertThat(r.succeeded()).isEqualTo(5);
        assertThat(r.hasFailures()).isFalse();
        assertThat(r.outcomes().stream().map(o -> o.item().index()).toList()).containsExactly(0, 1, 2, 3, 4);
        assertThat(r.outcomes().get(2).result().name()).isEqualTo("F2.TXT");
    }

    @Test
    @DisplayName("부분 실패 + continueOnError(기본): 실패는 수집하고 나머지는 계속")
    void partialFailureContinues() {
        BatchFileOperation op = item -> {
            if (item.index() == 2) {
                throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "2번 실패");
            }
            return item.withName(item.name() + ".done");
        };
        BatchResult r = processor.run(items(5), op);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.succeeded()).isEqualTo(4);
        ItemOutcome failed = r.failures().get(0);
        assertThat(failed.item().index()).isEqualTo(2);
        assertThat(failed.errorCode()).isEqualTo("E0001"); // BusinessException 의 ErrorCode 보존
    }

    @Test
    @DisplayName("fail-fast: continueOnError=false 면 실패 이후 일부는 스킵")
    void failFastSkipsRest() {
        BatchFileOperation op = item -> {
            if (item.index() == 0) {
                throw new IllegalStateException("즉시 실패");
            }
            return item;
        };
        BatchResult r = processor.run(
                items(50),
                op,
                BatchOptions.defaults().withContinueOnError(false).withParallelism(1));
        assertThat(r.failed()).isGreaterThanOrEqualTo(1);
        assertThat(r.skipped()).isGreaterThan(0); // 일부는 실행되지 못함
        assertThat(r.failed() + r.skipped() + r.succeeded()).isEqualTo(50);
    }

    @Test
    @DisplayName("드라이런: 실제 IO/적용 없음 — 작업이 호출되지 않고 계획만 수집")
    void dryRunDoesNoWork() {
        AtomicInteger applied = new AtomicInteger();
        BatchFileOperation op = new BatchFileOperation() {
            @Override
            public BatchItem apply(BatchItem item) {
                applied.incrementAndGet();
                return item;
            }

            @Override
            public String plan(BatchItem item) {
                return "would: " + item.name();
            }
        };
        BatchResult r = processor.run(items(4), op, BatchOptions.defaults().withDryRun(true));
        assertThat(applied.get()).isZero(); // apply 한 번도 호출 안 됨
        assertThat(r.succeeded()).isEqualTo(4);
        assertThat(r.outcomes().get(0).message()).isEqualTo("would: f0.txt");
        assertThat(r.outcomes().get(0).result()).isNull();
    }

    @Test
    @DisplayName("동시도 상한: 동시에 도는 작업 수가 parallelism 을 넘지 않는다")
    void concurrencyIsCapped() {
        int parallelism = 3;
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        BatchFileOperation op = item -> {
            int now = active.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(20);
            } finally {
                active.decrementAndGet();
            }
            return item;
        };
        BatchResult r = processor.run(items(30), op, BatchOptions.defaults().withParallelism(parallelism));
        assertThat(r.succeeded()).isEqualTo(30);
        assertThat(peak.get()).isLessThanOrEqualTo(parallelism);
    }

    @Test
    @DisplayName("preflight 실패는 IO 전에 전체를 중단(예외 전파)")
    void preflightAborts() {
        class Failing implements BatchFileOperation, BatchPreflight {
            @Override
            public void preflight(List<BatchItem> items) {
                throw new BusinessException(FileBatchErrorCode.NAME_COLLISION, "사전검증 충돌");
            }

            @Override
            public BatchItem apply(BatchItem item) {
                throw new AssertionError("preflight 실패 시 apply 가 호출되면 안 된다");
            }
        }
        assertThatThrownBy(() -> processor.run(items(3), new Failing()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사전검증");
    }

    @Test
    @DisplayName("빈 입력/널 옵션은 빈 결과")
    void emptyInput() {
        assertThat(processor.run(List.of(), upper()).total()).isZero();
        assertThat(processor.run(null, upper()).total()).isZero();
    }

    /** 사용자 정의 에러코드도 코드 문자열이 보존되는지. */
    enum MyCode implements ErrorCode {
        BOOM("X9", "터짐", HttpStatus.BAD_REQUEST);
        private final String c;
        private final String m;
        private final HttpStatus s;

        MyCode(String c, String m, HttpStatus s) {
            this.c = c;
            this.m = m;
            this.s = s;
        }

        public String code() {
            return c;
        }

        public String message() {
            return m;
        }

        public HttpStatus httpStatus() {
            return s;
        }
    }

    @Test
    @DisplayName("일반 예외는 OPERATION_FAILED 로, BusinessException 은 원래 코드로 수집")
    void errorCodeMapping() {
        BatchFileOperation op = item -> {
            if (item.index() == 0) {
                throw new RuntimeException("generic");
            }
            throw new BusinessException(MyCode.BOOM);
        };
        BatchResult r = processor.run(items(2), op);
        assertThat(r.outcomes().get(0).errorCode()).isEqualTo("FBAT0004");
        assertThat(r.outcomes().get(1).errorCode()).isEqualTo("X9");
    }
}
