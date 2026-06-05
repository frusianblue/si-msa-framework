package com.company.framework.webauthn.web;

import java.time.Instant;
import java.util.List;

/**
 * 패스키 자격증명 1건의 관리용 요약(목록 응답 항목). SS7 {@code CredentialRecord} 에서 <b>관리 UX 에 필요한 비민감</b>
 * 필드만 추린다 — 공개키/attestation/user handle 등 민감/내부 식별자는 노출하지 않는다.
 *
 * <p>{@link #credentialId} 는 base64url 문자열로, 삭제({@code DELETE .../{credentialId}}) 시 그대로 경로 변수로 쓴다.
 * (credential id 는 본인 인증 채널로만 노출 — WebAuthn 명세 §14.6.3 프라이버시 권고.)
 *
 * @param credentialId base64url 인코딩된 자격증명 ID(삭제 시 경로 변수)
 * @param label 사용자/인증기가 부여한 표시명(예: "iPhone", "YubiKey 5")
 * @param type 자격증명 타입(보통 {@code public-key}, 미상이면 null)
 * @param transports 인증기 전송 방식(예: {@code internal}, {@code usb}, {@code hybrid})
 * @param signatureCount 마지막으로 관측된 서명 카운터(클론 탐지용 — UX 표시는 선택)
 * @param backupEligible 백업(동기) 자격 여부 — 멀티디바이스 패스키 식별 힌트
 * @param backupState 현재 백업(동기)된 상태 여부
 * @param created 등록 시각
 * @param lastUsed 마지막 사용 시각
 */
public record WebAuthnCredentialSummary(
        String credentialId,
        String label,
        String type,
        List<String> transports,
        long signatureCount,
        boolean backupEligible,
        boolean backupState,
        Instant created,
        Instant lastUsed) {}
