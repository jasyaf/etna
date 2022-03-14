package org.etnaframework.jedis;

import org.etnaframework.core.util.SyncTransactionalHelper;
import org.etnaframework.core.util.SyncTransactionalHelper.SyncTransactionCallalable;
import org.etnaframework.core.util.SyncTransactionalHelper.SyncTransactionRunnable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * jedis增强组件，分布式锁
 *
 * @author BlackCat
 * @since 2018-01-11
 */
public abstract class BaseJedisLock {

    @Autowired
    protected SyncTransactionalHelper syncTransactionalHelper;

    /**
     * redis连接配置
     */
    public abstract JedisConfig jedisConfig();

    /**
     * 分布式同步锁，使用相同的key标注的代码，即便部署在不同的服务器实例上也能【防止同时执行】，避免出现并发问题
     *
     * 锁定时间默认为{@link JedisLock#DEFAULT_LOCK_LEASE_MS}，如果没有在规定时间内执行完，只要线程存活就会自动续期，如果碰到服务器重启就会在锁定期后自动释放
     */
    public <V> V syncCall(String key, SyncTransactionCallalable<V> call) throws Throwable {
        try (JedisLock lock = jedisConfig().getTemplateByKey(key)
                                           .lock(key)) {
            // 将事务包在里面，确保释放锁的时候事务已提交
            return syncTransactionalHelper.call(call);
        }
    }

    /**
     * 分布式同步锁，使用相同的key标注的代码，即便部署在不同的服务器实例上也能【防止同时执行】，避免出现并发问题
     *
     * 锁定时间默认为{@link JedisLock#DEFAULT_LOCK_LEASE_MS}，如果没有在规定时间内执行完，只要线程存活就会自动续期，如果碰到服务器重启就会在锁定期后自动释放
     */
    public void syncCall(String key, SyncTransactionRunnable run) throws Throwable {
        try (JedisLock lock = jedisConfig().getTemplateByKey(key)
                                           .lock(key)) {
            // 将事务包在里面，确保释放锁的时候事务已提交
            syncTransactionalHelper.run(run);
        }
    }
}
