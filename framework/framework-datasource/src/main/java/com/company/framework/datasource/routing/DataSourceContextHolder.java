package com.company.framework.datasource.routing;

/**
 * (선택적·고급) 라우팅 키를 호출 스레드 단위로 강제 지정하는 ThreadLocal 홀더.
 *
 * <p>기본 라우팅은 {@code @Transactional(readOnly = true)} 의 트랜잭션 readOnly 플래그로 충분하다.
 * 이 홀더는 <b>트랜잭션 밖에서 복제 노드로 강제 조회</b>하고 싶은 예외적 상황에만 쓴다.
 * 설정 시 트랜잭션 readOnly 플래그보다 우선한다({@link RoutingDataSource#determineCurrentLookupKey()}).
 *
 * <p><b>반드시 try/finally 로 정리</b>할 것. 가상 스레드/풀 스레드 재사용 시 누수되면 다른 요청이 잘못된 노드로 라우팅된다.
 *
 * <pre>{@code
 * DataSourceContextHolder.set(DataSourceType.READ);
 * try {
 *     return reportMapper.heavyAggregate(...); // 트랜잭션 없이도 READ 노드 사용
 * } finally {
 *     DataSourceContextHolder.clear();
 * }
 * }</pre>
 */
public final class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();

    private DataSourceContextHolder() {}

    /** 현재 스레드의 라우팅 키를 강제 지정한다. */
    public static void set(DataSourceType type) {
        CONTEXT.set(type);
    }

    /** 현재 스레드에 강제 지정된 라우팅 키(없으면 {@code null}). */
    public static DataSourceType peek() {
        return CONTEXT.get();
    }

    /** 강제 지정을 해제한다. 사용 후 반드시 호출(누수 방지). */
    public static void clear() {
        CONTEXT.remove();
    }
}
