package org.etnaframework.jedis;

import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;

/**
 * redis事务操作集合
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public class JedisOpsTransaction extends JedisOps {

    protected JedisOpsTransaction(JedisTemplate jedisTemplate) {
        super(jedisTemplate);
    }

    /**
     * <pre>
     * 以pipeline方式发送命令
     * 当有大量数据需要插入的时候，使用pipeline的效率会非常高！
     * 此方法只提供命令发送，不对redis回包返回或处理。
     *
     * 使用示例：
     *
     *         jedisTemplate.pipelined(p->{
     *                 p.set("foo1", "test");
     *                 p.set("foo2", "test");
     *         });
     * </pre>
     */
    public void pipelined(final JedisPipelineContent rpc) {
        try (Jedis jedis = getJedis()) {
            Pipeline p = jedis.pipelined();
            rpc.pipelined(p);
            p.sync();
        }
    }

    /**
     * <pre>
     * 以事务方式批量执行redis命令。
     * 当对数据一致性有特别高要求时，需要使用事务来改变状态。
     * 此方法只提供命令发送，不对redis回包返回或处理。
     * 使用示例：
     *
     *         jedisTemplate.transaction(t-> {
     *                 t.set("foo1", "test");
     *                 t.set("foo2", "test");
     *         });
     *
     * watchKeys: 监视一个(或多个) key ，如果在事务执行之前这个(或这些) key 被其他命令所改动，那么事务将被打断。
     *
     * </pre>
     *
     * @return true:事务执行成功，false:没执行成功。
     */
    public boolean transaction(final JedisTransactionContent rtc, String... watchKeys) {
        try (Jedis jedis = getJedis()) {
            if (watchKeys.length > 0) {
                jedis.watch(watchKeys);
            }
            Transaction t = jedis.multi();
            rtc.transaction(t);
            List<Object> exec = t.exec();
            return !exec.isEmpty();
        }
    }

    /**
     * 使用事务接口(接口可用lamba表达式)
     */
    public interface JedisTransactionContent {

        void transaction(Transaction t);
    }

    /**
     * 使用pipeline接口,pipeline用于批量操作(接口可用lamba表达式)
     */
    public interface JedisPipelineContent {

        void pipelined(Pipeline p);
    }
}
