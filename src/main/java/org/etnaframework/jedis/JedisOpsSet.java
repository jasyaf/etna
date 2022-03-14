package org.etnaframework.jedis;

import java.util.List;
import java.util.Set;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

/**
 * redis集合操作集合
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public class JedisOpsSet extends JedisOps {

    protected JedisOpsSet(JedisTemplate jedisTemplate) {
        super(jedisTemplate);
    }

    /**
     * <pre>
     * SADD key member [member ...]
     *
     * 将一个或多个 member 元素加入到集合 key 当中，已经存在于集合的 member 元素将被忽略。
     *
     * 假如 key 不存在，则创建一个只包含 member 元素作成员的集合。
     *
     * 当 key 不是集合类型时，返回一个错误。
     *
     * 在Redis2.4版本以前， SADD 只接受单个 member 值。
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(N)， N 是被添加的元素的数量。
     * 返回值:
     * 被添加到集合中的新元素的数量，不包括被忽略的元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/sadd.html">http://redisdoc.com/set/sadd.html</a>
     */
    public Long sadd(String key, String... member) {
        try (Jedis jedis = getJedis()) {
            return jedis.sadd(key, member);
        }
    }

    /**
     * <pre>
     * SCARD key
     *
     * 返回集合 key 的基数(集合中元素的数量)。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(1)
     * 返回值：
     * 集合的基数。
     * 当 key 不存在时，返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/scard.html">http://redisdoc.com/set/scard.html</a>
     */
    public Long scard(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.scard(key);
        }
    }

    /**
     * <pre>
     * SDIFF key [key ...]
     *
     * 返回一个集合的全部成员，该集合是所有给定集合之间的差集。
     *
     * 不存在的 key 被视为空集。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(N)， N 是所有给定集合的成员数量之和。
     * 返回值:
     * 一个包含差集成员的列表。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/sdiff.html">http://redisdoc.com/set/sdiff.html</a>
     */
    public Set<String> sdiff(String[] keys) {
        try (Jedis jedis = getJedis()) {
            return jedis.sdiff(keys);
        }
    }

    /**
     * <pre>
     *     SDIFFSTORE destination key [key ...]
     *
     * 这个命令的作用和 SDIFF 类似，但它将结果保存到 destination 集合，而不是简单地返回结果集。
     *
     * 如果 destination 集合已经存在，则将其覆盖。
     *
     * destination 可以是 key 本身。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(N)， N 是所有给定集合的成员数量之和。
     * 返回值:
     * 结果集中的元素数量。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/sdiffstore.html">http://redisdoc.com/set/sdiffstore.html</a>
     */
    public Long sdiffstore(String dstkey, String... keys) {
        try (Jedis jedis = getJedis()) {
            return jedis.sdiffstore(dstkey, keys);
        }
    }

    /**
     * <pre>
     *     SINTER key [key ...]
     *
     * 返回一个集合的全部成员，该集合是所有给定集合的交集。
     *
     * 不存在的 key 被视为空集。
     *
     * 当给定集合当中有一个空集时，结果也为空集(根据集合运算定律)。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(N * M)， N 为给定集合当中基数最小的集合， M 为给定集合的个数。
     * 返回值:
     * 交集成员的列表。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/sinter.html">http://redisdoc.com/set/sinter.html</a>
     */
    public Set<String> sinter(String... keys) {
        try (Jedis jedis = getJedis()) {
            return jedis.sinter(keys);
        }
    }

    /**
     * <pre>
     *     SINTERSTORE destination key [key ...]
     *
     * 这个命令类似于 SINTER 命令，但它将结果保存到 destination 集合，而不是简单地返回结果集。
     *
     * 如果 destination 集合已经存在，则将其覆盖。
     *
     * destination 可以是 key 本身。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(N * M)， N 为给定集合当中基数最小的集合， M 为给定集合的个数。
     * 返回值:
     * 结果集中的成员数量。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/sinterstore.html">http://redisdoc.com/set/sinterstore.html</a>
     */
    public Long sinterstore(String dstkey, String... keys) {
        try (Jedis jedis = getJedis()) {
            return jedis.sinterstore(dstkey, keys);
        }
    }

    /**
     * <pre>
     * SISMEMBER key member
     *
     * 判断 member 元素是否集合 key 的成员。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(1)
     * 返回值:
     * 如果 member 元素是集合的成员，返回 1 。
     * 如果 member 元素不是集合的成员，或 key 不存在，返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/sismember.html">http://redisdoc.com/set/sismember.html</a>
     */
    public Boolean sismember(String key, String member) {
        try (Jedis jedis = getJedis()) {
            return jedis.sismember(key, member);
        }
    }

    /**
     * <pre>
     * SMEMBERS key
     *
     * 返回集合 key 中的所有成员。
     *
     * 不存在的 key 被视为空集合。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(N)， N 为集合的基数。
     * 返回值:
     * 集合中的所有成员。
     * </pre>
     *
     * @return 注意！返回的是一个用set包装的list！
     * @see <a href="http://redisdoc.com/set/smembers.html">http://redisdoc.com/set/smembers.html</a>
     */
    public Set<String> smembers(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.smembers(key);
        }
    }

    /**
     * <pre>
     *     SMOVE source destination member
     *
     * 将 member 元素从 source 集合移动到 destination 集合。
     *
     * SMOVE 是原子性操作。
     *
     * 如果 source 集合不存在或不包含指定的 member 元素，则 SMOVE 命令不执行任何操作，仅返回 0 。否则， member 元素从 source 集合中被移除，并添加到 destination 集合中去。
     *
     * 当 destination 集合已经包含 member 元素时， SMOVE 命令只是简单地将 source 集合中的 member 元素删除。
     *
     * 当 source 或 destination 不是集合类型时，返回一个错误。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(1)
     * 返回值:
     * 如果 member 元素被成功移除，返回 1 。
     * 如果 member 元素不是 source 集合的成员，并且没有任何操作对 destination 集合执行，那么返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/smove.html">http://redisdoc.com/set/smove.html</a>
     */
    public Long smove(String srckey, String dstkey, String member) {
        try (Jedis jedis = getJedis()) {
            return jedis.smove(srckey, dstkey, member);
        }
    }

    /**
     * <pre>
     * SPOP key
     *
     * 移除并返回集合中的一个随机元素。
     *
     * 如果只想获取一个随机元素，但不想该元素从集合中被移除的话，可以使用 SRANDMEMBER 命令。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(1)
     * 返回值:
     * 被移除的随机元素。
     * 当 key 不存在或 key 是空集时，返回 nil 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/spop.html">http://redisdoc.com/set/spop.html</a>
     */
    public String spop(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.spop(key);
        }
    }

    /**
     * <pre>
     * SPOP key
     *
     * 移除并返回集合中的一个随机元素。
     *
     * 如果只想获取一个随机元素，但不想该元素从集合中被移除的话，可以使用 SRANDMEMBER 命令。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(1)
     * 返回值:
     * 被移除的随机元素。
     * 当 key 不存在或 key 是空集时，返回 nil 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/spop.html">http://redisdoc.com/set/spop.html</a>
     */
    public Set<String> spop(String key, long count) {
        try (Jedis jedis = getJedis()) {
            return jedis.spop(key, count);
        }
    }

    /**
     * <pre>
     * SRANDMEMBER key [count]
     *
     * 如果命令执行时，只提供了 key 参数，那么返回集合中的一个随机元素。
     *
     * 从 Redis 2.6 版本开始， SRANDMEMBER 命令接受可选的 count 参数：
     *
     * 如果 count 为正数，且小于集合基数，那么命令返回一个包含 count 个元素的数组，数组中的元素各不相同。如果 count 大于等于集合基数，那么返回整个集合。
     * 如果 count 为负数，那么命令返回一个数组，数组中的元素可能会重复出现多次，而数组的长度为 count 的绝对值。
     * 该操作和 SPOP 相似，但 SPOP 将随机元素从集合中移除并返回，而 SRANDMEMBER 则仅仅返回随机元素，而不对集合进行任何改动。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * 只提供 key 参数时为 O(1) 。
     * 如果提供了 count 参数，那么为 O(N) ，N 为返回数组的元素个数。
     * 返回值:
     * 只提供 key 参数时，返回一个元素；如果集合为空，返回 nil 。
     * 如果提供了 count 参数，那么返回一个数组；如果集合为空，返回空数组。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/srandmember.html">http://redisdoc.com/set/srandmember.html</a>
     */
    public String srandmember(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.srandmember(key);
        }
    }

    /**
     * <pre>
     * SRANDMEMBER key [count]
     *
     * 如果命令执行时，只提供了 key 参数，那么返回集合中的一个随机元素。
     *
     * 从 Redis 2.6 版本开始， SRANDMEMBER 命令接受可选的 count 参数：
     *
     * 如果 count 为正数，且小于集合基数，那么命令返回一个包含 count 个元素的数组，数组中的元素各不相同。如果 count 大于等于集合基数，那么返回整个集合。
     * 如果 count 为负数，那么命令返回一个数组，数组中的元素可能会重复出现多次，而数组的长度为 count 的绝对值。
     * 该操作和 SPOP 相似，但 SPOP 将随机元素从集合中移除并返回，而 SRANDMEMBER 则仅仅返回随机元素，而不对集合进行任何改动。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * 只提供 key 参数时为 O(1) 。
     * 如果提供了 count 参数，那么为 O(N) ，N 为返回数组的元素个数。
     * 返回值:
     * 只提供 key 参数时，返回一个元素；如果集合为空，返回 nil 。
     * 如果提供了 count 参数，那么返回一个数组；如果集合为空，返回空数组。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/srandmember.html">http://redisdoc.com/set/srandmember.html</a>
     */
    public List<String> srandmember(String key, int count) {
        try (Jedis jedis = getJedis()) {
            return jedis.srandmember(key, count);
        }
    }

    /**
     * <pre>
     * SREM key member [member ...]
     *
     * 移除集合 key 中的一个或多个 member 元素，不存在的 member 元素会被忽略。
     *
     * 当 key 不是集合类型，返回一个错误。
     *
     * 在 Redis 2.4 版本以前， SREM 只接受单个 member 值。
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(N)， N 为给定 member 元素的数量。
     * 返回值:
     * 被成功移除的元素的数量，不包括被忽略的元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/srem.html">http://redisdoc.com/set/srem.html</a>
     */
    public Long srem(String key, String... member) {
        try (Jedis jedis = getJedis()) {
            return jedis.srem(key, member);
        }
    }

    /**
     * <pre>
     *     SUNION key [key ...]
     *
     * 返回一个集合的全部成员，该集合是所有给定集合的并集。
     *
     * 不存在的 key 被视为空集。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(N)， N 是所有给定集合的成员数量之和。
     * 返回值:
     * 并集成员的列表。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/sunion.html">http://redisdoc.com/set/sunion.html</a>
     */
    public Set<String> sunion(String... keys) {
        try (Jedis jedis = getJedis()) {
            return jedis.sunion(keys);
        }
    }

    /**
     * <pre>
     *     SUNIONSTORE destination key [key ...]
     *
     * 这个命令类似于 SUNION 命令，但它将结果保存到 destination 集合，而不是简单地返回结果集。
     *
     * 如果 destination 已经存在，则将其覆盖。
     *
     * destination 可以是 key 本身。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(N)， N 是所有给定集合的成员数量之和。
     * 返回值:
     * 结果集中的元素数量。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/set/sunionstore.html">http://redisdoc.com/set/sunionstore.html</a>
     */
    public Long sunionstore(String dstkey, String... keys) {
        try (Jedis jedis = getJedis()) {
            return jedis.sunionstore(dstkey, keys);
        }
    }

    /**
     * <pre>
     * SSCAN key cursor [MATCH pattern] [COUNT count]
     *
     * 详细信息请参考 SCAN 命令。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/scan.html#scan">http://redisdoc.com/key/scan.html#scan</a>
     */
    @Deprecated
    public ScanResult<String> sscan(String key, int cursor) {
        try (Jedis jedis = getJedis()) {
            return jedis.sscan(key, cursor);
        }
    }

    /**
     * <pre>
     * SSCAN key cursor [MATCH pattern] [COUNT count]
     *
     * 详细信息请参考 SCAN 命令。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/scan.html#scan">http://redisdoc.com/key/scan.html#scan</a>
     */
    public ScanResult<String> sscan(String key, String cursor) {
        try (Jedis jedis = getJedis()) {
            return jedis.sscan(key, cursor);
        }
    }

    /**
     * <pre>
     * SSCAN key cursor [MATCH pattern] [COUNT count]
     *
     * 详细信息请参考 SCAN 命令。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/scan.html#scan">http://redisdoc.com/key/scan.html#scan</a>
     */
    public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
        try (Jedis jedis = getJedis()) {
            return jedis.sscan(key, cursor, params);
        }
    }
}
