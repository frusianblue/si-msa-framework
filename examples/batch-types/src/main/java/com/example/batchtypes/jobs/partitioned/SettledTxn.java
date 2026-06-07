package com.example.batchtypes.jobs.partitioned;

import java.math.BigDecimal;

/** 파티션 워커의 정산 결과(라이터 beanMapped → 게터 클래스). */
public class SettledTxn {

    private final Long id;
    private final String merchantId;
    private final BigDecimal netAmount;

    public SettledTxn(Long id, String merchantId, BigDecimal netAmount) {
        this.id = id;
        this.merchantId = merchantId;
        this.netAmount = netAmount;
    }

    public Long getId() {
        return id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }
}
