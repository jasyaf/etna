package org.etnaframework.jdbc;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;

/**
 * 带重试机制的事务管理器
 *
 * 由于云环境内网质量并不总是很稳定，开启事务时获取连接容易失败，为了增加成功率，加入重试机制
 * 配置事务时，需要将transaction-manager指向本类
 * <tx:annotation-driven transaction-manager="XXX"/>
 *
 * 非事务环境下，{@link JdbcTemplate}已内置重试机制，直接使用即可
 *
 * @author BlackCat
 * @since 2016-12-15
 */
public class RetriedDataSourceTransactionManager extends DataSourceTransactionManager {

    /** 当网络不稳定时，执行重试的次数 */
    private int retryTimes = 1;

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        for (int i = 0; i <= retryTimes; i++) {
            try {
                super.doBegin(transaction, definition);
                break;
            } catch (CannotCreateTransactionException ext) {
                if (i == retryTimes) {
                    throw ext;
                }
            }
        }
    }
}
