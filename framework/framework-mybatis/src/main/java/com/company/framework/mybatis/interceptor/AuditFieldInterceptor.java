package com.company.framework.mybatis.interceptor;

import com.company.framework.mybatis.handler.BaseEntity;
import com.company.framework.mybatis.support.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.Properties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;

/**
 * INSERT/UPDATE 시 BaseEntity 의 감사필드를 자동 주입한다.
 * 서비스 코드에서 createdBy/updatedAt 등을 매번 set 할 필요가 없어진다.
 */
@Intercepts({
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class})
})
public class AuditFieldInterceptor implements Interceptor {

    private final CurrentUserProvider currentUserProvider;

    public AuditFieldInterceptor(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object param = invocation.getArgs()[1];

        if (param instanceof BaseEntity entity) {
            String user = currentUserProvider.getCurrentUser().orElse("system");
            LocalDateTime now = LocalDateTime.now();
            SqlCommandType type = ms.getSqlCommandType();
            if (type == SqlCommandType.INSERT) {
                if (entity.getCreatedAt() == null) entity.setCreatedAt(now);
                if (entity.getCreatedBy() == null) entity.setCreatedBy(user);
                entity.setUpdatedAt(now);
                entity.setUpdatedBy(user);
            } else if (type == SqlCommandType.UPDATE) {
                entity.setUpdatedAt(now);
                entity.setUpdatedBy(user);
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        /* no-op */
    }
}
