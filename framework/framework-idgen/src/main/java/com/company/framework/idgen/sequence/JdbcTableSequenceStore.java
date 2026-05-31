package com.company.framework.idgen.sequence;

import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 테이블 기반 채번(DB 비종속: H2/PostgreSQL 공통).
 * 행 단위 잠금(UPDATE)으로 단조 증가 보장. 운영 다중 인스턴스에서도 안전.
 * 스키마: <table>(seq_key VARCHAR(100) PK, seq_value BIGINT NOT NULL)
 */
public class JdbcTableSequenceStore implements SequenceStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String table;

    public JdbcTableSequenceStore(JdbcTemplate jdbc, TransactionTemplate tx, String table) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.table = table;
    }

    /** initialize=true 일 때 오토컨피그가 호출. 없으면 무시(이미 있으면 그대로). */
    public void initSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + table
                + " (seq_key VARCHAR(100) PRIMARY KEY, seq_value BIGINT NOT NULL)");
    }

    @Override
    public long next(String key) {
        Long v = tx.execute(status -> {
            int updated = jdbc.update("UPDATE " + table + " SET seq_value = seq_value + 1 WHERE seq_key = ?", key);
            if (updated > 0) {
                return jdbc.queryForObject("SELECT seq_value FROM " + table + " WHERE seq_key = ?", Long.class, key);
            }
            try {
                jdbc.update("INSERT INTO " + table + " (seq_key, seq_value) VALUES (?, ?)", key, 1L);
                return 1L;
            } catch (DataIntegrityViolationException concurrentInsert) {
                jdbc.update("UPDATE " + table + " SET seq_value = seq_value + 1 WHERE seq_key = ?", key);
                return jdbc.queryForObject("SELECT seq_value FROM " + table + " WHERE seq_key = ?", Long.class, key);
            }
        });
        return v == null ? 1L : v;
    }

    /** DataSource 직접 받는 보조 생성자(테스트/수동 구성용). */
    public static JdbcTableSequenceStore of(DataSource ds, TransactionTemplate tx, String table) {
        return new JdbcTableSequenceStore(new JdbcTemplate(ds), tx, table);
    }
}
