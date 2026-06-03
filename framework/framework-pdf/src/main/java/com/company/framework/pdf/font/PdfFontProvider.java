package com.company.framework.pdf.font;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PDF 폰트 공급자. 한글 출력의 핵심: TrueType 폰트(NanumGothic 등)를 {@link BaseFont#IDENTITY_H} 인코딩으로
 * <b>임베딩</b>해, 폰트가 설치되지 않은 임의의 뷰어/OS 에서도 동일하게 한글이 렌더되도록 한다.
 *
 * <p>생성 시 폰트 바이트가 주어지면 임베딩 {@link BaseFont} 를 한 번 만들어 캐시한다. 바이트가 없거나(미설정) 파싱에
 * 실패하면 내장 라틴 폰트({@link Font#HELVETICA})로 <b>폴백</b>한다 — 이 경우 한글 글리프가 비어 보일 수 있으나 생성은
 * 실패하지 않는다(운영에서는 반드시 한글 TTF 경로를 지정).
 *
 * <p>OpenPDF 의 {@code BaseFont}/{@code Font} 는 모듈 내부 타입이다. 본 클래스는 익스포터 내부 협력자이며, 소비
 * 서비스에 OpenPDF 타입을 노출하지 않는다(빌드 의존성이 {@code implementation} 이라 컴파일 노출도 차단됨).
 */
public class PdfFontProvider {

    private static final Logger log = LoggerFactory.getLogger(PdfFontProvider.class);

    private final BaseFont baseFont; // null 이면 라틴 폴백

    /**
     * @param fontBytes 임베딩할 TTF/OTF 바이트(없으면 {@code null} → 라틴 폴백)
     * @param fontName 파서 힌트용 이름(확장자 {@code .ttf}/{@code .otf} 포함). 임베딩 폰트는 자체 글리프를 쓰므로 이름은 식별용.
     */
    public PdfFontProvider(byte[] fontBytes, String fontName) {
        this.baseFont = tryCreate(fontBytes, fontName);
    }

    private static BaseFont tryCreate(byte[] fontBytes, String fontName) {
        if (fontBytes == null || fontBytes.length == 0) {
            return null;
        }
        String name = (fontName == null || fontName.isBlank()) ? "embedded.ttf" : fontName.trim();
        try {
            // cached=true: 동일 이름 폰트 재사용. 마지막 두 인자(ttf/pfb 바이트)로 파일 없이 메모리에서 임베딩.
            return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
        } catch (DocumentException | IOException e) {
            log.warn("PDF 임베딩 폰트 생성 실패(name={}) — 라틴 기본 폰트로 폴백합니다: {}", name, e.getMessage());
            return null;
        }
    }

    /** 임베딩 폰트가 적재됐는지. false 면 라틴 폴백 중(한글이 깨질 수 있음). */
    public boolean hasEmbeddedFont() {
        return baseFont != null;
    }

    /** 본문(일반) 폰트. */
    public Font body(float size) {
        return font(size, Font.NORMAL);
    }

    /** 강조(볼드) 폰트 — 제목/헤더용. */
    public Font bold(float size) {
        return font(size, Font.BOLD);
    }

    private Font font(float size, int style) {
        if (baseFont != null) {
            return new Font(baseFont, size, style);
        }
        return new Font(Font.HELVETICA, size, style);
    }
}
