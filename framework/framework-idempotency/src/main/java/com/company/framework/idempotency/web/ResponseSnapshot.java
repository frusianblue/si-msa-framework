package com.company.framework.idempotency.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;

/**
 * 멱등 재생(replay)용 응답 스냅샷. 저장 포맷은 고정 셰이프 {@code "<status>\n<contentType>\n<base64(body)>"}.
 *
 * <p>status(숫자)·contentType(개행 없음)이 앞서고 본문은 Base64(개행 없음, {@link Base64#getEncoder()})로 인코딩하므로
 * 앞 두 개행만 끊으면 무손실 복원된다 — JSON 직렬화/이스케이프·문자셋 가정 불필요(임의 바이너리/문자셋 본문 안전).
 * 프레임워크의 "깨지기 쉬운 직렬화 대신 고정 셰이프 수기 인코딩" 원칙과 동일 결.
 */
public record ResponseSnapshot(int status, String contentType, byte[] body) {

    /** 저장 문자열로 인코딩. */
    public String encode() {
        return status + "\n" + (contentType == null ? "" : contentType) + "\n"
                + Base64.getEncoder().encodeToString(body);
    }

    /** 저장 문자열에서 복원. 포맷은 항상 {@link #encode()}가 생성하므로 고정 셰이프를 가정한다. */
    public static ResponseSnapshot decode(String snapshot) {
        int firstNl = snapshot.indexOf('\n');
        int secondNl = snapshot.indexOf('\n', firstNl + 1);
        int status = Integer.parseInt(snapshot.substring(0, firstNl));
        String contentType = snapshot.substring(firstNl + 1, secondNl);
        byte[] body = Base64.getDecoder().decode(snapshot.substring(secondNl + 1));
        return new ResponseSnapshot(status, contentType.isEmpty() ? null : contentType, body);
    }

    /** 저장된 응답을 클라이언트에 그대로 재생(상태/콘텐츠타입/본문 동일). */
    public void writeTo(HttpServletResponse res) throws IOException {
        res.setStatus(status);
        if (contentType != null) {
            res.setContentType(contentType);
        }
        res.setContentLength(body.length);
        res.getOutputStream().write(body);
    }
}
