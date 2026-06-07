package com.example.settlement;

import com.company.framework.task.EnableFrameworkTask;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 정산(settlement) 단발 태스크 앱.
 *
 * <p>컨테이너가 뜨면 {@code spring.batch.job.name=settlementJob} 에 따라 배치 Job 을 <b>1회</b> 실행하고 종료한다.
 * {@link EnableFrameworkTask}(= Spring Cloud Task {@code @EnableTask})가 부착되어, 이 프로세스 한 번의 실행이
 * {@code TASK_EXECUTION}(시작/종료/종료코드/오류)으로 기록된다. 배치의 {@code JobExecution} 은
 * {@code TASK_TASK_BATCH} 로 태스크 실행에 연결된다(Spring Cloud Task 의 {@code TaskBatchExecutionListener}).
 *
 * <p>스케줄/오케스트레이션은 <b>앱 바깥</b>이 담당한다 — k8s {@code CronJob}(권장, {@code k8s/cronjob.yaml}) 또는
 * SCDF. 같은 jar 를 framework-batch 의 Quartz cron 으로 상주 실행할 수도 있으나(README "두 실행 모델" 참고),
 * 이 예제는 run-once 태스크 모델을 보여 준다.
 */
@SpringBootApplication
@EnableFrameworkTask
public class SettlementTaskApplication {

    public static void main(String[] args) {
        // 종료코드를 컨테이너로 전파(배치 실패 시 ≠0). SpringApplication.exit + System.exit 로 명확히.
        System.exit(SpringApplication.exit(SpringApplication.run(SettlementTaskApplication.class, args)));
    }
}
