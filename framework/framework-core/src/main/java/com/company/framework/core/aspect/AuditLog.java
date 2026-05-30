package com.company.framework.core.aspect;

import java.lang.annotation.*;

/**
 * 감사 로그 대상 메서드 표시. (누가/언제/무엇을 했는지 기록 - SI 감사추적 요건)
 * 예: @AuditLog(action = "USER_CREATE", target = "USER")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    String action();

    String target() default "";
}
