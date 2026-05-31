package com.company.framework.datasource.routing;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 현재 트랜잭션의 readOnly 여부(또는 {@link DataSourceContextHolder} 강제값)에 따라 WRITE/READ 노드를 선택하는 라우팅 데이터소스.
 *
 * <p>결정 우선순위:
 *
 * <ol>
 *   <li>{@link DataSourceContextHolder#peek()} 가 지정되어 있으면 그 값(고급 사용).
 *   <li>{@code TransactionSynchronizationManager.isCurrentTransactionReadOnly()} 가 {@code true} → {@link DataSourceType#READ}.
 *   <li>그 외(쓰기 트랜잭션·트랜잭션 밖) → {@link DataSourceType#WRITE}.
 * </ol>
 *
 * <p>이 클래스 단독으로는 라우팅이 정확하지 않다. 트랜잭션 시작 시점에 connection 을 먼저 잡으면 readOnly 플래그가 아직
 * 바인딩되기 전이라 WRITE 로 고정될 수 있기 때문이다. 따라서 오토컨피그에서 {@code LazyConnectionDataSourceProxy} 로 감싸
 * <b>최초 실제 쿼리 시점</b>(= readOnly 플래그 확정 이후)에 물리 connection 을 잡도록 한다.
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType forced = DataSourceContextHolder.peek();
        if (forced != null) {
            return forced;
        }
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? DataSourceType.READ
                : DataSourceType.WRITE;
    }
}
