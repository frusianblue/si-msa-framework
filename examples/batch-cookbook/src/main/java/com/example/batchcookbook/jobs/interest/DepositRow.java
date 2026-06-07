package com.example.batchcookbook.jobs.interest;

import java.math.BigDecimal;

/**
 * 이자 계산 입력 한 행. <b>record 로 둬도 된다</b> — 리더가 만들어 내는 입력 타입은 게터 규약이 필요 없다.
 * (게터가 필요한 건 라이터의 {@code beanMapped()}/{@code names()} 쪽. → {@link InterestRecord} 참고.)
 */
public record DepositRow(Long id, String accountNo, BigDecimal balance, BigDecimal rate) {}
