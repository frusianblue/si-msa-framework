package com.company.framework.logmask.config;

import com.company.framework.logmask.mask.KoreanPiiRules;
import com.company.framework.logmask.mask.MaskingRule;
import com.company.framework.logmask.mask.SensitiveDataMasker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 로그 마스킹 자동설정. {@code framework.log-masking.enabled=true} 일 때만 활성(선택형 모듈 컨벤션).
 *
 * <p>노출 빈
 *
 * <ul>
 *   <li>{@link SensitiveDataMasker} — 프로퍼티로 조립한 마스킹 엔진. 감사로그/응답 등에 직접 주입해 쓰는 1차 경로.
 *       앱이 같은 타입 빈을 직접 정의하면 그쪽이 우선({@code @ConditionalOnMissingBean}).
 *   <li>{@link LogMaskingInstaller} — Logback {@code %mmsg} 컨버터용 정적 다리에 마스커를 설치.
 *       {@code install-converter=true}(기본)일 때만 생성.
 * </ul>
 *
 * <p>core 의 {@code MaskingUtils} 외 새 의존성 없음. Logback 은 Boot 기본 로깅이라 런타임 상존.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "framework.log-masking", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LogMaskingProperties.class)
public class LogMaskingAutoConfiguration {

    /** 프로퍼티 토글에 따라 내장 PII 규칙 + 커스텀 패턴을 조립한 마스킹 엔진. */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveDataMasker sensitiveDataMasker(LogMaskingProperties props) {
        List<MaskingRule> rules = new ArrayList<>();
        LogMaskingProperties.Rules r = props.getRules();
        // 순서: 자릿수가 긴 카드 → 주민번호 → 휴대폰 → 이메일 → 계좌 → 커스텀.
        if (r.isCard()) {
            rules.add(KoreanPiiRules.card());
        }
        if (r.isRrn()) {
            rules.add(KoreanPiiRules.rrn());
        }
        if (r.isPhone()) {
            rules.add(KoreanPiiRules.phone());
        }
        if (r.isEmail()) {
            rules.add(KoreanPiiRules.email());
        }
        if (r.isAccount()) {
            rules.add(KoreanPiiRules.account());
        }
        for (Map.Entry<String, String> e : props.getCustomPatterns().entrySet()) {
            rules.add(MaskingRule.fullMask(e.getKey(), e.getValue()));
        }
        return new SensitiveDataMasker(rules, props.isStripNewlines(), props.getMaxLength());
    }

    /** Logback 컨버터용 정적 다리에 마스커를 설치하는 라이프사이클 빈. install-converter=true(기본)일 때만. */
    @Bean
    @ConditionalOnProperty(
            prefix = "framework.log-masking",
            name = "install-converter",
            havingValue = "true",
            matchIfMissing = true)
    public LogMaskingInstaller logMaskingInstaller(SensitiveDataMasker sensitiveDataMasker) {
        return new LogMaskingInstaller(sensitiveDataMasker);
    }
}
