package com.company.framework.idempotency.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 멱등 처리 대상 표시. 컨트롤러 메서드에 붙이면 인터셉터가 Idempotency-Key 헤더를 검사한다.
 * 모듈/기능이 꺼져 있으면 인터셉터 자체가 등록되지 않아 무시된다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {}
