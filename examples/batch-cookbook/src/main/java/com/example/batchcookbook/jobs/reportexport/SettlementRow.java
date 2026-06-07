package com.example.batchcookbook.jobs.reportexport;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 추출(export) 한 행. 라이터의 {@code delimited().names("merchantId","tradeDate","netAmount")} 가
 * <b>게터</b>로 각 필드를 뽑아 CSV 한 줄로 만든다(BeanWrapperFieldExtractor). 그래서 record 가 아니라
 * 게터를 가진 클래스로 둔다(PITFALLS §3 의 beanMapped 와 같은 이유).
 */
public class SettlementRow {

    private final String merchantId;
    private final LocalDate tradeDate;
    private final BigDecimal netAmount;

    public SettlementRow(String merchantId, LocalDate tradeDate, BigDecimal netAmount) {
        this.merchantId = merchantId;
        this.tradeDate = tradeDate;
        this.netAmount = netAmount;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }
}
