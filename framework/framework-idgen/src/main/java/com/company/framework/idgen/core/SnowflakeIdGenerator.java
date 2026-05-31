package com.company.framework.idgen.core;

/**
 * Twitter Snowflake (64bit): [부호1][타임스탬프41][노드10][시퀀스12].
 * - DB 왕복 없음, 시간순 증가, 다중 인스턴스 고유(노드ID가 인스턴스별로 달라야 함).
 * - 시계 역행 시 따라잡을 때까지 스핀(짧은 NTP 보정 대응).
 */
public class SnowflakeIdGenerator implements IdGenerator {

    private static final long NODE_BITS = 10L;
    private static final long SEQ_BITS = 12L;
    private static final long MAX_NODE = (1L << NODE_BITS) - 1;
    private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;
    private static final long NODE_SHIFT = SEQ_BITS;
    private static final long TIME_SHIFT = SEQ_BITS + NODE_BITS;

    private final long epoch;
    private final long nodeId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long nodeId, long epoch) {
        if (nodeId < 0 || nodeId > MAX_NODE) {
            throw new IllegalArgumentException("nodeId 는 0~" + MAX_NODE + " 범위여야 합니다: " + nodeId);
        }
        this.nodeId = nodeId;
        this.epoch = epoch;
    }

    @Override
    public synchronized long nextLong() {
        long ts = System.currentTimeMillis();
        if (ts < lastTimestamp) {
            ts = waitUntil(lastTimestamp); // 시계 역행 → 마지막 시각까지 대기
        }
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQ;
            if (sequence == 0) {
                ts = waitUntil(lastTimestamp + 1); // 같은 ms 시퀀스 소진 → 다음 ms
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = ts;
        return ((ts - epoch) << TIME_SHIFT) | (nodeId << NODE_SHIFT) | sequence;
    }

    private long waitUntil(long target) {
        long t = System.currentTimeMillis();
        while (t < target) {
            t = System.currentTimeMillis();
        }
        return t;
    }
}
