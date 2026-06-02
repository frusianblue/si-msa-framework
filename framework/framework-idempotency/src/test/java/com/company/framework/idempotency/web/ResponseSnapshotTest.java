package com.company.framework.idempotency.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResponseSnapshotTest {

    @Test
    @DisplayName("기본 라운드트립: 상태/콘텐츠타입/본문 보존")
    void roundTrip() {
        ResponseSnapshot in =
                new ResponseSnapshot(200, "application/json", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        ResponseSnapshot out = ResponseSnapshot.decode(in.encode());
        assertThat(out.status()).isEqualTo(200);
        assertThat(out.contentType()).isEqualTo("application/json");
        assertThat(out.body()).isEqualTo(in.body());
    }

    @Test
    @DisplayName("본문에 개행이 있어도 무손실(Base64 라 앞 두 개행만 끊음)")
    void bodyWithNewlines() {
        byte[] body = "line1\nline2\r\nline3".getBytes(StandardCharsets.UTF_8);
        ResponseSnapshot out = ResponseSnapshot.decode(new ResponseSnapshot(201, "text/plain", body).encode());
        assertThat(out.body()).isEqualTo(body);
        assertThat(out.status()).isEqualTo(201);
    }

    @Test
    @DisplayName("콘텐츠타입 null 은 빈칸으로 저장되고 null 로 복원")
    void nullContentType() {
        ResponseSnapshot out = ResponseSnapshot.decode(new ResponseSnapshot(204, null, new byte[0]).encode());
        assertThat(out.contentType()).isNull();
        assertThat(out.body()).isEmpty();
        assertThat(out.status()).isEqualTo(204);
    }

    @Test
    @DisplayName("바이너리 본문도 바이트 단위로 보존")
    void binaryBody() {
        byte[] body = {0, 1, 2, (byte) 0xFF, (byte) 0x80, 10, 13};
        ResponseSnapshot out =
                ResponseSnapshot.decode(new ResponseSnapshot(200, "application/octet-stream", body).encode());
        assertThat(out.body()).isEqualTo(body);
    }
}
