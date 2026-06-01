package com.company.framework.batch.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quartz cron 스케줄 토글/목록.
 *
 * <pre>{@code
 * framework:
 *   scheduler:
 *     enabled: false                 # 선택형 → 명시적 on. (framework.batch.enabled 도 켜져 있어야 동작 — Job 기동에 JobLaunchSupport 필요)
 *     jobs:
 *       - name: settlementJob        # Spring Batch Job '빈 이름'
 *         cron: "0 0 2 * * ?"        # 매일 02:00 (Quartz cron: 초 분 시 일 월 요일)
 *         enabled: true              # 개별 토글(기본 true)
 *         group: framework-batch     # Quartz 그룹(기본)
 * }</pre>
 *
 * <p>기본 Quartz JobStore 는 RAM(메모리)이라 별도 DB 테이블이 필요 없다. 다중 인스턴스에서 1회만 실행하려면
 * {@code spring.quartz.job-store-type=jdbc} + 클러스터 설정을 서비스에서 켠다.
 */
@ConfigurationProperties(prefix = "framework.scheduler")
public class SchedulerProperties {

    /** 선택형 → 기본 off. */
    private boolean enabled = false;

    private List<JobSchedule> jobs = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<JobSchedule> getJobs() {
        return jobs;
    }

    public void setJobs(List<JobSchedule> jobs) {
        this.jobs = jobs;
    }

    public static class JobSchedule {
        /** 실행할 Spring Batch Job 의 스프링 빈 이름. */
        private String name;
        /** Quartz cron 식(초 분 시 일 월 요일). */
        private String cron;
        /** 개별 on/off. */
        private boolean enabled = true;
        /** Quartz 잡/트리거 그룹. */
        private String group = "framework-batch";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }
    }
}
