package com.company.framework.idgen.code;

import com.company.framework.idgen.sequence.SequenceStore;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DefaultCodeGenerator implements CodeGenerator {

    private final SequenceStore store;
    private final int defaultPad;

    public DefaultCodeGenerator(SequenceStore store, int defaultPad) {
        this.store = store;
        this.defaultPad = defaultPad;
    }

    @Override
    public String next(String prefix) {
        long n = store.next(prefix);
        return prefix + pad(n, defaultPad);
    }

    @Override
    public String next(String prefix, String datePattern, int pad) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern(datePattern));
        long n = store.next(prefix + ":" + date); // 키에 일자 포함 → 기간별 리셋
        return prefix + date + pad(n, pad);
    }

    private static String pad(long n, int width) {
        return String.format("%0" + width + "d", n);
    }
}
