package com.example.settlement.batch;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 리더 입력 — 정산 대상 원시 거래 한 건({@code raw_transaction} 테이블 행).
 *
 * <p>불변 레코드. {@code JdbcCursorItemReader} 의 {@code RowMapper} 가 행→객체로 매핑한다.
 */
public record RawTransaction(long id, String merchantId, BigDecimal amount, String status, LocalDate tradeDate) {}
