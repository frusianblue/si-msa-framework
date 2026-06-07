package com.example.settlement.batch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * 거래 → 정산 결과 변환(수수료 1.5% 차감).
 *
 * <p>{@code APPROVED} 가 아닌 거래는 {@code null} 반환 → 청크에서 <b>필터(write 제외)</b> 된다.
 * 금액이 비정상(음수)인 거래는 예외를 던져 스텝의 {@code faultTolerant().skip(...)} 정책으로 <b>스킵</b>되게 한다
 * (한 건 오류가 전체 정산을 멈추지 않도록 — 운영 배치의 기본 내성 패턴).
 */
public class SettlementProcessor implements ItemProcessor<RawTransaction, SettlementRecord> {

    private static final Logger log = LoggerFactory.getLogger(SettlementProcessor.class);
    private static final BigDecimal FEE_RATE = new BigDecimal("0.015");

    @Override
    public SettlementRecord process(RawTransaction tx) {
        if (!"APPROVED".equals(tx.status())) {
            return null; // 승인건만 정산 → 그 외는 필터
        }
        if (tx.amount() == null || tx.amount().signum() < 0) {
            throw new IllegalArgumentException("비정상 금액: txId=" + tx.id() + " amount=" + tx.amount());
        }
        BigDecimal gross = tx.amount();
        BigDecimal fee = gross.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(fee);
        log.debug("settle txId={} merchant={} gross={} fee={} net={}", tx.id(), tx.merchantId(), gross, fee, net);
        return new SettlementRecord(tx.merchantId(), tx.tradeDate(), gross, fee, net);
    }
}
