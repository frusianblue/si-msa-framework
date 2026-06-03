package com.company.framework.filebatch;

/**
 * 일괄처리 작업 SPI. 한 아이템에 작업을 적용해 <b>결과 아이템</b>을 돌려준다(rename/변환/압축 등).
 * 구현은 보통 {@code ops} 패키지의 {@code RenameOperation}/{@code ImageTransformOperation}/{@code CompressOperation}.
 *
 * <p>오케스트레이터({@link FileBatchProcessor})는 아이템마다 {@link #apply(BatchItem)} 를 가상스레드로 호출한다.
 * 작업이 던진 예외는 부분 실패로 수집되거나(continueOnError) fail-fast 로 전체를 멈춘다. {@code BusinessException}
 * 의 {@code ErrorCode} 는 결과에 보존된다. 교차 검증(이름 충돌 등)이 필요한 작업은 {@link BatchPreflight} 도 구현한다.
 */
public interface BatchFileOperation {

    /** 아이템에 작업을 적용하고 결과 아이템을 반환한다(실제 IO 수행). */
    BatchItem apply(BatchItem item) throws Exception;

    /** 드라이런 미리보기용 계획 문자열(실제 IO 금지). 기본은 현재 이름. */
    default String plan(BatchItem item) {
        return item.name();
    }
}
