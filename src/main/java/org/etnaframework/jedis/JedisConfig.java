package org.etnaframework.jedis;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.util.HumanReadableUtils;
import org.etnaframework.core.util.ThreadUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisShardInfo;

/**
 * jedis连接配置辅助类
 *
 * @author BlackCat
 * @since 2017-05-11
 */
public class JedisConfig implements InitializingBean {

    protected final Logger log = Log.getLogger(getClass());

    private String host;

    private int port;

    private String password;

    /** redis连接/操作超时时间配置，单位毫秒 */
    private int timeoutMs;

    /** 连接池：最大连接数 */
    private int maxTotal;

    /** 连接池：最大空闲数 */
    private int maxIdle;

    /** 连接池：最大建立连接等待时间 */
    private long maxWaitMs;

    /** redis分库分了多少个，阿里云默认是256 (0-255) */
    private int dbNum = 256;

    /** 阿里云内网不稳定，为提高成功率加入重试机制 */
    private int maxAttempts = 2;

    private JedisTemplate[] jedisTemplates;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    public int getMaxIdle() {
        return maxIdle;
    }

    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }

    public long getMaxWaitMs() {
        return maxWaitMs;
    }

    public void setMaxWaitMs(long maxWaitMs) {
        this.maxWaitMs = maxWaitMs;
    }

    public int getDbNum() {
        return dbNum;
    }

    public void setDbNum(int dbNum) {
        this.dbNum = dbNum;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * 初始化redis连接，使用实例下所有的库
     */
    @Override
    public void afterPropertiesSet() throws Exception {

        long start = System.currentTimeMillis();

        JedisShardInfo shared = new JedisShardInfo(host, port, timeoutMs);
        shared.setPassword(password);

        JedisPoolConfig pool = new JedisPoolConfig();
        pool.setMaxTotal(maxTotal);
        pool.setMaxIdle(maxIdle);
        pool.setMaxWaitMillis(maxWaitMs);

        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMaxWaitMillis(maxWaitMs);

        jedisTemplates = new JedisTemplate[dbNum];

        CountDownLatch latch = new CountDownLatch(dbNum);
        // 为了加速初始化采用并行操作
        for (int i = 0; i < jedisTemplates.length; i++) {
            int index = i;
            ThreadUtils.getDefault()
                       .execute(() -> {
                           try {
                               jedisTemplates[index] = new JedisTemplate(new JedisPool(config, host, port, timeoutMs, password, index));
                           } finally {
                               latch.countDown();
                           }
                       });
        }

        // 聚合结果，检查一遍是否都初始化成功了
        latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        for (int i = 0; i < dbNum; i++) {
            if (null == jedisTemplates[i]) {
                throw new IllegalStateException("连接到" + host + ":" + port + "的jedis实例初始化失败");
            }
        }

        log.info("JedisConfig for " + host + ":" + port + " inited [" + HumanReadableUtils.timeSpan(System.currentTimeMillis() - start) + "]");
    }

    /**
     * 获取下标对应库的{@link JedisTemplate}，不检查下标，请调用方自行确保不会越界
     */
    public JedisTemplate db(int index) {
        return jedisTemplates[index];
    }

    /**
     * 获取key对应的redisDB，采用直接【整数取模】的方式确定下标
     */
    public JedisTemplate getTemplateByKey(int key) {
        return db(Math.abs(key) % getDbNum());
    }

    /**
     * 获取key对应的redisDB，采用【取hashCode整数取模】的方式确定下标
     */
    public JedisTemplate getTemplateByKey(String key) {
        return db(getDbIndexByKey(key));
    }

    public int getDbIndexByKey(String key) {
        return Math.abs(String.valueOf(key)
                              .hashCode()) % getDbNum();
    }
}
