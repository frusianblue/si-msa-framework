package com.company.framework.filebatch;

/**
 * 아이템 1건의 처리 결과(불변).
 *
 * @param item      입력 아이템(인덱스 포함).
 * @param status    OK/FAILED/SKIPPED.
 * @param result    성공 시 결과 아이템(드라이런/실패/스킵이면 null).
 * @param errorCode 실패 시 보존된 에러 코드(없으면 null).
 * @param message   설명(드라이런 계획·실패 사유·스킵 사유 등; 없으면 null).
 */
public record ItemOutcome(BatchItem item, Status status, BatchItem result, String errorCode, String message) {

    public enum Status {
        OK,
        FAILED,
        SKIPPED
    }

    public static ItemOutcome ok(BatchItem item, BatchItem result) {
        return new ItemOutcome(item, Status.OK, result, null, null);
    }

    /** 드라이런: IO 없이 계획만 담아 OK 로 표시. */
    public static ItemOutcome planned(BatchItem item, String plan) {
        return new ItemOutcome(item, Status.OK, null, null, plan);
    }

    public static ItemOutcome failed(BatchItem item, String errorCode, String message) {
        return new ItemOutcome(item, Status.FAILED, null, errorCode, message);
    }

    public static ItemOutcome skipped(BatchItem item, String message) {
        return new ItemOutcome(item, Status.SKIPPED, null, null, message);
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }
}
