package com.company.framework.mfa.core;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.RelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * WebAuthn 2차 인증 ceremony 의 <b>상태 없는</b> 협력자: SS7 {@link WebAuthnRelyingPartyOperations} 호출과 옵션/자격증명
 * JSON 직렬화만 담당한다. challenge(옵션) 보관과 티켓 라이프사이클은 {@link MfaService} 가 오케스트레이션한다.
 *
 * <p>직렬화는 SS7 이 공식 제공하는 <b>Jackson 3</b> 모듈({@link WebauthnJacksonModule}, {@code tools.jackson.*})을
 * 등록한 전용 {@link JsonMapper} 로 한다(프레임워크 Jackson 3 규약 준수, 글로벌 ObjectMapper 무영향). 옵션 직렬화·attestation/
 * assertion 역직렬화는 모두 이 모듈이 커버하므로 수기 코덱이 필요 없다. 제네릭 자격증명
 * ({@code PublicKeyCredential<AuthenticatorAttestationResponse|AssertionResponse>})은 {@link TypeReference} 로 구체화한다
 * (SS 필터가 쓰는 {@code ResolvableType.forClassWithGenerics} 와 동등).
 *
 * <p>사용자 식별은 1차 인증을 통과한 userId 를 principal 로 하는 {@link Authentication} 으로 RP 연산에 전달한다 — RP 가
 * {@code auth.getName()} 으로 user handle·allowCredentials 를 해석하므로, ceremony·소유 검증이 framework-webauthn 의
 * 등록/인증과 동일 규약으로 정합한다.
 */
public class MfaWebAuthnSupport {

    private final WebAuthnRelyingPartyOperations rpOperations;
    private final JsonMapper mapper;

    public MfaWebAuthnSupport(WebAuthnRelyingPartyOperations rpOperations) {
        this.rpOperations = rpOperations;
        this.mapper =
                JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();
    }

    // ===================== 등록(registration) ceremony =====================

    /** 등록 ceremony 옵션(creation options, challenge 포함) 발급 → JSON. 호출자가 티켓에 바인딩 보관한다. */
    public String createRegistrationOptionsJson(String userId) {
        PublicKeyCredentialCreationOptions options =
                rpOperations.createPublicKeyCredentialCreationOptions(() -> authenticationFor(userId));
        return write(options);
    }

    /**
     * 클라이언트 attestation 으로 자격증명을 등록한다. 보관해 둔 creation 옵션(challenge)과 함께 RP 에 제출하면 RP 가
     * 검증 후 {@code UserCredentialRepository} 에 저장한다(저장은 RP 내부 책임). 등록 메타({@code MfaEnrollment})
     * 기록은 호출자({@link MfaService})가 한다.
     *
     * @param creationOptionsJson 발급 시 보관한 creation 옵션 JSON
     * @param credentialJson 브라우저 {@code navigator.credentials.create} 결과(attestation) JSON
     * @param label 자격증명 표시명(예: "YubiKey", "내 노트북")
     */
    public CredentialRecord registerCredential(String creationOptionsJson, String credentialJson, String label) {
        PublicKeyCredentialCreationOptions options =
                read(creationOptionsJson, PublicKeyCredentialCreationOptions.class);
        PublicKeyCredential<AuthenticatorAttestationResponse> credential =
                read(credentialJson, new TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse>>() {});
        RelyingPartyPublicKey publicKey =
                new RelyingPartyPublicKey(credential, (label == null || label.isBlank()) ? "passkey" : label);
        RelyingPartyRegistrationRequest request = new RelyingPartyRegistrationRequest() {
            @Override
            public PublicKeyCredentialCreationOptions getCreationOptions() {
                return options;
            }

            @Override
            public RelyingPartyPublicKey getPublicKey() {
                return publicKey;
            }
        };
        return rpOperations.registerCredential(request);
    }

    // ===================== 검증(assertion) ceremony =====================

    /** 검증 ceremony 옵션(request options, challenge 포함) 발급 → JSON. 호출자가 티켓에 바인딩 보관한다. */
    public String createAssertionOptionsJson(String userId) {
        PublicKeyCredentialRequestOptions options =
                rpOperations.createCredentialRequestOptions(() -> authenticationFor(userId));
        return write(options);
    }

    /**
     * 클라이언트 assertion 을 검증하고, 서명한 자격증명 소유자의 username 을 반환한다. 호출자는 이 username 이 티켓의
     * userId 와 일치하는지 확인해 factor 충족을 판정한다.
     *
     * @param requestOptionsJson 발급 시 보관한 request 옵션 JSON
     * @param credentialJson 브라우저 {@code navigator.credentials.get} 결과(assertion) JSON
     * @return 검증된 자격증명 소유자의 username({@link PublicKeyCredentialUserEntity#getName()})
     */
    public String authenticate(String requestOptionsJson, String credentialJson) {
        PublicKeyCredentialRequestOptions options = read(requestOptionsJson, PublicKeyCredentialRequestOptions.class);
        PublicKeyCredential<AuthenticatorAssertionResponse> credential =
                read(credentialJson, new TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>() {});
        PublicKeyCredentialUserEntity userEntity =
                rpOperations.authenticate(new RelyingPartyAuthenticationRequest(options, credential));
        return userEntity.getName();
    }

    // ===================== 내부 =====================

    private static Authentication authenticationFor(String userId) {
        // RP 는 principal 이름(userId)만 사용한다(권한 불요). authorities 를 주면 authenticated=true 로 생성된다.
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "WebAuthn 옵션 직렬화에 실패했습니다.");
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "WebAuthn 입력이 올바르지 않습니다.");
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "WebAuthn 입력이 올바르지 않습니다.");
        }
    }
}
