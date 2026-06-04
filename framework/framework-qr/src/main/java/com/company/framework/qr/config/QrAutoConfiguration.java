package com.company.framework.qr.config;

import com.company.framework.qr.QrGenerator;
import com.company.framework.qr.QrSpec;
import com.company.framework.qr.ZxingQrGenerator;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * QR 모듈 오토컨피그. {@code framework.qr.enabled=true} 이고 ZXing({@link QRCodeWriter})이 클래스패스에 있을 때만
 * {@link QrGenerator} 빈을 제공한다({@code @ConditionalOnClass} 백오프).
 *
 * <p>웹 비의존(배치/스케줄 등 비웹 컨텍스트에서도 사용 가능). 앱이 {@link QrGenerator} 빈을 직접 정의하면
 * 그쪽이 우선({@code @ConditionalOnMissingBean}).
 */
@AutoConfiguration
@ConditionalOnClass(QRCodeWriter.class)
@ConditionalOnProperty(prefix = "framework.qr", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(QrProperties.class)
public class QrAutoConfiguration {

    /** 기본 QR 생성기(ZXing 인코딩 + ImageIO 렌더링). 기본 스펙/내용 길이 상한을 프로퍼티에서 주입. */
    @Bean
    @ConditionalOnMissingBean
    public QrGenerator qrGenerator(QrProperties props) {
        QrSpec defaultSpec = QrSpec.builder()
                .sizePx(props.getDefaultSizePx())
                .margin(props.getDefaultMargin())
                .eccLevel(props.getDefaultEccLevel())
                .charset(props.getDefaultCharset())
                .build();
        return new ZxingQrGenerator(defaultSpec, props.getMaxContentLength());
    }
}
