package org.etnaframework.jedis;

import redis.clients.jedis.Jedis;

/**
 * Jedis操作的父类
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public abstract class JedisOps {

    protected JedisTemplate jedisTemplate;

    protected JedisOps(JedisTemplate jedisTemplate) {
        this.jedisTemplate = jedisTemplate;
    }

    protected Jedis getJedis() {
        return jedisTemplate.getJedis();
    }
}
