package com.company.framework.samlsp.slo;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.samlsp.core.SamlLogoutInfo;
import com.company.framework.samlsp.core.SamlLogoutUserResolver;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * SLO 오케스트레이션 단위검증(OpenSAML/SS 무의존 — fake resolver/terminator). 검증된 신원이 들어왔을 때의 분기:
 * 매핑 성공 → 무효화 호출, 매핑 실패/blank nameId → no-op.
 */
class SamlSloServiceTest {

    /** 호출 기록용 fake 종료기. */
    private static final class RecordingTerminator implements SamlSessionTerminator {
        String lastUserId;
        String lastReason;
        int returnValue;
        int calls;

        RecordingTerminator(int returnValue) {
            this.returnValue = returnValue;
        }

        @Override
        public int terminateAll(String userId, String reason) {
            this.lastUserId = userId;
            this.lastReason = reason;
            this.calls++;
            return returnValue;
        }
    }

    @Test
    void mapsNameIdToUserAndTerminates() {
        RecordingTerminator terminator = new RecordingTerminator(3);
        SamlLogoutUserResolver resolver = info -> "user-42"; // nameId → 우리 사용자
        SamlSloService service = new SamlSloService(resolver, terminator);

        int terminated = service.onLogoutRequest(new SamlLogoutInfo("corp", "nameid-abc", List.of("idx1")));

        assertThat(terminated).isEqualTo(3);
        assertThat(terminator.calls).isEqualTo(1);
        assertThat(terminator.lastUserId).isEqualTo("user-42");
        assertThat(terminator.lastReason).isEqualTo("saml-slo");
    }

    @Test
    void unmappedNameIdIsGracefulNoOp() {
        RecordingTerminator terminator = new RecordingTerminator(99);
        SamlLogoutUserResolver resolver = info -> null; // 매칭되는 우리 사용자 없음
        SamlSloService service = new SamlSloService(resolver, terminator);

        int terminated = service.onLogoutRequest(new SamlLogoutInfo("corp", "unknown-nameid"));

        assertThat(terminated).isZero();
        assertThat(terminator.calls).isZero(); // 무효화 자체를 호출하지 않음
    }

    @Test
    void blankNameIdIsIgnoredWithoutResolving() {
        RecordingTerminator terminator = new RecordingTerminator(1);
        AtomicReference<Boolean> resolverCalled = new AtomicReference<>(false);
        SamlLogoutUserResolver resolver = info -> {
            resolverCalled.set(true);
            return "x";
        };
        SamlSloService service = new SamlSloService(resolver, terminator);

        assertThat(service.onLogoutRequest(new SamlLogoutInfo("corp", "   "))).isZero();
        assertThat(service.onLogoutRequest(new SamlLogoutInfo("corp", null))).isZero();
        assertThat(service.onLogoutRequest(null)).isZero();
        assertThat(resolverCalled.get()).isFalse();
        assertThat(terminator.calls).isZero();
    }

    @Test
    void sessionIndexesArePreservedAndDefensivelyCopied() {
        SamlLogoutInfo info = new SamlLogoutInfo("corp", "n", List.of("a", "b"));
        assertThat(info.sessionIndexes()).containsExactly("a", "b");
        // null SessionIndex → 빈 리스트
        assertThat(new SamlLogoutInfo("corp", "n", null).sessionIndexes()).isEmpty();
        assertThat(new SamlLogoutInfo("corp", "n").sessionIndexes()).isEmpty();
    }
}
