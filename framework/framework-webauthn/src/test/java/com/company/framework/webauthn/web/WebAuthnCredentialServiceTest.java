package com.company.framework.webauthn.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.framework.core.error.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.CredentialRecordOwnerAuthorizationManager;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/**
 * 패스키 관리 서비스(목록/삭제) 단위 검증. 저장소·사용자엔티티 리포지토리는 mock, {@link Bytes} 는 실제 인스턴스를 써서
 * 웹 컨텍스트/실제 크립토 없이 (1) 목록 필드 매핑, (2) 소유권 게이트 삭제(소유→삭제 호출, 비소유→{@code NOT_FOUND}·삭제 미호출)를
 * 검증한다. 삭제 소유권은 운영 코드와 동일하게 SS7 {@link CredentialRecordOwnerAuthorizationManager} 를 거친다.
 */
class WebAuthnCredentialServiceTest {

    private static final Bytes ALICE_HANDLE = new Bytes("alice-handle".getBytes());
    private static final Bytes OTHER_HANDLE = new Bytes("mallory-handle".getBytes());
    private static final Bytes CRED_ID = new Bytes("credential-id-0001".getBytes());

    private final UserCredentialRepository userCredentials = mock(UserCredentialRepository.class);
    private final PublicKeyCredentialUserEntityRepository userEntities =
            mock(PublicKeyCredentialUserEntityRepository.class);
    private final WebAuthnCredentialService service = new WebAuthnCredentialService(
            userCredentials,
            userEntities,
            new CredentialRecordOwnerAuthorizationManager(userCredentials, userEntities));

    private static Authentication alice() {
        return new UsernamePasswordAuthenticationToken("alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("목록: user handle 의 자격증명을 비민감 요약으로 매핑")
    void listMapsCredentialFields() {
        PublicKeyCredentialUserEntity userEntity = mock(PublicKeyCredentialUserEntity.class);
        when(userEntity.getId()).thenReturn(ALICE_HANDLE);
        when(userEntities.findByUsername("alice")).thenReturn(userEntity);

        Instant created = Instant.parse("2026-06-01T00:00:00Z");
        Instant lastUsed = Instant.parse("2026-06-05T09:30:00Z");
        CredentialRecord credential = mock(CredentialRecord.class);
        when(credential.getCredentialId()).thenReturn(CRED_ID);
        when(credential.getLabel()).thenReturn("iPhone");
        when(credential.getCredentialType()).thenReturn(PublicKeyCredentialType.PUBLIC_KEY);
        when(credential.getTransports()).thenReturn(Set.of());
        when(credential.getSignatureCount()).thenReturn(7L);
        when(credential.isBackupEligible()).thenReturn(true);
        when(credential.isBackupState()).thenReturn(true);
        when(credential.getCreated()).thenReturn(created);
        when(credential.getLastUsed()).thenReturn(lastUsed);
        when(userCredentials.findByUserId(ALICE_HANDLE)).thenReturn(List.of(credential));

        List<WebAuthnCredentialSummary> summaries = service.listForCurrentUser(alice());

        assertThat(summaries).hasSize(1);
        WebAuthnCredentialSummary s = summaries.get(0);
        assertThat(s.credentialId()).isEqualTo(CRED_ID.toBase64UrlString());
        assertThat(s.label()).isEqualTo("iPhone");
        assertThat(s.type()).isEqualTo("public-key");
        assertThat(s.transports()).isEmpty();
        assertThat(s.signatureCount()).isEqualTo(7L);
        assertThat(s.backupEligible()).isTrue();
        assertThat(s.backupState()).isTrue();
        assertThat(s.created()).isEqualTo(created);
        assertThat(s.lastUsed()).isEqualTo(lastUsed);
    }

    @Test
    @DisplayName("목록: user handle 미존재(패스키 0개)면 빈 목록")
    void listReturnsEmptyWhenNoUserEntity() {
        when(userEntities.findByUsername("alice")).thenReturn(null);

        assertThat(service.listForCurrentUser(alice())).isEmpty();
    }

    @Test
    @DisplayName("삭제: 본인 소유면 저장소 delete 호출")
    void deleteRemovesWhenOwned() {
        CredentialRecord credential = mock(CredentialRecord.class);
        when(credential.getUserEntityUserId()).thenReturn(ALICE_HANDLE);
        when(userCredentials.findByCredentialId(any(Bytes.class))).thenReturn(credential);

        PublicKeyCredentialUserEntity userEntity = mock(PublicKeyCredentialUserEntity.class);
        when(userEntity.getId()).thenReturn(ALICE_HANDLE);
        when(userEntities.findByUsername("alice")).thenReturn(userEntity);

        service.deleteForCurrentUser(alice(), CRED_ID.toBase64UrlString());

        verify(userCredentials).delete(any(Bytes.class));
    }

    @Test
    @DisplayName("삭제: 타인 소유면 NOT_FOUND + delete 미호출(존재 비노출)")
    void deleteDeniesWhenNotOwned() {
        CredentialRecord credential = mock(CredentialRecord.class);
        when(credential.getUserEntityUserId()).thenReturn(OTHER_HANDLE);
        when(userCredentials.findByCredentialId(any(Bytes.class))).thenReturn(credential);

        PublicKeyCredentialUserEntity userEntity = mock(PublicKeyCredentialUserEntity.class);
        when(userEntity.getId()).thenReturn(ALICE_HANDLE);
        when(userEntities.findByUsername("alice")).thenReturn(userEntity);

        assertThatThrownBy(() -> service.deleteForCurrentUser(alice(), CRED_ID.toBase64UrlString()))
                .isInstanceOf(BusinessException.class);

        verify(userCredentials, never()).delete(any(Bytes.class));
    }

    @Test
    @DisplayName("삭제: base64url 디코딩 불가 입력도 NOT_FOUND(식별자 형태 비노출)")
    void deleteRejectsMalformedCredentialId() {
        assertThatThrownBy(() -> service.deleteForCurrentUser(alice(), "not valid base64!!"))
                .isInstanceOf(BusinessException.class);

        verify(userCredentials, never()).delete(any(Bytes.class));
    }
}
