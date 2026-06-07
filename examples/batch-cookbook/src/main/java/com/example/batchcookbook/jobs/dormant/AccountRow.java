package com.example.batchcookbook.jobs.dormant;

/** 휴면 전환 대상 한 행. 라이터 {@code beanMapped()} 가 {@code :id} 를 {@code getId()} 로 채운다. */
public class AccountRow {

    private final Long id;
    private final String owner;

    public AccountRow(Long id, String owner) {
        this.id = id;
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }
}
