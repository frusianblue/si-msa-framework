package com.company.framework.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * JWT 발급/검증/파싱 단위 테스트. 서명 위·변조 거부와 역할→권한 매핑(ROLE_ 접두) 규약을 고정한다.
 * 시계 의존을 피하려 만료 시각은 "미래"인지만 확인하고, 보안상 핵심인 "다른 키로는 검증 실패"를 명시 검증한다.
 */
class JwtProviderTest {

    // HS 서명용 충분히 긴(32바이트 이상) 비밀키
    private static final String SECRET = "0123456789-abcdefghij-ABCDEFGHIJ-0123456789";
    private static final String OTHER_SECRET = "ZZZZZZZZZZ-abcdefghij-ABCDEFGHIJ-9876543210";

    private final JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, 1800, 1209600));

    @Test
    @DisplayName("발급 토큰은 검증 통과 + subject/jti/만료 보존")
    void issueAndParse() {
        String token = provider.createAccessToken("user-1", List.of("USER"));
        assertThat(provider.validate(token)).isTrue();
        assertThat(provider.getSubject(token)).isEqualTo("user-1");
        assertThat(provider.getJti(token)).isNotBlank();
        assertThat(provider.getExpiresAt(token)).isAfter(Instant.now());
    }

    @Test
    @DisplayName("roles 클레임 → GrantedAuthority(ROLE_ 접두 정규화)")
    void rolesBecomeAuthorities() {
        // 접두 없는 USER 는 ROLE_USER 로, 이미 ROLE_ 인 것은 그대로.
        String token = provider.createAccessToken("user-2", List.of("USER", "ROLE_ADMIN"));
        Authentication auth = provider.getAuthentication(token);
        assertThat(auth.getName()).isEqualTo("user-2");
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("다른 비밀키로 서명검증 시 실패(위·변조 차단)")
    void rejectsWrongSignature() {
        String token = provider.createAccessToken("user-3", List.of("USER"));
        JwtProvider otherKeyProvider = new JwtProvider(new JwtProperties(OTHER_SECRET, 1800, 1209600));
        assertThat(otherKeyProvider.validate(token)).isFalse();
    }

    @Test
    @DisplayName("형식이 깨진 토큰은 검증 실패(예외 누출 없이 false)")
    void rejectsMalformed() {
        assertThat(provider.validate("not.a.jwt")).isFalse();
        assertThat(provider.validate("")).isFalse();
        assertThat(provider.validate("aaa.bbb.ccc")).isFalse();
    }
}
