package com.example.batchcookbook.jobs.fileingest;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 파일 적재 한 행. <b>JavaBean</b>(무인자 생성자 + getter/setter)으로 둔 이유:
 * 라이터 {@code JdbcBatchItemWriterBuilder.beanMapped()} 가 {@code :merchantId} 같은 네임드 파라미터를
 * <b>게터</b>로 채우기 때문이다. record 접근자({@code merchantId()})는 게터 규약({@code getMerchantId()})이
 * 아니라 못 잡는다(PITFALLS §3). 그래서 record 대신 JavaBean.
 */
public class IncomingTransaction {

    private Long id;
    private String merchantId;
    private BigDecimal amount;
    private String status;
    private LocalDate tradeDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }
}
