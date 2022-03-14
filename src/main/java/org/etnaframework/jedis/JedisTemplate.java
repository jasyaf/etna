package org.etnaframework.jedis;

import java.util.UUID;
import org.etnaframework.core.util.ThreadUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Jedis命令使用模板
 *
 * 根据http://redisdoc.com对redis操作的分类细分具体操作类。
 * 其实官网也是这样分类的https://redis.io/commands#generic
 *
 * 与spring-data-redis主要不同的是，本模板更接近redis的原生操作。
 *
 * 仅支持基本redis命令
 * 更新支持至Redis 3.0
 *
 * Redis命令实现总共有
 * JedisCommands,
 * MultiKeyCommands,
 * AdvancedJedisCommands,
 * ScriptingCommands,
 * BasicCommands,
 * ClusterCommands,
 * SentinelCommands,
 * BinaryJedisCommands,
 * MultiKeyBinaryCommands,
 * AdvancedBinaryJedisCommands,
 * BinaryScriptingCommands
 *
 * 本模板只实现了JedisCommands/MultiKeyCommands/ScriptingCommands
 * </pre>
 *
 * @author YuanHaoliang
 * @version jedis_2.9.0
 * @see <a href="http://redisdoc.com/">http://redisdoc.com/</a>
 */
public class JedisTemplate {

    /** 最大获取连接尝试数 */
    private static final int RETRY_COUNT_LIMIT = 5;

    /** Jedis客户端的唯一ID */
    protected final UUID id = UUID.randomUUID();

    /** Jedis连接池 */
    protected JedisPool jedisPool;

    private JedisOpsKey key = new JedisOpsKey(this);

    private JedisOpsString string = new JedisOpsString(this);

    private JedisOpsHash hash = new JedisOpsHash(this);

    private JedisOpsList list = new JedisOpsList(this);

    private JedisOpsSet set = new JedisOpsSet(this);

    private JedisOpsSortedSet sortedSet = new JedisOpsSortedSet(this);

    private JedisOpsHyperLogLog hyperLogLog = new JedisOpsHyperLogLog(this);

    private JedisOpsGeo geo = new JedisOpsGeo(this);

    private JedisOpsPubsub pubsub = new JedisOpsPubsub(this);

    private JedisOpsTransaction transaction = new JedisOpsTransaction(this);

    private JedisOpsConfig config = new JedisOpsConfig(this);

    private JedisOpsScript script = new JedisOpsScript(this);

    private JedisOpsServer server = new JedisOpsServer(this);

    public JedisTemplate(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * 带重试获取Jedis连接,提供完整的jedis功能。
     * 需要使用 try with resource 来进行自动归还连接池，例如：
     * try(Jedis jedis=jedisTemplate.getJedis()){
     *     // do something.
     * }
     * 建议使用 {@link #execute(JedisRunnable)}
     */
    @Deprecated
    public Jedis getJedis() {
        Jedis jedis = null;
        JedisConnectionException ex = null;
        int tryTimes = 1;
        do {
            try {
                jedis = jedisPool.getResource();
            } catch (JedisConnectionException e) {
                ex = e;
            }
            if (jedis == null) {
                // 休息几毫秒再尝试
                ThreadUtils.sleep(50 * tryTimes);
            }
        } while (jedis == null && tryTimes++ <= RETRY_COUNT_LIMIT);
        if (null == jedis && null != ex) {
            throw ex;
        }
        return jedis;
    }

    /**
     * 带自动重试获取jedis连接的执行(不需要使用try-with-resource)
     *
     * 用法
     * <pre>
     *       jedisTemplate.execute(jedis -> {
     *          jedis.set("aa","bb");
     *       });
     * </pre>
     */
    public void execute(final JedisRunnable runnable) {
        try (Jedis jedis = getJedis()) {
            runnable.run(jedis);
        }
    }

    /**
     * 带自动重试获取jedis连接的执行(不需要使用try-with-resource)
     *
     * 用法
     * <pre>
     *       String result = jedisTemplate.execute(jedis -> {
     *                           return jedis.get("aa");
     *                       });
     * </pre>
     */
    public <V> V execute(final JedisCallable<V> callable) {
        try (Jedis jedis = getJedis()) {
            return callable.call(jedis);
        }
    }

    /**
     * 获取可重入锁
     *
     * jedis的分布式可重入锁JedisLock Java对象实现了java.ut2il.concurrent.locks.Lock接口，同时还支持自动过期解锁。
     *
     * JedisLock lock = jedisTemplate.getLock("anyLock");
     * // 最常见的使用方法
     * lock.lock();
     *
     * // 支持过期解锁功能
     * // 10秒钟以后自动解锁
     * // 无需调用unlock方法手动解锁
     * lock.lock(10, TimeUnit.SECONDS);
     *
     * // 尝试加锁，最多等待100秒，上锁以后10秒自动解锁
     * boolean res = lock.tryLock(100, 10, TimeUnit.SECONDS);
     * ...
     * lock.unlock();
     */
    public JedisLock getLock(Object obj) {
        return new JedisLock(String.valueOf(obj), this);
    }

    /**
     * 获取可重入锁,用try with resource自动释放锁,
     *
     * 例如：
     * try(JedisLock lock = jedisTemplate.lock("anyLock")) {
     * ...
     * }
     */
    public JedisLock lock(Object obj) {
        JedisLock lock = new JedisLock(String.valueOf(obj), this);
        lock.lock();
        return lock;
    }

    public JedisOpsKey key() {
        return key;
    }

    public JedisOpsString string() {
        return string;
    }

    public JedisOpsHash hash() {
        return hash;
    }

    public JedisOpsList list() {
        return list;
    }

    public JedisOpsSet set() {
        return set;
    }

    public JedisOpsSortedSet sortedSet() {
        return sortedSet;
    }

    public JedisOpsHyperLogLog hyperLogLog() {
        return hyperLogLog;
    }

    public JedisOpsGeo geo() {
        return geo;
    }

    public JedisOpsPubsub pubsub() {
        return pubsub;
    }

    public JedisOpsTransaction transaction() {
        return transaction;
    }

    public JedisOpsConfig config() {
        return config;
    }

    public JedisOpsScript script() {
        return script;
    }

    public JedisOpsServer server() {
        return server;
    }

    /**
     * jedis带返回值的执行块
     */
    public interface JedisCallable<V> {

        V call(Jedis jedis);
    }

    /**
     * jedis执行块
     */
    public interface JedisRunnable {

        void run(Jedis jedis);
    }
}
