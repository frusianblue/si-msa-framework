package com.example.batchtypes.jobs.composite;

import java.math.BigDecimal;

/** 보강 결과(라이터 beanMapped → 게터 클래스). */
public class EnrichedTxn {

    private final Long id;
    private final String merchantId;
    private final BigDecimal amount;
    private final BigDecimal fee;
    private final BigDecimal netAmount;
    private final String category;

    public EnrichedTxn(Long id, String merchantId, BigDecimal amount, BigDecimal fee, BigDecimal netAmount, String category) {
        this.id = id;
        this.merchantId = merchantId;
        this.amount = amount;
        this.fee = fee;
        this.netAmount = netAmount;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public String getCategory() {
        return category;
    }
}
