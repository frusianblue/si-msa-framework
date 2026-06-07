package com.example.batchtypes.jobs.multithreaded;

/** 등급 산정 결과(라이터 beanMapped → 게터 클래스). */
public class GradedTxn {

    private final Long id;
    private final String merchantId;
    private final String grade;

    public GradedTxn(Long id, String merchantId, String grade) {
        this.id = id;
        this.merchantId = merchantId;
        this.grade = grade;
    }

    public Long getId() {
        return id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getGrade() {
        return grade;
    }
}
