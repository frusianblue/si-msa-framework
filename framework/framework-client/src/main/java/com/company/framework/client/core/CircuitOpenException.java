package com.company.framework.client.core;

import java.io.IOException;

/** 서킷이 OPEN 이라 호출을 차단했을 때. (IOException 계열로 던져 RestClient 흐름과 일관) */
public class CircuitOpenException extends IOException {
    private static final long serialVersionUID = 1L;

    public CircuitOpenException(String host) {
        super("circuit open for host: " + host);
    }
}
