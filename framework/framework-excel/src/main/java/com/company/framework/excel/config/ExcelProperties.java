package com.company.framework.excel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Excel 모듈 토글/튜닝.
 *
 * <pre>{@code
 * framework:
 *   excel:
 *     enabled: false              # 선택형 → 명시적 on 필요. 켜면 ExcelExporter/ExcelImporter 빈 제공.
 *     export:
 *       window-size: 100          # SXSSF 스트리밍 윈도(메모리에 유지할 행 수). 클수록 빠르고 메모리↑.
 *     import:
 *       max-rows: 100000          # 업로드 데이터 행 상한(헤더 제외). 초과 시 INVALID_INPUT.
 * }</pre>
 */
@ConfigurationProperties(prefix = "framework.excel")
public class ExcelProperties {

    /** 선택형 → 기본 off. */
    private boolean enabled = false;

    private final Export export = new Export();
    private final Import importing = new Import();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Export getExport() {
        return export;
    }

    public Import getImport() {
        return importing;
    }

    public static class Export {
        /** SXSSF rowAccessWindowSize. 1 이상. */
        private int windowSize = 100;

        public int getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(int windowSize) {
            this.windowSize = windowSize;
        }
    }

    public static class Import {
        /** 업로드 데이터 행 상한(헤더 제외). 메모리 보호용. */
        private int maxRows = 100_000;

        public int getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows;
        }
    }
}
