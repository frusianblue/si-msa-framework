package com.company.framework.samlsp.store;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SAML AuthnRequest in-flight 데이터의 <b>고정형(fixed-shape) 직렬화 코덱</b>. Spring Security / OpenSAML 타입에
 * 무의존한 순수 로직(코어 분리)이라 JDK 단독으로 검증한다(SamlAttributeMapper 와 동일한 "순수 코어" 패턴).
 *
 * <p><b>왜 Java 네이티브 직렬화/Jackson 이 아니라 수기 고정형인가:</b> {@code AbstractSaml2AuthenticationRequest} 는
 * {@code Serializable} 이지만, 파드/SS 버전 간 네이티브 직렬화는 {@code serialVersionUID}·클래스 진화에 취약하고
 * 바이너리가 불투명하다(되돌리지 말 것 — HANDOFF 함정). Jackson 리플렉션 역시 깨지기 쉬워, 이 프레임워크는
 * "값이 고정 형태일 때 수기 직렬화가 더 견고하다"는 원칙을 따른다. 여기서는 필드를 명시 열거하고 각 값을
 * Base64(UTF-8)로 감싸 개행으로 구분한다 — Base64 알파벳({@code A–Z a–z 0–9 + / =})에 개행이 없으므로
 * 구분자 충돌·이스케이프 버그가 원천적으로 불가능하다. 맨 앞 매직/버전 라인으로 포맷 진화 시 깨끗하게 거부한다.
 *
 * <p><b>포맷(v1, 9라인):</b>
 *
 * <pre>
 *   0: "siv1"                 (매직+버전)
 *   1: REDIRECT | POST        (바인딩 — 고정 토큰)
 *   2: 1{b64(samlRequest)}    (필수)
 *   3: {presence}{b64(relayState)}
 *   4: 1{b64(authenticationRequestUri)} (필수)
 *   5: {presence}{b64(relyingPartyRegistrationId)}
 *   6: {presence}{b64(id)}
 *   7: {presence}{b64(sigAlg)}    (REDIRECT 전용, POST 면 부재)
 *   8: {presence}{b64(signature)} (REDIRECT 전용)
 * </pre>
 *
 * presence 플래그: {@code '1'}=값 있음(빈 문자열 포함), {@code '0'}=null. 따라서 빈 문자열과 null 을 구분한다.
 * 손상/단축/매직불일치/필수누락 입력은 {@link #decode(String)} 가 {@code null} 을 반환한다(예외 대신) — 저장소는
 * 이를 "보관된 요청 없음"으로 처리해 Spring Security 가 명확히 인증 실패시킨다(조용한 손상 전파 금지).
 */
public final class Saml2AuthnRequestCodec {

    /** 매직 + 포맷 버전. 포맷 변경 시 증가시켜 구버전 값을 깨끗이 거부한다. */
    static final String MAGIC = "siv1";

    static final String BINDING_REDIRECT = "REDIRECT";
    static final String BINDING_POST = "POST";

    private static final char SEP = '\n';
    private static final char PRESENT = '1';
    private static final char ABSENT = '0';
    private static final int FIELD_COUNT = 9;

    private Saml2AuthnRequestCodec() {}

    /**
     * 바인딩에 무관한 AuthnRequest 필드 묶음(순수 데이터, SS 타입 무의존). 저장소가 SS 타입과 상호 변환한다.
     * REDIRECT 가 아니면 {@code sigAlg}/{@code signature} 는 무시(null)된다.
     */
    public record Data(
            String binding,
            String samlRequest,
            String relayState,
            String authenticationRequestUri,
            String relyingPartyRegistrationId,
            String id,
            String sigAlg,
            String signature) {

        public boolean isRedirect() {
            return BINDING_REDIRECT.equals(binding);
        }
    }

    /** {@link Data} → redis 저장 문자열(고정형). 필수값(binding/samlRequest/uri)이 비면 {@link IllegalArgumentException}. */
    public static String encode(Data d) {
        if (d == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        String binding = d.isRedirect() ? BINDING_REDIRECT : BINDING_POST;
        if (isBlank(d.samlRequest()) || isBlank(d.authenticationRequestUri())) {
            throw new IllegalArgumentException("samlRequest/authenticationRequestUri must not be blank");
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append(MAGIC).append(SEP);
        sb.append(binding).append(SEP);
        field(sb, d.samlRequest()).append(SEP);
        field(sb, d.relayState()).append(SEP);
        field(sb, d.authenticationRequestUri()).append(SEP);
        field(sb, d.relyingPartyRegistrationId()).append(SEP);
        field(sb, d.id()).append(SEP);
        // POST 는 sigAlg/signature 가 없으므로 명시적으로 부재 처리(REDIRECT 만 값 보존).
        field(sb, d.isRedirect() ? d.sigAlg() : null).append(SEP);
        field(sb, d.isRedirect() ? d.signature() : null);
        return sb.toString();
    }

    /** redis 저장 문자열 → {@link Data}. 형식 불일치/손상/필수누락이면 {@code null}(예외 아님). */
    public static Data decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split("\n", -1); // -1: 후행 빈 토큰 보존
        if (parts.length != FIELD_COUNT || !MAGIC.equals(parts[0])) {
            return null;
        }
        String binding = parts[1];
        if (!BINDING_REDIRECT.equals(binding) && !BINDING_POST.equals(binding)) {
            return null;
        }
        String samlRequest = value(parts[2]);
        String relayState = value(parts[3]);
        String uri = value(parts[4]);
        String rpId = value(parts[5]);
        String id = value(parts[6]);
        String sigAlg = value(parts[7]);
        String signature = value(parts[8]);
        // 필수: samlRequest/uri 가 비면 SS 빌더가 Assert.hasText 로 던지므로 미리 무효 처리.
        if (isBlank(samlRequest) || isBlank(uri)) {
            return null;
        }
        return new Data(binding, samlRequest, relayState, uri, rpId, id, sigAlg, signature);
    }

    private static StringBuilder field(StringBuilder sb, String v) {
        if (v == null) {
            return sb.append(ABSENT);
        }
        return sb.append(PRESENT).append(Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8)));
    }

    /** 한 라인("{flag}{b64}")을 원래 값으로. flag='0' → null, '1' → 디코딩(빈 b64 → ""). 형식 위반 시 null. */
    private static String value(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        char flag = line.charAt(0);
        if (flag == ABSENT) {
            return null;
        }
        if (flag != PRESENT) {
            return null;
        }
        String b64 = line.substring(1);
        if (b64.isEmpty()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
