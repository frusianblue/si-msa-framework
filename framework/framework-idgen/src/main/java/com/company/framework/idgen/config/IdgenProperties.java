package com.company.framework.idgen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 채번 토글/설정.
 * framework:
 *   idgen:
 *     enabled: true
 *     snowflake:
 *       node-id: -1            # -1 = HOSTNAME 으로 자동 산출. 엄격 유일성은 인스턴스별 명시 권장
 *       epoch: 1704067200000   # 2024-01-01 UTC 기준
 *     sequence:
 *       table-name: id_sequence
 *       initialize: true       # 시작 시 CREATE TABLE IF NOT EXISTS
 *       default-pad: 6
 */
@ConfigurationProperties(prefix = "framework.idgen")
public class IdgenProperties {

    private boolean enabled = false;
    private final Snowflake snowflake = new Snowflake();
    private final Sequence sequence = new Sequence();

    public static class Snowflake {
        private long nodeId = -1L;
        private long epoch = 1704067200000L;

        public long getNodeId() {
            return nodeId;
        }

        public void setNodeId(long nodeId) {
            this.nodeId = nodeId;
        }

        public long getEpoch() {
            return epoch;
        }

        public void setEpoch(long epoch) {
            this.epoch = epoch;
        }
    }

    public static class Sequence {
        private String tableName = "id_sequence";
        private boolean initialize = true;
        private int defaultPad = 6;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public boolean isInitialize() {
            return initialize;
        }

        public void setInitialize(boolean initialize) {
            this.initialize = initialize;
        }

        public int getDefaultPad() {
            return defaultPad;
        }

        public void setDefaultPad(int defaultPad) {
            this.defaultPad = defaultPad;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Snowflake getSnowflake() {
        return snowflake;
    }

    public Sequence getSequence() {
        return sequence;
    }
}
