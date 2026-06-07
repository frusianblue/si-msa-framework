package com.example.batchtypes.jobs.mybatis;

import java.math.BigDecimal;

/**
 * MyBatis 매핑 행. <b>JavaBean</b>(무인자 생성자 + getter/setter): MyBatis 가 resultType 매핑은 setter 로,
 * insert 파라미터({@code #{merchantId}})는 getter 로 다룬다(record 매핑 불확실성 회피).
 */
public class SettlementMb {

    private Long id;
    private String merchantId;
    private BigDecimal amount;

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
}
