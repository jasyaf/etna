package org.etnaframework.core.util;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事务方法调用区
 *
 * @author BlackCat
 * @since 2017-07-17
 */
@Service
public class SyncTransactionalHelper {

    /**
     * 执行事务块，无返回值
     */
    @Transactional(readOnly = false, propagation = Propagation.NESTED, rollbackFor = Throwable.class)
    public void run(SyncTransactionRunnable st) throws Throwable {
        st.run();
    }

    /**
     * 执行事务块，可返回返回值
     */
    @Transactional(readOnly = false, propagation = Propagation.NESTED, rollbackFor = Throwable.class)
    public <T> T call(SyncTransactionCallalable<T> st) throws Throwable {
        return st.call();
    }

    /**
     * 需要事务处理的模块统一处理类，支持返回值
     */
    @FunctionalInterface
    public interface SyncTransactionCallalable<T> {

        T call() throws Throwable;
    }

    /**
     * 需要事务处理的模块统一处理类
     */
    @FunctionalInterface
    public interface SyncTransactionRunnable {

        void run() throws Throwable;
    }
}
