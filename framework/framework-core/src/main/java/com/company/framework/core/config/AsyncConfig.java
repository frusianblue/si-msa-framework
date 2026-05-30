package com.company.framework.core.config;

import java.util.concurrent.Executors;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Java 21 가상 스레드 기반 @Async 실행기.
 * 작업마다 가상 스레드를 생성하므로 I/O 바운드 비동기 작업에 적합하다.
 * (HTTP 요청 처리 스레드는 application.yml 의 spring.threads.virtual.enabled=true 로 가상화)
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncTaskExecutor getAsyncExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
