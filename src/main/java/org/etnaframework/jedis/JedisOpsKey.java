package org.etnaframework.jedis;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.SortingParams;

/**
 * redis的简单键-值操作集合
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public class JedisOpsKey extends JedisOps {

    protected JedisOpsKey(JedisTemplate jedisTemplate) {
        super(jedisTemplate);
    }

    /**
     * <pre>
     * DEL key [key ...]
     *
     * 删除给定的一个或多个 key 。
     *
     * @see #del(String...)
     * @see <a href="http://redisdoc.com/key/del.html">http://redisdoc.com/key/del.html</a>
     * </pre>
     */
    public Long del(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.del(key);
        }
    }

    /**
     * <pre>
     * DEL key [key ...]
     *
     * 删除给定的一个或多个 key 。
     *
     * 不存在的 key 会被忽略。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(N)， N 为被删除的 key 的数量。
     * 删除单个字符串类型的 key ，时间复杂度为O(1)。
     * 删除单个列表、集合、有序集合或哈希表类型的 key ，时间复杂度为O(M)， M 为以上数据结构内的元素数量。
     * 返回值：
     * 被删除 key 的数量。
     *
     * @see <a href="http://redisdoc.com/key/del.html">http://redisdoc.com/key/del.html</a>
     * </pre>
     */
    public long del(String... keys) {
        try (Jedis jedis = getJedis()) {
            return jedis.del(keys);
        }
    }

    /**
     * <pre>
     * EXISTS key
     *
     * 检查给定 key 是否存在。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 若 key 存在，返回 1 ，否则返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/exists.html">http://redisdoc.com/key/exists.html</a>
     */
    public Long exists(String... keys) {

        try (Jedis jedis = getJedis()) {
            return jedis.exists(keys);
        }
    }

    /**
     * <pre>
     * EXISTS key
     *
     * 检查给定 key 是否存在。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 若 key 存在，返回 1 ，否则返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/exists.html">http://redisdoc.com/key/exists.html</a>
     */
    public boolean exists(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.exists(key);
        }
    }

    /**
     * <pre>
     * EXPIRE key seconds
     *
     * 为给定 key 设置生存时间，当 key 过期时(生存时间为 0 )，它会被自动删除。
     *
     * 在 Redis 中，带有生存时间的 key 被称为『易失的』(volatile)。
     *
     * 生存时间可以通过使用 DEL 命令来删除整个 key 来移除，或者被 SET 和 GETSET 命令覆写(overwrite)，这意味着，如果一个命令只是修改(alter)一个带生存时间的 key 的值而不是用一个新的 key
     * 值来代替(replace)它的话，那么生存时间不会被改变。
     *
     * 比如说，对一个 key 执行 INCR 命令，对一个列表进行 LPUSH 命令，或者对一个哈希表执行 HSET 命令，这类操作都不会修改 key 本身的生存时间。
     *
     * 另一方面，如果使用 RENAME 对一个 key 进行改名，那么改名后的 key 的生存时间和改名前一样。
     *
     * RENAME 命令的另一种可能是，尝试将一个带生存时间的 key 改名成另一个带生存时间的 another_key ，这时旧的 another_key (以及它的生存时间)会被删除，然后旧的 key 会改名为
     * another_key ，因此，新的 another_key 的生存时间也和原本的 key 一样。
     *
     * 使用 PERSIST 命令可以在不删除 key 的情况下，移除 key 的生存时间，让 key 重新成为一个『持久的』(persistent) key 。
     *
     * 更新生存时间
     *
     * 可以对一个已经带有生存时间的 key 执行 EXPIRE 命令，新指定的生存时间会取代旧的生存时间。
     *
     * 过期时间的精确度
     *
     * 在 Redis 2.4 版本中，过期时间的延迟在 1 秒钟之内 —— 也即是，就算 key 已经过期，但它还是可能在过期之后一秒钟之内被访问到，而在新的 Redis 2.6 版本中，延迟被降低到 1 毫秒之内。
     *
     * Redis 2.1.3 之前的不同之处
     *
     * 在 Redis 2.1.3 之前的版本中，修改一个带有生存时间的 key 会导致整个 key 被删除，这一行为是受当时复制(replication)层的限制而作出的，现在这一限制已经被修复。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 设置成功返回 1 。
     * 当 key 不存在或者不能为 key 设置生存时间时(比如在低于 2.1.3 版本的 Redis 中你尝试更新 key 的生存时间)，返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/expire.html">http://redisdoc.com/key/expire.html</a>
     */
    public long expire(String key, int seconds) {
        try (Jedis jedis = getJedis()) {
            return jedis.expire(key, seconds);
        }
    }

    /**
     * <pre>
     * EXPIREAT key timestamp
     *
     * EXPIREAT 的作用和 EXPIRE 类似，都用于为 key 设置生存时间。
     *
     * 不同在于 EXPIREAT 命令接受的时间参数是 UNIX 时间戳(unix timestamp)。
     *
     * 可用版本：
     * >= 1.2.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 如果生存时间设置成功，返回 1 。
     * 当 key 不存在或没办法设置生存时间，返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/expireat.html">http://redisdoc.com/key/expireat.html</a>
     */
    public Long expireAt(String key, long unixTime) {
        try (Jedis jedis = getJedis()) {
            return jedis.expireAt(key, unixTime);
        }
    }

    /**
     * <pre>
     * 使用scan命令代替keys获取指定pattern的所有key
     *
     * KEYS pattern
     *
     * 查找所有符合给定模式 pattern 的 key 。
     *
     * KEYS * 匹配数据库中所有 key 。
     * KEYS h?llo 匹配 hello ， hallo 和 hxllo 等。
     * KEYS h*llo 匹配 hllo 和 heeeeello 等。
     * KEYS h[ae]llo 匹配 hello 和 hallo ，但不匹配 hillo 。
     * 特殊符号用 \ 隔开
     *
     * KEYS 的速度非常快，但在一个大的数据库中使用它仍然可能造成性能问题，如果你需要从一个数据集中查找特定的 key ，你最好还是用 Redis 的集合结构(set)来代替。
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(N)， N 为数据库中 key 的数量。
     * 返回值：
     * 符合给定模式的 key 列表。：
     *
     * @see <a href="http://redisdoc.com/database/keys.html">http://redisdoc.com/database/keys.html</a>
     * @see <a href="http://redisdoc.com/database/scan.html">http://redisdoc.com/database/scan.html</a>
     * </pre>
     */
    public Set<String> keys(String pattern) {
        try (Jedis jedis = getJedis()) {

            ScanParams scanParams = new ScanParams().match(pattern).count(1000);
            ScanResult<String> scanResult = jedis.scan(ScanParams.SCAN_POINTER_START, scanParams);
            // 使用set保证去重
            Set<String> keySet=new LinkedHashSet<>(scanResult.getResult());
            while(!ScanParams.SCAN_POINTER_START.equals(scanResult.getStringCursor())){
                // 直到返回游标为"0"才结束获取
                scanResult=jedis.scan(scanResult.getStringCursor(), scanParams);
                keySet.addAll(scanResult.getResult());
            }

            return keySet;

//            return jedis.keys(pattern);
        }
    }

    /**
     * <pre>
     * MOVE key db
     *
     * 将当前数据库的 key 移动到给定的数据库 db 当中。
     *
     * 如果当前数据库(源数据库)和给定数据库(目标数据库)有相同名字的给定 key ，或者 key 不存在于当前数据库，那么 MOVE 没有任何效果。
     *
     * 因此，也可以利用这一特性，将 MOVE 当作锁(locking)原语(primitive)。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 移动成功返回 1 ，失败则返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/move.html">http://redisdoc.com/key/move.html</a>
     */
    public Long move(String key, int dbIndex) {
        try (Jedis jedis = getJedis()) {
            return jedis.move(key, dbIndex);
        }
    }

    /**
     * <pre>
     * PERSIST key
     *
     * 移除给定 key 的生存时间，将这个 key 从『易失的』(带生存时间 key )转换成『持久的』(一个不带生存时间、永不过期的 key )。
     *
     * 可用版本：
     * >= 2.2.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 当生存时间移除成功时，返回 1 .
     * 如果 key 不存在或 key 没有设置生存时间，返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/persist.html">http://redisdoc.com/key/persist.html</a>
     */
    public Long persist(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.persist(key);
        }
    }

    /**
     * <pre>
     *     PEXPIRE key milliseconds
     *
     * 这个命令和 EXPIRE 命令的作用类似，但是它以毫秒为单位设置 key 的生存时间，而不像 EXPIRE 命令那样，以秒为单位。
     *
     * 可用版本：
     * >= 2.6.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 设置成功，返回 1
     * key 不存在或设置失败，返回 0
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/pexpire.html">http://redisdoc.com/key/pexpire.html</a>
     */
    public Long pexpire(String key, long milliseconds) {
        try (Jedis jedis = getJedis()) {
            return jedis.pexpire(key, milliseconds);
        }
    }

    /**
     * <pre>
     *     PEXPIREAT key milliseconds-timestamp
     *
     * 这个命令和 EXPIREAT 命令类似，但它以毫秒为单位设置 key 的过期 unix 时间戳，而不是像 EXPIREAT 那样，以秒为单位。
     *
     * 可用版本：
     * >= 2.6.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 如果生存时间设置成功，返回 1 。
     * 当 key 不存在或没办法设置生存时间时，返回 0 。(查看 EXPIRE 命令获取更多信息)
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/pexpireat.html">http://redisdoc.com/key/pexpireat.html</a>
     */
    public Long pexpireAt(String key, long millisecondsTimestamp) {
        try (Jedis jedis = getJedis()) {
            return jedis.pexpireAt(key, millisecondsTimestamp);
        }
    }

    /**
     * <pre>
     * TTL key
     *
     * 以秒为单位，返回给定 key 的剩余生存时间(TTL, time to live)。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 当 key 不存在时，返回 -2 。
     * 当 key 存在但没有设置剩余生存时间时，返回 -1 。
     * 否则，以秒为单位，返回 key 的剩余生存时间。
     * 在 Redis 2.8 以前，当 key 不存在，或者 key 没有设置剩余生存时间时，命令都返回 -1 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/ttl.html">http://redisdoc.com/key/ttl.html</a>
     */
    public long ttl(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.ttl(key);
        }
    }

    /**
     * <pre>
     * PTTL key
     *
     * 这个命令类似于 TTL 命令，但它以毫秒为单位返回 key 的剩余生存时间，而不是像 TTL 命令那样，以秒为单位。
     *
     * 可用版本：
     * >= 2.6.0
     * 复杂度：
     * O(1)
     * 返回值：
     * 当 key 不存在时，返回 -2 。
     * 当 key 存在但没有设置剩余生存时间时，返回 -1 。
     * 否则，以毫秒为单位，返回 key 的剩余生存时间。
     *
     * 在 Redis 2.8 以前，当 key 不存在，或者 key 没有设置剩余生存时间时，命令都返回 -1 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/pttl.html">http://redisdoc.com/key/pttl.html</a>
     */
    public long pttl(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.pttl(key);
        }
    }

    /**
     * <pre>
     * RANDOMKEY
     *
     * 从当前数据库中随机返回(不删除)一个 key
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 当数据库不为空时，返回一个 key 。
     * 当数据库为空时，返回 nil
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/randomkey.html">http://redisdoc.com/key/randomkey.html</a>
     */
    public String randomKey() {
        try (Jedis jedis = getJedis()) {
            return jedis.randomKey();
        }
    }

    /**
     * <pre>
     *
     * RENAME key newkey
     *
     * 将 key 改名为 newkey
     *
     * 当 key 和 newkey 相同，或者 key 不存在时，返回一个错误
     *
     * 当 newkey 已经存在时， RENAME 命令将覆盖旧值
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 改名成功时提示 OK ，失败时候返回一个错误。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/rename.html">http://redisdoc.com/key/rename.html</a>
     */
    public String rename(String oldkey, String newkey) {
        try (Jedis jedis = getJedis()) {
            return jedis.rename(oldkey, newkey);
        }
    }

    /**
     * <pre>
     * RENAMENX key newkey
     *
     * 当且仅当 newkey 不存在时，将 key 改名为 newkey 。
     *
     * 当 key 不存在时，返回一个错误。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 修改成功时，返回 1 。
     * 如果 newkey 已经存在，返回 0
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/renamenx.html">http://redisdoc.com/key/renamenx.html</a>
     */
    public Long renamenx(String oldkey, String newkey) {
        try (Jedis jedis = getJedis()) {
            return jedis.renamenx(oldkey, newkey);
        }
    }

    /**
     * <pre>
     * SORT
     * SORT key [BY pattern] [LIMIT offset count] [GET pattern [GET pattern ...]] [ASC | DESC] [ALPHA] [STORE
     * destination]
     *
     * 返回或保存给定列表、集合、有序集合 key 中经过排序的元素。
     *
     * 排序默认以数字作为对象，值被解释为双精度浮点数，然后进行比较。
     *
     * 一般 SORT 用法
     * 最简单的 SORT 使用方法是 SORT key 和 SORT key DESC ：
     *
     * SORT key 返回键值从小到大排序的结果。
     * SORT key DESC 返回键值从大到小排序的结果。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/sort.html">http://redisdoc.com/key/sort.html</a>
     */
    public List<String> sort(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.sort(key);
        }
    }

    /**
     * <pre>
     * SORT key [BY pattern] [LIMIT offset count] [GET pattern [GET pattern ...]] [ASC | DESC] [ALPHA] [STORE
     * destination]
     *
     * 返回或保存给定列表、集合、有序集合 key 中经过排序的元素。
     *
     * 排序默认以数字作为对象，值被解释为双精度浮点数，然后进行比较。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/sort.html">http://redisdoc.com/key/sort.html</a>
     */
    public List<String> sort(String key, SortingParams sortingParameters) {
        try (Jedis jedis = getJedis()) {
            return jedis.sort(key, sortingParameters);
        }
    }

    /**
     * <pre>
     * SORT key [BY pattern] [LIMIT offset count] [GET pattern [GET pattern ...]] [ASC | DESC] [ALPHA] [STORE
     * destination]
     *
     * 返回或保存给定列表、集合、有序集合 key 中经过排序的元素。
     *
     * 排序默认以数字作为对象，值被解释为双精度浮点数，然后进行比较。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/sort.html">http://redisdoc.com/key/sort.html</a>
     */
    public Long sort(String key, SortingParams sortingParameters, String dstkey) {
        try (Jedis jedis = getJedis()) {
            return jedis.sort(key, sortingParameters, dstkey);
        }
    }

    /**
     * <pre>
     * SORT key [BY pattern] [LIMIT offset count] [GET pattern [GET pattern ...]] [ASC | DESC] [ALPHA] [STORE
     * destination]
     *
     * 返回或保存给定列表、集合、有序集合 key 中经过排序的元素。
     *
     * 排序默认以数字作为对象，值被解释为双精度浮点数，然后进行比较。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/sort.html">http://redisdoc.com/key/sort.html</a>
     */
    public Long sort(String key, String dstkey) {
        try (Jedis jedis = getJedis()) {
            return jedis.sort(key, dstkey);
        }
    }

    /**
     * <pre>
     * TYPE key
     *
     * 返回 key 所储存的值的类型。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * none (key不存在)
     * string (字符串)
     * list (列表)
     * set (集合)
     * zset (有序集)
     * hash (哈希表)
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/type.html">http://redisdoc.com/key/type.html</a>
     */
    public String type(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.type(key);
        }
    }

    /**
     * <pre>
     * SCAN cursor [MATCH pattern] [COUNT count]
     *
     * SCAN 命令及其相关的 SSCAN 命令、 HSCAN 命令和 ZSCAN 命令都用于增量地迭代（incrementally iterate）一集元素（a collection of elements）：
     *
     * SCAN 命令用于迭代当前数据库中的数据库键。
     * SSCAN 命令用于迭代集合键中的元素。
     * HSCAN 命令用于迭代哈希键中的键值对。
     * ZSCAN 命令用于迭代有序集合中的元素（包括元素成员和元素分值）。
     * 以上列出的四个命令都支持增量式迭代， 它们每次执行都只会返回少量元素， 所以这些命令可以用于生产环境， 而不会出现像 KEYS 命令、 SMEMBERS 命令带来的问题 —— 当 KEYS 命令被用于处理一个大的数据库时， 又或者 SMEMBERS 命令被用于处理一个大的集合键时， 它们可能会阻塞服务器达数秒之久。
     *
     * 不过， 增量式迭代命令也不是没有缺点的： 举个例子， 使用 SMEMBERS 命令可以返回集合键当前包含的所有元素， 但是对于 SCAN 这类增量式迭代命令来说， 因为在对键进行增量式迭代的过程中， 键可能会被修改， 所以增量式迭代命令只能对被返回的元素提供有限的保证 （offer limited guarantees about the returned elements）。
     *
     * 因为 SCAN 、 SSCAN 、 HSCAN 和 ZSCAN 四个命令的工作方式都非常相似， 所以这个文档会一并介绍这四个命令， 但是要记住：
     *
     * SSCAN 命令、 HSCAN 命令和 ZSCAN 命令的第一个参数总是一个数据库键。
     * 而 SCAN 命令则不需要在第一个参数提供任何数据库键 —— 因为它迭代的是当前数据库中的所有数据库键。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/scan.html">http://redisdoc.com/key/scan.html</a>
     */
    public ScanResult<String> scan(int cursor) {
        try (Jedis jedis = getJedis()) {
            return jedis.scan(String.valueOf(cursor));
        }
    }

    /**
     * <pre>
     * SCAN cursor [MATCH pattern] [COUNT count]
     *
     * SCAN 命令及其相关的 SSCAN 命令、 HSCAN 命令和 ZSCAN 命令都用于增量地迭代（incrementally iterate）一集元素（a collection of elements）：
     *
     * SCAN 命令用于迭代当前数据库中的数据库键。
     * SSCAN 命令用于迭代集合键中的元素。
     * HSCAN 命令用于迭代哈希键中的键值对。
     * ZSCAN 命令用于迭代有序集合中的元素（包括元素成员和元素分值）。
     * 以上列出的四个命令都支持增量式迭代， 它们每次执行都只会返回少量元素， 所以这些命令可以用于生产环境， 而不会出现像 KEYS 命令、 SMEMBERS 命令带来的问题 —— 当 KEYS 命令被用于处理一个大的数据库时， 又或者 SMEMBERS 命令被用于处理一个大的集合键时， 它们可能会阻塞服务器达数秒之久。
     *
     * 不过， 增量式迭代命令也不是没有缺点的： 举个例子， 使用 SMEMBERS 命令可以返回集合键当前包含的所有元素， 但是对于 SCAN 这类增量式迭代命令来说， 因为在对键进行增量式迭代的过程中， 键可能会被修改， 所以增量式迭代命令只能对被返回的元素提供有限的保证 （offer limited guarantees about the returned elements）。
     *
     * 因为 SCAN 、 SSCAN 、 HSCAN 和 ZSCAN 四个命令的工作方式都非常相似， 所以这个文档会一并介绍这四个命令， 但是要记住：
     *
     * SSCAN 命令、 HSCAN 命令和 ZSCAN 命令的第一个参数总是一个数据库键。
     * 而 SCAN 命令则不需要在第一个参数提供任何数据库键 —— 因为它迭代的是当前数据库中的所有数据库键。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/scan.html">http://redisdoc.com/key/scan.html</a>
     */
    public ScanResult<String> scan(String cursor) {
        try (Jedis jedis = getJedis()) {
            return jedis.scan(cursor);
        }
    }

    /**
     * <pre>
     * SCAN cursor [MATCH pattern] [COUNT count]
     *
     * SCAN 命令及其相关的 SSCAN 命令、 HSCAN 命令和 ZSCAN 命令都用于增量地迭代（incrementally iterate）一集元素（a collection of elements）：
     *
     * SCAN 命令用于迭代当前数据库中的数据库键。
     * SSCAN 命令用于迭代集合键中的元素。
     * HSCAN 命令用于迭代哈希键中的键值对。
     * ZSCAN 命令用于迭代有序集合中的元素（包括元素成员和元素分值）。
     * 以上列出的四个命令都支持增量式迭代， 它们每次执行都只会返回少量元素， 所以这些命令可以用于生产环境， 而不会出现像 KEYS 命令、 SMEMBERS 命令带来的问题 —— 当 KEYS 命令被用于处理一个大的数据库时， 又或者 SMEMBERS 命令被用于处理一个大的集合键时， 它们可能会阻塞服务器达数秒之久。
     *
     * 不过， 增量式迭代命令也不是没有缺点的： 举个例子， 使用 SMEMBERS 命令可以返回集合键当前包含的所有元素， 但是对于 SCAN 这类增量式迭代命令来说， 因为在对键进行增量式迭代的过程中， 键可能会被修改， 所以增量式迭代命令只能对被返回的元素提供有限的保证 （offer limited guarantees about the returned elements）。
     *
     * 因为 SCAN 、 SSCAN 、 HSCAN 和 ZSCAN 四个命令的工作方式都非常相似， 所以这个文档会一并介绍这四个命令， 但是要记住：
     *
     * SSCAN 命令、 HSCAN 命令和 ZSCAN 命令的第一个参数总是一个数据库键。
     * 而 SCAN 命令则不需要在第一个参数提供任何数据库键 —— 因为它迭代的是当前数据库中的所有数据库键。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/key/scan.html">http://redisdoc.com/key/scan.html</a>
     */
    public ScanResult<String> scan(String cursor, ScanParams params) {
        try (Jedis jedis = getJedis()) {
            return jedis.scan(cursor, params);
        }
    }
}
