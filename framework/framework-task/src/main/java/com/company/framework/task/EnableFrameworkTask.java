package com.company.framework.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.cloud.task.configuration.EnableTask;

/**
 * 이 애플리케이션을 "프레임워크 태스크"로 활성화한다 — Spring Cloud Task 의
 * {@link EnableTask @EnableTask} 를 메타-합성한 프레임워크 별칭.
 *
 * <p>부착하면 Spring Cloud Task 의 {@code TaskLifecycleListener} 가 활성화되어 애플리케이션 한 번 실행의
 * <b>태스크 실행 이력</b>(시작 시각·종료 시각·종료코드·오류)을 태스크 저장소에 기록한다. 저장소는 {@code DataSource}
 * 가 있으면 JDBC(PostgreSQL 등), 없으면 인메모리({@code MapTaskRepository})로 자동 선택된다(Spring Cloud Task 기본).
 *
 * <p>표준 {@code @EnableTask} 대신 이 별칭을 쓰는 이유는 "프레임워크 태스크"라는 의도를 코드에 드러내고,
 * 추후 프레임워크 차원의 합성(예: 표준 인자/메타 부착)을 한 지점에서 확장하기 위함이다. 기능은 {@code @EnableTask}
 * 와 동일하므로, 이미 {@code @EnableTask} 를 쓰는 앱은 그대로 두어도 된다(중복 부착 시 동일 구성 클래스로 디듀프).
 *
 * <pre>{@code
 * @SpringBootApplication
 * @EnableFrameworkTask
 * public class SettlementTaskApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(SettlementTaskApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * <p>표준 감사 로깅 리스너({@code FrameworkTaskExecutionListener})는 별개로 {@code framework.task.enabled=true}
 * 로 켠다(아래 README 참조).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@EnableTask
public @interface EnableFrameworkTask {}
