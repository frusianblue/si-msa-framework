package com.company.framework.batch.launch;

/**
 * 배치 Job 기동 실패를 감싸는 런타임 예외. Spring Batch 의 체크예외({@code JobExecutionException} 계열)를
 * 호출부(컨트롤러/스케줄러)가 try/catch 강제 없이 다루도록 한다.
 */
public class BatchLaunchException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BatchLaunchException(String message, Throwable cause) {
        super(message, cause);
    }
}
