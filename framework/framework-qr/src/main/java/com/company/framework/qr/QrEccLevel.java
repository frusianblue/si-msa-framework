package com.company.framework.qr;

/**
 * QR 오류정정(ECC) 레벨. 값이 높을수록 손상/오염에 강하지만 같은 용량에서 담을 수 있는 데이터가 줄고 모듈 수가 늘어
 * 더 조밀해진다. (실제 ZXing {@code ErrorCorrectionLevel} 매핑은 구현 내부에서 수행 — API 는 ZXing 무의존.)
 *
 * <ul>
 *   <li>{@link #L} — 약 7% 복원. 깨끗한 화면 표시용.
 *   <li>{@link #M} — 약 15% 복원(기본). 일반 인쇄/화면 범용.
 *   <li>{@link #Q} — 약 25% 복원. 로고 오버레이·다소 오염 환경.
 *   <li>{@link #H} — 약 30% 복원. 인쇄물 훼손 가능 환경.
 * </ul>
 */
public enum QrEccLevel {
    /** 약 7% 복원. */
    L,
    /** 약 15% 복원(기본). */
    M,
    /** 약 25% 복원. */
    Q,
    /** 약 30% 복원. */
    H
}
