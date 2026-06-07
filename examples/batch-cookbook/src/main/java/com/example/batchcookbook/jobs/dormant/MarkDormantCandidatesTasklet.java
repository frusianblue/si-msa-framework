package com.example.batchcookbook.jobs.dormant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 다단계 Job 의 1단계(Tasklet) — 1년 이상 활동이 없는 ACTIVE 계좌를 "휴면 후보"로 마킹한다.
 * Tasklet 은 청크 없이 한 번의 트랜잭션으로 끝나는 작업(집계/마킹/정리)에 알맞다.
 *
 * <p>{@code RepeatStatus} 는 Batch 6 에서 {@code infrastructure.repeat.RepeatStatus} 로 이동했다.
 * {@code Tasklet#execute(StepContribution, ChunkContext)} 시그니처는 core 에 그대로.
 */
public class MarkDormantCandidatesTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(MarkDormantCandidatesTasklet.class);

    private final JdbcTemplate jdbcTemplate;

    public MarkDormantCandidatesTasklet(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int marked = jdbcTemplate.update("UPDATE account SET dormant_candidate = true "
                + "WHERE status = 'ACTIVE' AND last_active_date < (CURRENT_DATE - INTERVAL '1 year')");
        log.info("[dormant] 휴면 후보 마킹 {}건", marked);
        contribution.incrementWriteCount(marked);
        return RepeatStatus.FINISHED;
    }
}
