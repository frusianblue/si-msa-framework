package com.company.framework.samlsp.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.samlsp.store.Saml2AuthnRequestCodec.Data;
import org.junit.jupiter.api.Test;

/**
 * {@link Saml2AuthnRequestCodec} 순수 라운드트립/내성(tamper) 검증. Spring Security/OpenSAML 무의존(JDK 단독) —
 * SamlAttributeMapper 와 같은 "순수 코어" 테스트로, 작성 환경(Shibboleth 차단)에서도 컴파일/실행된다.
 */
class Saml2AuthnRequestCodecTest {

    @Test
    void roundtrip_post_minimal() {
        Data in = new Data("POST", "PHNhbWw+", "rs-1", "https://idp/sso", "corp", "ARQ_abc", null, null);
        assertThat(Saml2AuthnRequestCodec.decode(Saml2AuthnRequestCodec.encode(in)))
                .isEqualTo(in);
    }

    @Test
    void roundtrip_post_null_relay_and_id() {
        Data in = new Data("POST", "PHNhbWw+", null, "https://idp/sso", "corp", null, null, null);
        assertThat(Saml2AuthnRequestCodec.decode(Saml2AuthnRequestCodec.encode(in)))
                .isEqualTo(in);
    }

    @Test
    void roundtrip_redirect_full() {
        Data in = new Data(
                "REDIRECT",
                "deflated==",
                "relay-state",
                "https://idp/sso?x=1",
                "corp",
                "ARQ_xyz",
                "RSA-SHA256",
                "c2ln");
        assertThat(Saml2AuthnRequestCodec.decode(Saml2AuthnRequestCodec.encode(in)))
                .isEqualTo(in);
    }

    @Test
    void empty_string_relay_preserved_distinct_from_null() {
        Data emptyRelay = new Data("POST", "X", "", "https://idp/sso", "c", "i", null, null);
        Data decoded = Saml2AuthnRequestCodec.decode(Saml2AuthnRequestCodec.encode(emptyRelay));
        assertThat(decoded).isNotNull();
        assertThat(decoded.relayState()).isEmpty();

        Data nullRelay = new Data("POST", "X", null, "https://idp/sso", "c", "i", null, null);
        assertThat(Saml2AuthnRequestCodec.decode(Saml2AuthnRequestCodec.encode(nullRelay))
                        .relayState())
                .isNull();
    }

    @Test
    void values_with_delimiters_and_unicode_survive() {
        Data in = new Data(
                "POST", "X", "줄바꿈\n콜론:콤마,파이프|탭\t끝", "https://idp/sso?a=b&c=한글", "corp-한", "ID\n:,|", null, null);
        assertThat(Saml2AuthnRequestCodec.decode(Saml2AuthnRequestCodec.encode(in)))
                .isEqualTo(in);
    }

    @Test
    void post_drops_redirect_only_fields() {
        Data in = new Data("POST", "X", "r", "https://idp/sso", "c", "i", "SHOULD_DROP", "SHOULD_DROP");
        Data out = Saml2AuthnRequestCodec.decode(Saml2AuthnRequestCodec.encode(in));
        assertThat(out).isNotNull();
        assertThat(out.sigAlg()).isNull();
        assertThat(out.signature()).isNull();
    }

    @Test
    void corrupt_or_tampered_input_decodes_to_null() {
        assertThat(Saml2AuthnRequestCodec.decode(null)).isNull();
        assertThat(Saml2AuthnRequestCodec.decode("")).isNull();
        assertThat(Saml2AuthnRequestCodec.decode("not-a-blob")).isNull();
        assertThat(Saml2AuthnRequestCodec.decode("xxxx\nPOST\n1X\n0\n1aHR0cA==\n0\n0\n0\n0"))
                .isNull(); // wrong magic
        assertThat(Saml2AuthnRequestCodec.decode("siv1\nPOST\n1X")).isNull(); // too few fields
        assertThat(Saml2AuthnRequestCodec.decode("siv1\nFOO\n1X\n0\n1aHR0cA==\n0\n0\n0\n0"))
                .isNull(); // bad binding

        String good =
                Saml2AuthnRequestCodec.encode(new Data("REDIRECT", "X", "r", "https://idp/sso", "c", "i", "a", "s"));
        assertThat(Saml2AuthnRequestCodec.decode(good + "\nEXTRA")).isNull(); // appended junk
    }

    @Test
    void blank_required_fields_rejected() {
        assertThatThrownBy(() -> Saml2AuthnRequestCodec.encode(
                        new Data("POST", "  ", "r", "https://idp/sso", "c", "i", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Saml2AuthnRequestCodec.encode(new Data("POST", "X", "r", "", "c", "i", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
