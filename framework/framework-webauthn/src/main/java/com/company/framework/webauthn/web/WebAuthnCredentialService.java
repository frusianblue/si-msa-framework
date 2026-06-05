package com.company.framework.webauthn.web;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.CredentialRecordOwnerAuthorizationManager;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/**
 * 패스키 자격증명 관리(현재 사용자 기준 목록 조회/삭제) 도메인 서비스.
 *
 * <p>등록/인증 ceremony 는 SS7 네이티브 {@code http.webAuthn()} 가 처리하므로, 본 서비스는 ceremony 가 아닌 <b>저장소
 * CRUD + 소유권 검증</b>만 담당한다. 사용자 식별은 ceremony 와 동일하게 인증 principal 의 username
 * ({@link Authentication#getName()})을 키로 {@link PublicKeyCredentialUserEntityRepository#findByUsername(String)}
 * 으로 user handle 을 얻어 일관성을 맞춘다(SS authz 매니저와 동일 규약).
 *
 * <h3>삭제 소유권 검증</h3>
 * 자체 비교를 새로 구현하지 않고 SS7 네이티브 {@link CredentialRecordOwnerAuthorizationManager}(since 6.5.10)를 재사용한다.
 * 이 매니저는 (1) 인증 여부, (2) credential 존재, (3) {@code credential.getUserEntityUserId()} 가 현재 사용자 handle 과
 * 일치하는지를 한 번에 판정한다. 소유 아님·미존재는 <b>모두 deny</b> 로 동일 처리되므로, 본 서비스는 deny 를
 * {@code NOT_FOUND} 로 변환해 <b>자격증명 존재 여부를 노출하지 않는다</b>(WebAuthn 명세 §14.6.3 프라이버시 권고).
 */
public class WebAuthnCredentialService {

    private final UserCredentialRepository userCredentials;
    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final CredentialRecordOwnerAuthorizationManager ownerAuthorization;

    public WebAuthnCredentialService(
            UserCredentialRepository userCredentials,
            PublicKeyCredentialUserEntityRepository userEntities,
            CredentialRecordOwnerAuthorizationManager ownerAuthorization) {
        this.userCredentials = userCredentials;
        this.userEntities = userEntities;
        this.ownerAuthorization = ownerAuthorization;
    }

    /**
     * 현재 인증 사용자가 등록한 패스키 목록. user handle 이 없으면(= 등록한 패스키가 하나도 없음) 빈 목록을 돌려준다.
     */
    public List<WebAuthnCredentialSummary> listForCurrentUser(Authentication authentication) {
        PublicKeyCredentialUserEntity userEntity = userEntities.findByUsername(authentication.getName());
        if (userEntity == null) {
            return List.of();
        }
        List<CredentialRecord> records = userCredentials.findByUserId(userEntity.getId());
        List<WebAuthnCredentialSummary> summaries = new ArrayList<>(records.size());
        for (CredentialRecord credential : records) {
            summaries.add(toSummary(credential));
        }
        return summaries;
    }

    /**
     * 현재 사용자가 소유한 패스키 1건을 삭제한다. 소유가 아니거나 존재하지 않으면(둘 다 deny) {@code NOT_FOUND} 로
     * 응답한다(존재 여부 비노출). credentialId 가 base64url 로 디코딩 불가해도 동일하게 {@code NOT_FOUND}.
     *
     * @param credentialIdBase64Url 삭제할 자격증명 ID(base64url) — 목록 응답의 {@code credentialId}
     */
    public void deleteForCurrentUser(Authentication authentication, String credentialIdBase64Url) {
        Bytes credentialId = decode(credentialIdBase64Url);
        AuthorizationResult decision = ownerAuthorization.authorize(() -> authentication, credentialId);
        if (decision == null || !decision.isGranted()) {
            throw new BusinessException(ErrorCode.Common.NOT_FOUND, "해당 패스키를 찾을 수 없습니다.");
        }
        userCredentials.delete(credentialId);
    }

    private static Bytes decode(String base64Url) {
        try {
            return Bytes.fromBase64(base64Url);
        } catch (RuntimeException ex) {
            // 잘못된 base64url 입력은 "존재하지 않는 자격증명" 과 동일하게 다뤄 식별자 형태를 추측하지 못하게 한다.
            throw new BusinessException(ErrorCode.Common.NOT_FOUND, "해당 패스키를 찾을 수 없습니다.");
        }
    }

    private static WebAuthnCredentialSummary toSummary(CredentialRecord credential) {
        List<String> transports = (credential.getTransports() == null)
                ? List.of()
                : credential.getTransports().stream()
                        .map(AuthenticatorTransport::getValue)
                        .toList();
        String type = (credential.getCredentialType() == null)
                ? null
                : credential.getCredentialType().getValue();
        return new WebAuthnCredentialSummary(
                credential.getCredentialId().toBase64UrlString(),
                credential.getLabel(),
                type,
                transports,
                credential.getSignatureCount(),
                credential.isBackupEligible(),
                credential.isBackupState(),
                credential.getCreated(),
                credential.getLastUsed());
    }
}
