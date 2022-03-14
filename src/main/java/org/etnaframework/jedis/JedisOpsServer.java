package org.etnaframework.jedis;

import java.util.List;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import redis.clients.jedis.Jedis;

/**
 * redis服务器操作集合
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public class JedisOpsServer extends JedisOps {

    protected JedisOpsServer(JedisTemplate jedisTemplate) {
        super(jedisTemplate);
    }

    /**
     * 获取服务器的时间，精度到毫秒（10^-3秒）
     *
     * @see <a href="http://redisdoc.com/server/time.html">http://redisdoc.com/server/time.html</a>
     */
    public Datetime time() {
        try (Jedis jedis = getJedis()) {
            List<String> r = jedis.time();
            long ms = Long.parseLong(r.get(0)) * 1000L + Long.parseLong(r.get(1)) / 1000L;
            return new Datetime(ms);
        }
    }

    /**
     * 获取服务器的时间，返回两部分，第一部分是当前到秒的时间戳（UNIX时间戳），第二部分是当前秒过的微秒（10^-6秒）
     *
     * @see <a href="http://redisdoc.com/server/time.html">http://redisdoc.com/server/time.html</a>
     */
    public long[] timeInMicroSecond() {
        try (Jedis jedis = getJedis()) {
            List<String> r = jedis.time();
            return new long[] {
                Long.parseLong(r.get(0)),
                Long.parseLong(r.get(1))
            };
        }
    }
}
