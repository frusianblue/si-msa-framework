package com.example.batchtypes.jobs.partitioned;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code txn.id} 의 [min,max] 범위를 gridSize 개로 균등 분할해, 각 파티션에 {@code minId}/{@code maxId} 를
 * {@link ExecutionContext} 로 넘긴다. 워커 스텝(@StepScope 리더)이 그 범위만 읽어 병렬 처리한다.
 *
 * <p>{@link Partitioner} 는 {@code core.partition}, {@code ExecutionContext} 는 {@code infrastructure.item}.
 */
public class IdRangePartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(IdRangePartitioner.class);

    private final JdbcTemplate jdbcTemplate;

    public IdRangePartitioner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Long min = jdbcTemplate.queryForObject("SELECT min(id) FROM txn", Long.class);
        Long max = jdbcTemplate.queryForObject("SELECT max(id) FROM txn", Long.class);
        Map<String, ExecutionContext> result = new HashMap<>();
        if (min == null || max == null) {
            return result; // 빈 테이블 → 파티션 없음
        }
        long total = max - min + 1;
        long size = Math.max(1, (long) Math.ceil((double) total / gridSize));
        int i = 0;
        for (long start = min; start <= max; start += size) {
            long end = Math.min(start + size - 1, max);
            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong("minId", start);
            ctx.putLong("maxId", end);
            result.put("partition" + i, ctx);
            log.info("[partition] partition{} = id {}..{}", i, start, end);
            i++;
        }
        return result;
    }
}
