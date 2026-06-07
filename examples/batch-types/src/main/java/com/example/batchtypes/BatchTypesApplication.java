package com.example.batchtypes;

import com.company.framework.task.EnableFrameworkTask;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 배치 "처리 방식(유형)" + DB 리더/라이터 종류 카탈로그 앱. {@code --spring.batch.job.name=<jobName>} 으로
 * 하나만 골라 1회 실행한다. 실행 이력은 {@link EnableFrameworkTask} 가 TASK_EXECUTION 에 남긴다.
 */
@SpringBootApplication
@EnableFrameworkTask
public class BatchTypesApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(BatchTypesApplication.class, args)));
    }
}
