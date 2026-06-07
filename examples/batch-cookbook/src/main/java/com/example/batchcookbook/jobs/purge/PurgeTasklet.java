package com.example.batchcookbook.jobs.purge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * <b>패턴 4 — 단일 Tasklet(정리 배치).</b> 보관기한이 지난 감사 로그를 삭제한다.
 * 리더/라이터/청크가 필요 없는 단순 작업은 Tasklet 하나로 끝내는 게 가장 단순하고 명확하다.
 *
 * <p>보관일수는 {@code app.purge.retention-days}(기본 90). 대량 삭제는 운영에서 락/부하를 고려해
 * 배치 분할(LIMIT 반복)하기도 하지만, 여기선 패턴을 보이기 위해 1방 DELETE 로 둔다.
 */
public class PurgeTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(PurgeTasklet.class);

    private final JdbcTemplate jdbcTemplate;
    private final int retentionDays;

    public PurgeTasklet(JdbcTemplate jdbcTemplate, int retentionDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionDays = retentionDays;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM audit_log WHERE created_at < (CURRENT_TIMESTAMP - make_interval(days => ?))",
                retentionDays);
        log.info("[purge] 보관기한({}일) 초과 감사로그 {}건 삭제", retentionDays, deleted);
        return RepeatStatus.FINISHED;
    }
}
