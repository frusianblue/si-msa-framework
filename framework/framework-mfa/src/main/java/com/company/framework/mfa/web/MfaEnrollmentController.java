package com.company.framework.mfa.web;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.response.ApiResponse;
import com.company.framework.mfa.core.MfaMethod;
import com.company.framework.mfa.core.MfaService;
import com.company.framework.mybatis.support.CurrentUserProvider;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2단계 인증 등록/관리 엔드포인트(<b>인증 필요</b>). JWT 로 식별된 본인이 자신의 MFA 수단을 등록/확정/해제한다.
 *
 * <p>경로 {@code /api/v1/mfa/**} 는 SecurityAutoConfiguration 의 permitAll 매처에 포함되지 않으므로 기본적으로
 * 인증을 요구한다(별도 설정 불필요). 현재 사용자 = JWT subject = {@link CurrentUserProvider#getCurrentUser()}.
 *
 * <ul>
 *   <li>{@code POST /enroll/totp} — TOTP 등록 시작. 미확정 시크릿과 otpauth:// URI 반환(QR 은 클라이언트가 생성).
 *   <li>{@code POST /enroll/totp/confirm} {@code {code}} — 입력 코드 검증 후 확정. 일회용 복구코드 목록 반환(이 응답에서만 노출).
 *   <li>{@code POST /enroll/otp} — OTP(SMS/이메일/알림톡) 수단 등록(OtpSender 빈과 otp.enabled 필요).
 *   <li>{@code GET /status} — 확정/대기 수단 조회.
 *   <li>{@code DELETE /enroll/{method}} — 수단 해제.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/mfa")
public class MfaEnrollmentController {

    private final MfaService mfaService;
    private final CurrentUserProvider currentUserProvider;

    public MfaEnrollmentController(MfaService mfaService, CurrentUserProvider currentUserProvider) {
        this.mfaService = mfaService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/enroll/totp")
    public ApiResponse<MfaService.EnrollmentStart> enrollTotp() {
        String userId = currentUserId();
        return ApiResponse.ok(mfaService.beginTotpEnrollment(userId, userId), "TOTP 등록을 시작했습니다.");
    }

    @PostMapping("/enroll/totp/confirm")
    public ApiResponse<List<String>> confirmTotp(@RequestBody Map<String, String> body) {
        String userId = currentUserId();
        String code = body == null ? null : body.get("code");
        List<String> recoveryCodes = mfaService.confirmTotp(userId, code);
        return ApiResponse.ok(recoveryCodes, "TOTP 등록이 완료되었습니다. 복구코드를 안전한 곳에 보관하세요.");
    }

    @PostMapping("/enroll/otp")
    public ApiResponse<Void> enrollOtp() {
        mfaService.enrollOtp(currentUserId());
        return ApiResponse.ok(null, "OTP 인증이 등록되었습니다.");
    }

    @GetMapping("/status")
    public ApiResponse<MfaService.MfaStatus> status() {
        return ApiResponse.ok(mfaService.status(currentUserId()));
    }

    @DeleteMapping("/enroll/{method}")
    public ApiResponse<Void> disable(@PathVariable("method") String method) {
        MfaMethod m = MfaMethod.from(method);
        if (m == null) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "알 수 없는 인증 방식입니다: " + method);
        }
        mfaService.disable(currentUserId(), m);
        return ApiResponse.ok(null, "인증 수단이 해제되었습니다.");
    }

    private String currentUserId() {
        return currentUserProvider
                .getCurrentUser()
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED));
    }
}
