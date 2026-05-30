package com.company.framework.core.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * 요청 파라미터/헤더의 HTML 특수문자를 이스케이프하여 저장형/반사형 XSS 를 1차 차단한다.
 * (RequestBody(JSON)는 별도 Deserializer 또는 출력 인코딩으로 보완 권장)
 */
public class XssRequestWrapper extends HttpServletRequestWrapper {

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        return clean(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) return null;
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = clean(values[i]);
        return result;
    }

    @Override
    public String getHeader(String name) {
        return clean(super.getHeader(name));
    }

    private String clean(String value) {
        if (value == null) return null;
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("(", "&#40;")
                .replace(")", "&#41;")
                .replaceAll("(?i)<script", "&lt;script")
                .replaceAll("(?i)javascript:", "");
    }
}
