package org.etnaframework.jedis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

/**
 * redis订阅/发布操作集合
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public class JedisOpsPubsub extends JedisOps {

    protected JedisOpsPubsub(JedisTemplate jedisTemplate) {
        super(jedisTemplate);
    }

    /**
     * <pre>
     * PUBLISH channel message
     *
     * 将信息 message 发送到指定的频道 channel 。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(N+M)，其中 N 是频道 channel 的订阅者数量，而 M 则是使用模式订阅(subscribed patterns)的客户端的数量。
     * 返回值：
     * 接收到信息 message 的订阅者数量。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/pub_sub/publish.html">http://redisdoc.com/pub_sub/publish.html</a>
     */
    public Long publish(String channel, String message) {
        try (Jedis jedis = getJedis()) {
            return jedis.publish(channel, message);
        }
    }

    /**
     * <pre>
     * SUBSCRIBE channel [channel ...]
     *
     * 订阅给定的一个或多个频道的信息。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(N)，其中 N 是订阅的频道的数量。
     * 返回值：
     * 接收到的信息
     * </pre>
     *
     * @see <a href="http://redisdoc.com/pub_sub/subscribe.html">http://redisdoc.com/pub_sub/subscribe.html</a>
     */
    public void subscribe(JedisPubSub jedisPubSub, String... channels) {
        try (Jedis jedis = getJedis()) {
            jedis.subscribe(jedisPubSub, channels);
        }
    }

    /**
     * <pre>
     * PSUBSCRIBE pattern [pattern ...]
     *
     * 订阅一个或多个符合给定模式的频道。
     *
     * 每个模式以 * 作为匹配符，比如 it* 匹配所有以 it 开头的频道( it.news 、 it.blog 、 it.tweets 等等)， news.* 匹配所有以 news. 开头的频道( news.it 、 news.global.today 等等)，诸如此类。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(N)， N 是订阅的模式的数量。
     * 返回值：
     * 接收到的信息
     * </pre>
     *
     * @see <a href="http://redisdoc.com/pub_sub/psubscribe.html">http://redisdoc.com/pub_sub/psubscribe.html</a>
     */
    public void psubscribe(JedisPubSub jedisPubSub, String... patterns) {
        try (Jedis jedis = getJedis()) {
            jedis.psubscribe(jedisPubSub, patterns);
        }
    }
}
