package com.company.framework.datasource.routing;

/**
 * 라우팅 대상 노드 종류.
 *
 * <ul>
 *   <li>{@link #WRITE} — 주(쓰기) 노드. 비안전/쓰기 트랜잭션, 트랜잭션 밖 호출의 기본값.
 *   <li>{@link #READ} — 복제(읽기) 노드. {@code @Transactional(readOnly = true)} 트랜잭션이 매핑.
 * </ul>
 *
 * <p>복제 노드(read.url)가 설정되지 않으면 READ 키도 WRITE 데이터소스로 매핑되어 단일 DB 환경에서도 무해하게 동작한다.
 */
public enum DataSourceType {
    WRITE,
    READ
}
