package com.example.batchcookbook.jobs.interest;

import java.math.BigDecimal;

/** 이자 계산 결과 한 행. 라이터 {@code beanMapped()} 가 게터로 네임드 파라미터를 채운다 → 게터 클래스. */
public class InterestRecord {

    private final String accountNo;
    private final BigDecimal baseBalance;
    private final BigDecimal interest;

    public InterestRecord(String accountNo, BigDecimal baseBalance, BigDecimal interest) {
        this.accountNo = accountNo;
        this.baseBalance = baseBalance;
        this.interest = interest;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public BigDecimal getBaseBalance() {
        return baseBalance;
    }

    public BigDecimal getInterest() {
        return interest;
    }
}
