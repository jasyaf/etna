package org.etnaframework.jedis;

import java.util.List;
import redis.clients.jedis.BinaryClient.LIST_POSITION;
import redis.clients.jedis.Jedis;

/**
 * redis列表操作集合
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public class JedisOpsList extends JedisOps {

    protected JedisOpsList(JedisTemplate jedisTemplate) {
        super(jedisTemplate);
    }

    /**
     * <pre>
     * BLPOP key [key ...] timeout
     *
     * BLPOP 是列表的阻塞式(blocking)弹出原语。
     *
     * 它是 LPOP 命令的阻塞版本，当给定列表内没有任何元素可供弹出的时候，连接将被 BLPOP 命令阻塞，直到等待超时或发现可弹出元素为止。
     *
     * 当给定多个 key 参数时，按参数 key 的先后顺序依次检查各个列表，弹出第一个非空列表的头元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/blpop.html">http://redisdoc.com/list/blpop.html</a>
     */
    public List<String> blpop(int timeout, String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.blpop(timeout, key);
        }
    }

    /**
     * <pre>
     * BLPOP key [key ...] timeout
     *
     * BLPOP 是列表的阻塞式(blocking)弹出原语。
     *
     * 它是 LPOP 命令的阻塞版本，当给定列表内没有任何元素可供弹出的时候，连接将被 BLPOP 命令阻塞，直到等待超时或发现可弹出元素为止。
     *
     * 当给定多个 key 参数时，按参数 key 的先后顺序依次检查各个列表，弹出第一个非空列表的头元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/blpop.html">http://redisdoc.com/list/blpop.html</a>
     */
    public List<String> blpop(int timeout, String... keys) {
        try (Jedis jedis = getJedis()) {
            return jedis.blpop(timeout, keys);
        }
    }

    /**
     * <pre>
     * BLPOP key [key ...] timeout
     *
     * BLPOP 是列表的阻塞式(blocking)弹出原语。
     *
     * 它是 LPOP 命令的阻塞版本，当给定列表内没有任何元素可供弹出的时候，连接将被 BLPOP 命令阻塞，直到等待超时或发现可弹出元素为止。
     *
     * 当给定多个 key 参数时，按参数 key 的先后顺序依次检查各个列表，弹出第一个非空列表的头元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/blpop.html">http://redisdoc.com/list/blpop.html</a>
     */
    @Deprecated
    public List<String> blpop(String arg) {
        try (Jedis jedis = getJedis()) {
            return jedis.blpop(arg);
        }
    }

    /**
     * <pre>
     * BLPOP key [key ...] timeout
     *
     * BLPOP 是列表的阻塞式(blocking)弹出原语。
     *
     * 它是 LPOP 命令的阻塞版本，当给定列表内没有任何元素可供弹出的时候，连接将被 BLPOP 命令阻塞，直到等待超时或发现可弹出元素为止。
     *
     * 当给定多个 key 参数时，按参数 key 的先后顺序依次检查各个列表，弹出第一个非空列表的头元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/blpop.html">http://redisdoc.com/list/blpop.html</a>
     */
    public List<String> blpop(String... args) {
        try (Jedis jedis = getJedis()) {
            return jedis.blpop(args);
        }
    }

    /**
     * <pre>
     * BRPOP key [key ...] timeout
     *
     * BRPOP 是列表的阻塞式(blocking)弹出原语。
     *
     * 它是 RPOP 命令的阻塞版本，当给定列表内没有任何元素可供弹出的时候，连接将被 BRPOP 命令阻塞，直到等待超时或发现可弹出元素为止。
     *
     * 当给定多个 key 参数时，按参数 key 的先后顺序依次检查各个列表，弹出第一个非空列表的尾部元素。
     *
     * 关于阻塞操作的更多信息，请查看 BLPOP 命令， BRPOP 除了弹出元素的位置和 BLPOP 不同之外，其他表现一致。
     *
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 假如在指定时间内没有任何元素被弹出，则返回一个 nil 和等待时长。
     * 反之，返回一个含有两个元素的列表，第一个元素是被弹出元素所属的 key ，第二个元素是被弹出元素的值。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/brpop.html">http://redisdoc.com/list/brpop.html</a>
     */
    @Deprecated
    public List<String> brpop(String arg) {
        try (Jedis jedis = getJedis()) {
            return jedis.brpop(arg);
        }
    }

    /**
     * <pre>
     * BRPOP key [key ...] timeout
     *
     * BRPOP 是列表的阻塞式(blocking)弹出原语。
     *
     * 它是 RPOP 命令的阻塞版本，当给定列表内没有任何元素可供弹出的时候，连接将被 BRPOP 命令阻塞，直到等待超时或发现可弹出元素为止。
     *
     * 当给定多个 key 参数时，按参数 key 的先后顺序依次检查各个列表，弹出第一个非空列表的尾部元素。
     *
     * 关于阻塞操作的更多信息，请查看 BLPOP 命令， BRPOP 除了弹出元素的位置和 BLPOP 不同之外，其他表现一致。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/brpop.html">http://redisdoc.com/list/brpop.html</a>
     */
    public List<String> brpop(int timeout, String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.brpop(timeout, key);
        }
    }

    /**
     * <pre>
     * BRPOP key [key ...] timeout
     *
     * BRPOP 是列表的阻塞式(blocking)弹出原语。
     *
     * 它是 RPOP 命令的阻塞版本，当给定列表内没有任何元素可供弹出的时候，连接将被 BRPOP 命令阻塞，直到等待超时或发现可弹出元素为止。
     *
     * 当给定多个 key 参数时，按参数 key 的先后顺序依次检查各个列表，弹出第一个非空列表的尾部元素。
     *
     * 关于阻塞操作的更多信息，请查看 BLPOP 命令， BRPOP 除了弹出元素的位置和 BLPOP 不同之外，其他表现一致。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/brpop.html">http://redisdoc.com/list/brpop.html</a>
     */
    public List<String> brpop(int timeout, String... keys) {
        try (Jedis jedis = getJedis()) {
            return jedis.brpop(timeout, keys);
        }
    }

    /**
     * <pre>
     * BRPOP key [key ...] timeout
     *
     * BRPOP 是列表的阻塞式(blocking)弹出原语。
     *
     * 它是 RPOP 命令的阻塞版本，当给定列表内没有任何元素可供弹出的时候，连接将被 BRPOP 命令阻塞，直到等待超时或发现可弹出元素为止。
     *
     * 当给定多个 key 参数时，按参数 key 的先后顺序依次检查各个列表，弹出第一个非空列表的尾部元素。
     *
     * 关于阻塞操作的更多信息，请查看 BLPOP 命令， BRPOP 除了弹出元素的位置和 BLPOP 不同之外，其他表现一致。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/brpop.html">http://redisdoc.com/list/brpop.html</a>
     */
    public List<String> brpop(String... args) {
        try (Jedis jedis = getJedis()) {
            return jedis.brpop(args);
        }
    }

    /**
     * <pre>
     *     BRPOPLPUSH source destination timeout
     *
     * BRPOPLPUSH 是 RPOPLPUSH 的阻塞版本，当给定列表 source 不为空时， BRPOPLPUSH 的表现和 RPOPLPUSH 一样。
     *
     * 当列表 source 为空时， BRPOPLPUSH 命令将阻塞连接，直到等待超时，或有另一个客户端对 source 执行 LPUSH 或 RPUSH 命令为止。
     *
     * 超时参数 timeout 接受一个以秒为单位的数字作为值。超时参数设为 0 表示阻塞时间可以无限期延长(block indefinitely) 。
     *
     * 更多相关信息，请参考 RPOPLPUSH 命令。
     *
     * 可用版本：
     * >= 2.2.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 假如在指定时间内没有任何元素被弹出，则返回一个 nil 和等待时长。
     * 反之，返回一个含有两个元素的列表，第一个元素是被弹出元素的值，第二个元素是等待时长。
     * </pre>
     */
    public String brpoplpush(String source, String destination, int timeout) {
        try (Jedis jedis = getJedis()) {
            return jedis.brpoplpush(source, destination, timeout);
        }
    }

    /**
     * <pre>
     * LINDEX key index
     *
     * 返回列表 key 中，下标为 index 的元素。
     *
     * 下标(index)参数 start 和 stop 都以 0 为底，也就是说，以 0 表示列表的第一个元素，以 1 表示列表的第二个元素，以此类推。
     *
     * 你也可以使用负数下标，以 -1 表示列表的最后一个元素， -2 表示列表的倒数第二个元素，以此类推。
     *
     * 如果 key 不是列表类型，返回一个错误。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(N)， N 为到达下标 index 过程中经过的元素数量。
     * 因此，对列表的头元素和尾元素执行 LINDEX 命令，复杂度为O(1)。
     * 返回值:
     * 列表中下标为 index 的元素。
     * 如果 index 参数的值不在列表的区间范围内(out of range)，返回 nil 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/lindex.html">http://redisdoc.com/list/lindex.html</a>
     */
    public String lindex(String key, long index) {
        try (Jedis jedis = getJedis()) {
            return jedis.lindex(key, index);
        }
    }

    /**
     * <pre>
     * LINSERT key BEFORE|AFTER pivot value
     *
     * 将值 value 插入到列表 key 当中，位于值 pivot 之前或之后。
     *
     * 当 pivot 不存在于列表 key 时，不执行任何操作。
     *
     * 当 key 不存在时， key 被视为空列表，不执行任何操作。
     *
     * 如果 key 不是列表类型，返回一个错误。
     *
     * 可用版本：
     * >= 2.2.0
     * 时间复杂度:
     * O(N)， N 为寻找 pivot 过程中经过的元素数量。
     * 返回值:
     * 如果命令执行成功，返回插入操作完成之后，列表的长度。
     * 如果没有找到 pivot ，返回 -1 。
     * 如果 key 不存在或为空列表，返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/linsert.html">http://redisdoc.com/list/linsert.html</a>
     */
    public Long linsert(String key, LIST_POSITION where, String pivot, String value) {
        try (Jedis jedis = getJedis()) {
            return jedis.linsert(key, where, pivot, value);
        }
    }

    /**
     * <pre>
     * LLEN key
     *
     * 返回列表 key 的长度。
     *
     * 如果 key 不存在，则 key 被解释为一个空列表，返回 0 .
     *
     * 如果 key 不是列表类型，返回一个错误。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 列表 key 的长度。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/llen.html">http://redisdoc.com/list/llen.html</a>
     */
    public Long llen(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.llen(key);
        }
    }

    /**
     * <pre>
     * LPOP key
     *
     * 移除并返回列表 key 的头元素。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 列表的头元素。
     * 当 key 不存在时，返回 nil 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/lpop.html">http://redisdoc.com/list/lpop.html</a>
     */
    public String lpop(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.lpop(key);
        }
    }

    /**
     * <pre>
     * LPUSH key value [value ...]
     *
     * 将一个或多个值 value 插入到列表 key 的表头
     *
     * 如果有多个 value 值，那么各个 value 值按从左到右的顺序依次插入到表头： 比如说，对空列表 mylist 执行命令 LPUSH mylist a b c ，列表的值将是 c b a ，这等同于原子性地执行
     * LPUSH mylist a 、 LPUSH mylist b 和 LPUSH mylist c 三个命令。
     *
     * 如果 key 不存在，一个空列表会被创建并执行 LPUSH 操作。
     *
     * 当 key 存在但不是列表类型时，返回一个错误。
     *
     * 在Redis 2.4版本以前的 LPUSH 命令，都只接受单个 value 值。
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 执行 LPUSH 命令后，列表的长度。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/lpush.html">http://redisdoc.com/list/lpush.html</a>
     */
    public Long lpush(String key, String... string) {
        try (Jedis jedis = getJedis()) {
            return jedis.lpush(key, string);
        }
    }

    /**
     * <pre>
     * LPUSHX key value
     *
     * 将值 value 插入到列表 key 的表头，当且仅当 key 存在并且是一个列表。
     *
     * 和 LPUSH 命令相反，当 key 不存在时， LPUSHX 命令什么也不做。
     *
     * 可用版本：
     * >= 2.2.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * LPUSHX 命令执行之后，表的长度。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/lpushx.html">http://redisdoc.com/list/lpushx.html</a>
     */
    public Long lpushx(String key, String... string) {
        try (Jedis jedis = getJedis()) {
            return jedis.lpushx(key, string);
        }
    }

    /**
     * <pre>
     * LRANGE key start stop
     *
     * 返回列表 key 中指定区间内的元素，区间以偏移量 start 和 stop 指定。
     *
     * 下标(index)参数 start 和 stop 都以 0 为底，也就是说，以 0 表示列表的第一个元素，以 1 表示列表的第二个元素，以此类推。
     *
     * 你也可以使用负数下标，以 -1 表示列表的最后一个元素， -2 表示列表的倒数第二个元素，以此类推。
     *
     * 注意LRANGE命令和编程语言区间函数的区别
     *
     * 假如你有一个包含一百个元素的列表，对该列表执行 LRANGE list 0 10 ，结果是一个包含11个元素的列表，这表明 stop 下标也在 LRANGE
     * 命令的取值范围之内(闭区间)，这和某些语言的区间函数可能不一致，比如Ruby的 Range.new 、 Array#slice 和Python的 range() 函数。
     *
     * 超出范围的下标
     *
     * 超出范围的下标值不会引起错误。
     *
     * 如果 start 下标比列表的最大下标 end ( LLEN list 减去 1 )还要大，那么 LRANGE 返回一个空列表。
     *
     * 如果 stop 下标比 end 下标还要大，Redis将 stop 的值设置为 end 。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(S+N)， S 为偏移量 start ， N 为指定区间内元素的数量。
     * 返回值:
     * 一个列表，包含指定区间内的元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/lrange.html">http://redisdoc.com/list/lrange.html</a>
     */
    public List<String> lrange(String key, long start, long end) {
        try (Jedis jedis = getJedis()) {
            return jedis.lrange(key, start, end);
        }
    }

    /**
     * <pre>
     * LREM key count value
     *
     * 根据参数 count 的值，移除列表中与参数 value 相等的元素。
     *
     * count 的值可以是以下几种：
     *
     * count > 0 : 从表头开始向表尾搜索，移除与 value 相等的元素，数量为 count 。
     * count < 0 : 从表尾开始向表头搜索，移除与 value 相等的元素，数量为 count 的绝对值。
     * count = 0 : 移除表中所有与 value 相等的值。
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(N)， N 为列表的长度。
     * 返回值：
     * 被移除元素的数量。
     * 因为不存在的 key 被视作空表(empty list)，所以当 key 不存在时， LREM 命令总是返回 0 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/lrem.html">http://redisdoc.com/list/lrem.html</a>
     */
    public Long lrem(String key, long count, String value) {
        try (Jedis jedis = getJedis()) {
            return jedis.lrem(key, count, value);
        }
    }

    /**
     * <pre>
     * LSET key index value
     *
     * 将列表 key 下标为 index 的元素的值设置为 value 。
     *
     * 当 index 参数超出范围，或对一个空列表( key 不存在)进行 LSET 时，返回一个错误。
     *
     * 关于列表下标的更多信息，请参考 LINDEX 命令。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * 对头元素或尾元素进行 LSET 操作，复杂度为 O(1)。
     * 其他情况下，为 O(N)， N 为列表的长度。
     * 返回值：
     * 操作成功返回 ok ，否则返回错误信息。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/lset.html">http://redisdoc.com/list/lset.html</a>
     */
    public String lset(String key, long index, String value) {
        try (Jedis jedis = getJedis()) {
            return jedis.lset(key, index, value);
        }
    }

    /**
     * <pre>
     * LTRIM key start stop
     *
     * 对一个列表进行修剪(trim)，就是说，让列表只保留指定区间内的元素，不在指定区间之内的元素都将被删除。
     *
     * 举个例子，执行命令 LTRIM list 0 2 ，表示只保留列表 list 的前三个元素，其余元素全部删除。
     *
     * 下标(index)参数 start 和 stop 都以 0 为底，也就是说，以 0 表示列表的第一个元素，以 1 表示列表的第二个元素，以此类推。
     *
     * 你也可以使用负数下标，以 -1 表示列表的最后一个元素， -2 表示列表的倒数第二个元素，以此类推。
     *
     * 当 key 不是列表类型时，返回一个错误。
     *
     * LTRIM 命令通常和 LPUSH 命令或 RPUSH 命令配合使用，举个例子：
     *
     * LPUSH log newest_log
     * LTRIM log 0 99
     * 这个例子模拟了一个日志程序，每次将最新日志 newest_log 放到 log 列表中，并且只保留最新的 100 项。注意当这样使用 LTRIM 命令时，时间复杂度是O(1)，因为平均情况下，每次只有一个元素被移除。
     *
     * 注意LTRIM命令和编程语言区间函数的区别
     *
     * 假如你有一个包含一百个元素的列表 list ，对该列表执行 LTRIM list 0 10 ，结果是一个包含11个元素的列表，这表明 stop 下标也在 LTRIM
     * 命令的取值范围之内(闭区间)，这和某些语言的区间函数可能不一致，比如Ruby的 Range.new 、 Array#slice 和Python的 range() 函数。
     *
     * 超出范围的下标
     *
     * 超出范围的下标值不会引起错误。
     *
     * 如果 start 下标比列表的最大下标 end ( LLEN list 减去 1 )还要大，或者 start > stop ， LTRIM 返回一个空列表(因为 LTRIM 已经将整个列表清空)。
     *
     * 如果 stop 下标比 end 下标还要大，Redis将 stop 的值设置为 end 。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度:
     * O(N)， N 为被移除的元素的数量。
     * 返回值:
     * 命令执行成功时，返回 ok 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/ltrim.html">http://redisdoc.com/list/ltrim.html</a>
     */
    public String ltrim(String key, long start, long end) {
        try (Jedis jedis = getJedis()) {
            return jedis.ltrim(key, start, end);
        }
    }

    /**
     * <pre>
     * RPOP key
     *
     * 移除并返回列表 key 的尾元素。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 列表的尾元素。
     * 当 key 不存在时，返回 nil 。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/rpop.html">http://redisdoc.com/list/rpop.html</a>
     */
    public String rpop(String key) {
        try (Jedis jedis = getJedis()) {
            return jedis.rpop(key);
        }
    }

    /**
     * <pre>
     *     RPOPLPUSH source destination
     *
     * 命令 RPOPLPUSH 在一个原子时间内，执行以下两个动作：
     *
     * 将列表 source 中的最后一个元素(尾元素)弹出，并返回给客户端。
     * 将 source 弹出的元素插入到列表 destination ，作为 destination 列表的的头元素。
     * 举个例子，你有两个列表 source 和 destination ， source 列表有元素 a, b, c ， destination 列表有元素 x, y, z ，执行 RPOPLPUSH source
     * destination 之后， source 列表包含元素 a, b ， destination 列表包含元素 c, x, y, z ，并且元素 c 会被返回给客户端。
     *
     * 如果 source 不存在，值 nil 被返回，并且不执行其他动作。
     *
     * 如果 source 和 destination 相同，则列表中的表尾元素被移动到表头，并返回该元素，可以把这种特殊情况视作列表的旋转(rotation)操作。
     *
     * 可用版本：
     * >= 1.2.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 被弹出的元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/rpoplpush.html">http://redisdoc.com/list/rpoplpush.html</a>
     */
    public String rpoplpush(String srckey, String dstkey) {
        try (Jedis jedis = getJedis()) {
            return jedis.rpoplpush(srckey, dstkey);
        }
    }

    /**
     * <pre>
     * RPUSH key value [value ...]
     *
     * 将一个或多个值 value 插入到列表 key 的表尾(最右边)。
     *
     * 如果有多个 value 值，那么各个 value 值按从左到右的顺序依次插入到表尾：比如对一个空列表 mylist 执行 RPUSH mylist a b c ，得出的结果列表为 a b c ，等同于执行命令 RPUSH
     * mylist a 、 RPUSH mylist b 、 RPUSH mylist c 。
     *
     * 如果 key 不存在，一个空列表会被创建并执行 RPUSH 操作。
     *
     * 当 key 存在但不是列表类型时，返回一个错误。
     *
     * 在 Redis 2.4 版本以前的 RPUSH 命令，都只接受单个 value 值。
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 执行 RPUSH 操作后，表的长度。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/rpush.html">http://redisdoc.com/list/rpush.html</a>
     */
    public Long rpush(String key, String... string) {
        try (Jedis jedis = getJedis()) {
            return jedis.rpush(key, string);
        }
    }

    /**
     * <pre>
     * RPUSHX key value
     *
     * 将值 value 插入到列表 key 的表尾，当且仅当 key 存在并且是一个列表。
     *
     * 和 RPUSH 命令相反，当 key 不存在时， RPUSHX 命令什么也不做。
     *
     * 可用版本：
     * >= 2.2.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * RPUSHX 命令执行之后，表的长度。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/list/rpushx.html">http://redisdoc.com/list/rpushx.html</a>
     */
    public Long rpushx(String key, String... string) {
        try (Jedis jedis = getJedis()) {
            return jedis.rpushx(key, string);
        }
    }
}
