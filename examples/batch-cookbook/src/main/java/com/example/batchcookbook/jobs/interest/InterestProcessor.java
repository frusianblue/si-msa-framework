package com.example.batchcookbook.jobs.interest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * 이자 = 잔액 × 이율(원 단위 반올림). 잔액이 음수인 비정상 행은 {@link IllegalArgumentException} 을 던져
 * 스텝의 {@code skip(IllegalArgumentException)} 정책으로 건너뛰게 한다(한 건의 불량 데이터가 전체 잡을
 * 멈추지 않도록). {@code null} 을 반환하면 그 행은 라이터로 가지 않고 필터된다.
 */
public class InterestProcessor implements ItemProcessor<DepositRow, InterestRecord> {

    @Override
    public InterestRecord process(DepositRow in) {
        if (in.balance() == null || in.balance().signum() < 0) {
            throw new IllegalArgumentException("잔액이 음수/누락: accountNo=" + in.accountNo());
        }
        if (in.balance().signum() == 0) {
            return null; // 잔액 0 → 이자 없음, 결과 미생성(필터)
        }
        BigDecimal interest = in.balance().multiply(in.rate()).setScale(0, RoundingMode.HALF_UP);
        return new InterestRecord(in.accountNo(), in.balance(), interest);
    }
}
