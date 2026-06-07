package com.example.settlement.batch;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 라이터 출력 — 가맹점·일자별 정산 결과 한 건({@code settlement_result} 테이블 행).
 *
 * <p>{@code JdbcBatchItemWriter} 의 {@code beanMapped()} 는 표준 JavaBean 게터({@code getXxx})로 네임드 파라미터
 * ({@code :merchantId} 등)를 채운다. <b>record 가 아니라 게터를 가진 클래스</b>로 둔 이유다 —
 * record 접근자({@code merchantId()})는 BeanProperty 규약에 잡히지 않는다(PITFALLS 참조).
 */
public class SettlementRecord {

    private final String merchantId;
    private final LocalDate tradeDate;
    private final BigDecimal grossAmount;
    private final BigDecimal feeAmount;
    private final BigDecimal netAmount;

    public SettlementRecord(
            String merchantId, LocalDate tradeDate, BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal netAmount) {
        this.merchantId = merchantId;
        this.tradeDate = tradeDate;
        this.grossAmount = grossAmount;
        this.feeAmount = feeAmount;
        this.netAmount = netAmount;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }
}
