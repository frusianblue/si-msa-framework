package com.example.batchcookbook;

import com.company.framework.task.EnableFrameworkTask;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 배치 요리책(cookbook) 부트 앱 — 한 프로세스에 여러 패턴의 Job 을 담아 두고,
 * {@code --spring.batch.job.name=<jobName>} 으로 하나만 골라 1회 실행한다(run-once 태스크).
 *
 * <p>{@link EnableFrameworkTask} 가 Spring Cloud Task 실행 이력(TASK_EXECUTION)을 켠다.
 * 배치 결합 시 SCT 가 JobExecution↔TaskExecution 을 자동으로 잇는다(TASK_TASK_BATCH).
 *
 * <p><b>종료코드 전파</b>: {@code System.exit(SpringApplication.exit(...))} 로 스프링 종료코드를
 * 프로세스 종료코드로 넘긴다. 배치 실패 시(application.yml 의 {@code fail-on-job-failure=true})
 * 종료코드가 0이 아니게 되어 k8s Job/CronJob 이 실패를 감지한다. ENTRYPOINT 는 exec 형태여야 한다.
 */
@SpringBootApplication
@EnableFrameworkTask
public class BatchCookbookApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(BatchCookbookApplication.class, args)));
    }
}
