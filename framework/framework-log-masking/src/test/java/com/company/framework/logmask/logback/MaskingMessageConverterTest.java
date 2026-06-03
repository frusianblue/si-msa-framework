package com.company.framework.logmask.logback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.company.framework.logmask.mask.MaskingRule;
import com.company.framework.logmask.mask.SensitiveDataMasker;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Logback 컨버터 + 정적 다리 검증. 컨버터는 {@code event.getFormattedMessage()} 의 최종 텍스트(인자 치환 후)를
 * {@link MaskingSupport} 로 마스킹한다. 설치된 마스커가 없어도 내장 기본 규칙으로 폴백함을 확인한다.
 */
class MaskingMessageConverterTest {

    private final MaskingMessageConverter converter = new MaskingMessageConverter();

    @AfterEach
    void tearDown() {
        // 정적 상태 → 테스트 격리를 위해 매번 해제.
        MaskingSupport.clear();
    }

    private ILoggingEvent eventWith(String formatted) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn(formatted);
        return event;
    }

    @Test
    @DisplayName("설치된 마스커로 포맷된 메시지를 마스킹한다")
    void masksWithInstalledMasker() {
        MaskingSupport.setMasker(SensitiveDataMasker.withDefaults());
        String out = converter.convert(eventWith("login 010-1234-5678"));
        assertThat(out).isEqualTo("login 010-****-5678");
    }

    @Test
    @DisplayName("마스커 미설치 시에도 내장 기본 규칙으로 폴백 마스킹한다")
    void fallsBackToDefaultsWhenNotInstalled() {
        assertThat(MaskingSupport.isInstalled()).isFalse();
        String out = converter.convert(eventWith("rrn 900101-1234567"));
        assertThat(out).isEqualTo("rrn 900101-1******");
    }

    @Test
    @DisplayName("설치된 마스커의 커스텀 규칙이 반영된다")
    void appliesInstalledCustomRule() {
        MaskingSupport.setMasker(new SensitiveDataMasker(List.of(MaskingRule.fullMask("emp", "EMP\\d{6}")), true, 0));
        assertThat(converter.convert(eventWith("emp EMP123456"))).isEqualTo("emp *********");
    }

    @Test
    @DisplayName("clear 후에는 다시 폴백으로 동작한다")
    void clearRestoresFallback() {
        MaskingSupport.setMasker(new SensitiveDataMasker(List.of(), true, 0));
        MaskingSupport.clear();
        assertThat(MaskingSupport.isInstalled()).isFalse();
        // 폴백(기본 규칙)이 카드 마스킹을 수행.
        assertThat(converter.convert(eventWith("card 1234-5678-9012-3456"))).isEqualTo("card 1234-****-****-3456");
    }
}
