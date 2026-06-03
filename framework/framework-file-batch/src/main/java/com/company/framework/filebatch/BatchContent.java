package com.company.framework.filebatch;

import java.io.IOException;
import java.io.InputStream;

/**
 * 아이템 본문 스트림 공급자. 호출될 때마다 처음부터 읽을 수 있는 <b>새 스트림</b>을 연다
 * (대용량도 메모리에 한꺼번에 적재하지 않도록 지연 개방 — archive 모듈 {@code ContentSupplier} 와 같은 결).
 */
@FunctionalInterface
public interface BatchContent {
    InputStream open() throws IOException;
}
