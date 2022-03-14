package org.etnaframework.jedis;

import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScriptingCommands;

/**
 * redis脚本操作集合
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public class JedisOpsScript extends JedisOps implements ScriptingCommands {

    protected JedisOpsScript(JedisTemplate jedisTemplate) {
        super(jedisTemplate);
    }

    /**
     * @see <a href="http://redisdoc.com/script/eval.html">http://redisdoc.com/script/eval.html</a>
     */
    @Override
    public Object eval(String script, int keyCount, String... params) {
        try (Jedis jedis = getJedis()) {
            return jedis.eval(script, keyCount, params);
        }
    }

    /**
     * @see <a href="http://redisdoc.com/script/eval.html">http://redisdoc.com/script/eval.html</a>
     */
    @Override
    public Object eval(String script, List<String> keys, List<String> args) {
        try (Jedis jedis = getJedis()) {
            return jedis.eval(script, keys, args);
        }
    }

    /**
     * @see <a href="http://redisdoc.com/script/eval.html">http://redisdoc.com/script/eval.html</a>
     */
    @Override
    public Object eval(String script) {
        try (Jedis jedis = getJedis()) {
            return jedis.eval(script);
        }
    }

    /**
     * @see <a href="http://redisdoc.com/script/evalsha.html">http://redisdoc.com/script/evalsha.html</a>
     */
    @Override
    public Object evalsha(String script) {
        try (Jedis jedis = getJedis()) {
            return jedis.evalsha(script);
        }
    }

    /**
     * @see <a href="http://redisdoc.com/script/evalsha.html">http://redisdoc.com/script/evalsha.html</a>
     */
    @Override
    public Object evalsha(String sha1, List<String> keys, List<String> args) {
        try (Jedis jedis = getJedis()) {
            return jedis.evalsha(sha1, keys, args);
        }
    }

    /**
     * @see <a href="http://redisdoc.com/script/evalsha.html">http://redisdoc.com/script/evalsha.html</a>
     */
    @Override
    public Object evalsha(String sha1, int keyCount, String... params) {
        try (Jedis jedis = getJedis()) {
            return jedis.evalsha(sha1, keyCount, params);
        }
    }

    /**
     * @see <a href="http://redisdoc.com/script/script_exists.html">http://redisdoc.com/script/script_exists.html</a>
     */
    @Override
    public Boolean scriptExists(String sha1) {
        try (Jedis jedis = getJedis()) {
            return jedis.scriptExists(sha1);
        }
    }

    /**
     * @see <a href="http://redisdoc.com/script/script_exists.html">http://redisdoc.com/script/script_exists.html</a>
     */
    @Override
    public List<Boolean> scriptExists(String... sha1) {
        try (Jedis jedis = getJedis()) {
            return jedis.scriptExists(sha1);
        }
    }

    /**
     * @see <a href="http://redisdoc.com/script/script_load.html">http://redisdoc.com/script/script_load.html</a>
     */
    @Override
    public String scriptLoad(String script) {
        try (Jedis jedis = getJedis()) {
            return jedis.scriptLoad(script);
        }
    }
}
