package org.etnaframework.jedis;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

/**
 * redis的哈希表操作集合
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public class JedisOpsHash extends JedisOps {

    protected JedisOpsHash(JedisTemplate jedisTemplate) {
        super(jedisTemplate);
    }

    /**
     * <pre>
     * HDEL key field [field ...]
     *
     * 删除哈希表 key 中的一个或多个指定域，不存在的域将被忽略。
     *
     * 在Redis2.4以下的版本里， HDEL 每次只能删除单个域，如果你需要在一个原子时间内删除多个域，请将命令包含在 MULTI / EXEC 块内。
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度:
     * O(N)， N 为要删除的域的数量。
     * 返回值:
     * 被成功移除的域的数量，不包括被忽略的域。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hdel.html">http://redisdoc.com/hash/hdel.html</a>
     */
    public Long hdel(String key, String... fields) {
        try (Jedis jedis = getJedis()) {
            return jedis.hdel(key, fields);
        }
    }

    /**
     * <pre>
     * HEXISTS key field
     *
     * 查看哈希表 key 中，给定域 field 是否存在。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 如果哈希表含有给定域，返回 1 。
     * 如果哈希表不含有给定域，或 key 不存在，返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hexists.html">http://redisdoc.com/hash/hexists.html</a>
     */

    public Boolean hexists(String key, String field) {
        try (Jedis jedis = getJedis()) {
            return jedis.hexists(key, field);
        }
    }

    /**
     * <pre>
     * HGET key field
     *
     * 返回哈希表 key 中给定域 field 的值。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 给定域的值。
     * 当给定域不存在或是给定 key 不存在时，返回 nil 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hget.html">http://redisdoc.com/hash/hget.html</a>
     */

    public String hget(String key, String field) {
        try (Jedis jedis = getJedis()) {
            return jedis.hget(key, field);
        }
    }

    /**
     * <pre>
     * HGETALL key
     *
     * 返回哈希表 key 中，所有的域和值。
     *
     * 在返回值里，紧跟每个域名(field name)之后是域的值(value)，所以返回值的长度是哈希表大小的两倍。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(N)， N 为哈希表的大小。
     * 返回值：
     * 以列表形式返回哈希表的域和域的值。
     * 若 key 不存在，返回空列表。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hgetall.html">http://redisdoc.com/hash/hgetall.html</a>
     */

    public Map<String, String> hgetAll(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.hgetAll(key);
        }
    }

    /**
     * <pre>
     * INCRBY key increment
     *
     * 将 key 所储存的值加上增量 increment 。
     *
     * 如果 key 不存在，那么 key 的值会先被初始化为 0 ，然后再执行 INCRBY 命令。
     *
     * 如果值包含错误的类型，或字符串类型的值不能表示为数字，那么返回一个错误。
     *
     * 本操作的值限制在 64 位(bit)有符号数字表示之内。
     *
     * 关于递增(increment) / 递减(decrement)操作的更多信息，参见 INCR 命令。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 加上 increment 之后， key 的值。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/string/incrby.html">http://redisdoc.com/string/incrby.html</a>
     */

    public Long hincrBy(String key, String field, long value) {
        try (Jedis jedis = getJedis()) {
            return jedis.hincrBy(key, field, value);
        }
    }

    public Double hincrByFloat(String key, String field, double value) {
        try (Jedis jedis = getJedis()) {
            return jedis.hincrByFloat(key, field, value);
        }
    }

    /**
     * <pre>
     * HKEYS key
     *
     * 返回哈希表 key 中的所有域。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(N)， N 为哈希表的大小。
     * 返回值：
     * 一个包含哈希表中所有域的表。
     * 当 key 不存在时，返回一个空表。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hkeys.html">http://redisdoc.com/hash/hkeys.html</a>
     */

    public Set<String> hkeys(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.hkeys(key);
        }
    }

    /**
     * <pre>
     * HLEN key
     *
     * 返回哈希表 key 中域的数量。
     *
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 哈希表中域的数量。
     * 当 key 不存在时，返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hlen.html">http://redisdoc.com/hash/hlen.html</a>
     */

    public Long hlen(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.hlen(key);
        }
    }

    /**
     * <pre>
     * HMGET key field [field ...]
     *
     * 返回哈希表 key 中，一个或多个给定域的值。
     *
     * 如果给定的域不存在于哈希表，那么返回一个 nil 值。
     *
     * 因为不存在的 key 被当作一个空哈希表来处理，所以对一个不存在的 key 进行 HMGET 操作将返回一个只带有 nil 值的表。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(N)， N 为给定域的数量。
     * 返回值：
     * 一个包含多个给定域的关联值的表，表值的排列顺序和给定域参数的请求顺序一样。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hmget.html">http://redisdoc.com/hash/hmget.html</a>
     */

    public List<String> hmget(String key, String... fields) {
        try (Jedis jedis = getJedis()) {
            return jedis.hmget(key, fields);
        }
    }

    /**
     * <pre>
     * HMSET key field value [field value ...]
     *
     * 同时将多个 field-value (域-值)对设置到哈希表 key 中。
     *
     * 此命令会覆盖哈希表中已存在的域。
     *
     * 如果 key 不存在，一个空哈希表被创建并执行 HMSET 操作。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(N)， N 为 field-value 对的数量。
     * 返回值：
     * 如果命令执行成功，返回 OK 。
     * 当 key 不是哈希表(hash)类型时，返回一个错误。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hmset.html">http://redisdoc.com/hash/hmset.html</a>
     */

    public String hmset(String key, Map<String, String> hash) {
        try (Jedis jedis = getJedis()) {
            return jedis.hmset(key, hash);
        }
    }

    /**
     * <pre>
     * HSET key field value
     *
     * 将哈希表 key 中的域 field 的值设为 value 。
     *
     * 如果 key 不存在，一个新的哈希表被创建并进行 HSET 操作。
     *
     * 如果域 field 已经存在于哈希表中，旧值将被覆盖。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 如果 field 是哈希表中的一个新建域，并且值设置成功，返回 1 。
     * 如果哈希表中域 field 已经存在且旧值已被新值覆盖，返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hset.html">http://redisdoc.com/hash/hset.html</a>
     */

    public Long hset(String key, String field, String value) {
        try (Jedis jedis = getJedis()) {
            return jedis.hset(key, field, value);
        }
    }

    /**
     * <pre>
     * HSETNX key field value
     *
     * 将哈希表 key 中的域 field 的值设置为 value ，当且仅当域 field 不存在。
     *
     * 若域 field 已经存在，该操作无效。
     *
     * 如果 key 不存在，一个新哈希表被创建并执行 HSETNX 命令。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 设置成功，返回 1 。
     * 如果给定域已经存在且没有操作被执行，返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hsetnx.html">http://redisdoc.com/hash/hsetnx.html</a>
     */

    public Long hsetnx(String key, String field, String value) {
        try (Jedis jedis = getJedis()) {
            return jedis.hsetnx(key, field, value);
        }
    }

    /**
     * <pre>
     * HVALS key
     *
     * 返回哈希表 key 中所有域的值。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(N)， N 为哈希表的大小。
     * 返回值：
     * 一个包含哈希表中所有值的表。
     * 当 key 不存在时，返回一个空表。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/hash/hvals.html">http://redisdoc.com/hash/hvals.html</a>
     */

    public List<String> hvals(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.hvals(key);
        }
    }

    /**
     * <pre>
     * HSCAN key cursor [MATCH pattern] [COUNT count]
     *
     * 具体信息请参考 SCAN 命令。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/scan.html#scan">http://redisdoc.com/key/scan.html#scan</a>
     */

    @Deprecated
    @SuppressWarnings("deprecation")
    public ScanResult<Entry<String, String>> hscan(String key, int cursor) {
        try (Jedis jedis = getJedis()) {
            return jedis.hscan(key, cursor);
        }
    }

    /**
     * <pre>
     * HSCAN key cursor [MATCH pattern] [COUNT count]
     *
     * 具体信息请参考 SCAN 命令。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/scan.html">http://redisdoc.com/key/scan.html</a>
     */

    public ScanResult<Entry<String, String>> hscan(String key, String cursor) {
        try (Jedis jedis = getJedis()) {
            return jedis.hscan(key, cursor);
        }
    }

    public ScanResult<Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
        try (Jedis jedis = getJedis()) {
            return jedis.hscan(key, cursor, params);
        }
    }
}
