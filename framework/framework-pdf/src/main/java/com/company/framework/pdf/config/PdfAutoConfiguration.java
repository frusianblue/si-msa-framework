package com.company.framework.pdf.config;

import com.company.framework.pdf.exporter.PdfExporter;
import com.company.framework.pdf.font.PdfFontProvider;
import com.company.framework.pdf.model.PdfLayout;
import com.lowagie.text.Document;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * PDF 모듈 오토컨피그.
 *
 * <ul>
 *   <li>1단(모듈): {@code @ConditionalOnClass(Document)} — OpenPDF 가 클래스패스에 있어야 활성.
 *   <li>2단(기능): {@code framework.pdf.enabled=true} → {@link PdfFontProvider}/{@link PdfExporter} 빈 제공.
 * </ul>
 *
 * <p>폰트는 {@code framework.pdf.font.location}(Spring Resource) 을 읽어 임베딩한다. 미설정/적재 실패 시 경고 후
 * 라틴 폴백 — 한글 산출물은 반드시 한글 TTF 경로를 지정해야 한다.
 */
@AutoConfiguration
@ConditionalOnClass(Document.class)
@ConditionalOnProperty(prefix = "framework.pdf", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PdfProperties.class)
public class PdfAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PdfAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public PdfFontProvider pdfFontProvider(PdfProperties properties, ResourceLoader resourceLoader) {
        String location = properties.getFont().getLocation();
        if (location == null || location.isBlank()) {
            log.warn("framework.pdf.font.location 미설정 — 라틴 기본 폰트로 폴백합니다(한글이 깨질 수 있음)."
                    + " 한글 산출물은 NanumGothic 등 TTF 경로를 지정하세요.");
            return new PdfFontProvider(null, null);
        }
        try {
            Resource resource = resourceLoader.getResource(location.trim());
            if (!resource.exists()) {
                log.warn("PDF 폰트 리소스를 찾지 못했습니다: {} — 라틴 폴백.", location);
                return new PdfFontProvider(null, null);
            }
            byte[] bytes;
            try (InputStream in = resource.getInputStream()) {
                bytes = in.readAllBytes();
            }
            return new PdfFontProvider(bytes, fontName(resource.getFilename()));
        } catch (IOException e) {
            log.warn("PDF 폰트 로딩 실패: {} ({}) — 라틴 폴백.", location, e.getMessage());
            return new PdfFontProvider(null, null);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public PdfExporter pdfExporter(PdfFontProvider fonts, PdfProperties properties) {
        PdfLayout layout = new PdfLayout(
                properties.getPageSize(),
                properties.isLandscape(),
                properties.getMargin(),
                properties.getTitleFontSize(),
                properties.getHeaderFontSize(),
                properties.getBodyFontSize(),
                properties.isPageNumber());
        return new PdfExporter(fonts, layout);
    }

    /** BaseFont 파서 힌트용 이름. 확장자가 ttf/otf 가 아니면 .ttf 로 가정. */
    private static String fontName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "embedded.ttf";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
            return filename;
        }
        return filename + ".ttf";
    }
}
