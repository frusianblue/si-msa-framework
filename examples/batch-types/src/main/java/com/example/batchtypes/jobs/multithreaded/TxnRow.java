package com.example.batchtypes.jobs.multithreaded;

import java.math.BigDecimal;

/** 거래 한 행(리더 출력 → record OK). 멀티스레드/파티션/컴포지트 잡이 공통으로 읽는 {@code txn} 행. */
public record TxnRow(Long id, String merchantId, BigDecimal amount, String status) {}
