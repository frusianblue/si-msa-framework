package com.company.authserver.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.framework.core.error.BusinessException;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.LoginCommand;
import com.company.framework.security.password.BcryptEnforcingPasswordEncoder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * DbAuthenticator 순수 단위 테스트(Spring 컨텍스트 불요).
 * 실제 운영 인코더(BcryptEnforcingPasswordEncoder)로 V7 seed 와 동일한 {bcrypt} 해시를 검증 →
 * "seed 비밀번호 포맷이 진짜 매칭되는지"까지 함께 확인한다.
 */
class DbAuthenticatorTest {

    // V7__app_user.sql 의 tester seed 와 동일한 저장 해시(= Test1234! 의 BCrypt).
    private static final String TESTER_HASH = "{bcrypt}$2b$10$APHJ9O6CEOEVioEv4IkIZ.b/qTBQ4uAIRzHrDyqQaJMKAekfASk2.";

    private final PasswordEncoder encoder =
            new BcryptEnforcingPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder(), false);

    private AppUser tester(boolean enabled) {
        AppUser u = new AppUser();
        u.setId(1L);
        u.setLoginId("tester");
        u.setPassword(TESTER_HASH);
        u.setName("테스터");
        u.setRole("USER");
        u.setEnabled(enabled);
        return u;
    }

    /** 주어진 사용자만 반환하는 인라인 스텁 매퍼(Mockito 불요). */
    private AppUserMapper mapperOf(AppUser user) {
        return loginId -> (user != null && user.getLoginId().equals(loginId)) ? Optional.of(user) : Optional.empty();
    }

    private LoginCommand login(String id, String pw) {
        return new LoginCommand(id, pw, Map.of());
    }

    @Test
    void 정상_로그인_성공() {
        DbAuthenticator auth = new DbAuthenticator(mapperOf(tester(true)), encoder);

        AuthenticatedUser result = auth.authenticate(login("tester", "Test1234!"));

        assertEquals("tester", result.userId());
        assertEquals("테스터", result.name());
        assertEquals(List.of("USER"), result.roles());
    }

    @Test
    void seed_해시는_실_인코더로_매칭된다() {
        // V7 seed 포맷({bcrypt}$2b$...)이 운영 인코더에서 실제로 통과하는지 직접 확인.
        assertTrue(encoder.matches("Test1234!", TESTER_HASH));
    }

    @Test
    void 틀린_비밀번호_거부() {
        DbAuthenticator auth = new DbAuthenticator(mapperOf(tester(true)), encoder);
        assertThrows(BusinessException.class, () -> auth.authenticate(login("tester", "wrong-password")));
    }

    @Test
    void 없는_사용자_거부() {
        DbAuthenticator auth = new DbAuthenticator(mapperOf(tester(true)), encoder);
        assertThrows(BusinessException.class, () -> auth.authenticate(login("ghost", "Test1234!")));
    }

    @Test
    void 비활성_계정_거부() {
        DbAuthenticator auth = new DbAuthenticator(mapperOf(tester(false)), encoder);
        assertThrows(BusinessException.class, () -> auth.authenticate(login("tester", "Test1234!")));
    }
}
