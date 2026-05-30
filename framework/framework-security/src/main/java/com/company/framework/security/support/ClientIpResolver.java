package com.company.framework.security.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 클라이언트 IP 추출. 프록시/L7 LB/k8s 인그레스 뒤에서는 {@code getRemoteAddr()} 가 프록시 IP 이므로
 * 설정된 포워딩 헤더(기본 X-Forwarded-For)의 맨 앞(원 클라이언트) 값을 우선 사용한다.
 *
 * <p><b>주의(위조 가능):</b> X-Forwarded-For 는 클라이언트가 임의로 보낼 수 있다. <u>신뢰할 수 있는
 * 프록시가 헤더를 세팅/정규화하는 환경</u>에서만 의미가 있으며, 그렇지 않으면 공격자가 IP 를 회전시켜
 * 계정+IP 기반 잠금을 우회할 수 있다. 외부에 직접 노출되는 경우 헤더 신뢰를 끄고 {@code getRemoteAddr()}
 * 만 쓰도록 {@code client-ip-header} 를 비워 운영하는 편이 안전하다.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request, String forwardedHeader) {
        if (request == null) {
            return null;
        }
        if (forwardedHeader != null && !forwardedHeader.isBlank()) {
            String header = request.getHeader(forwardedHeader);
            if (header != null && !header.isBlank()) {
                int comma = header.indexOf(',');
                String first = (comma >= 0 ? header.substring(0, comma) : header).trim();
                if (!first.isBlank()) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
