package org.etnaframework.jedis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import com.google.common.base.Splitter;
import redis.clients.jedis.Jedis;

/**
 * 获取redis服务器的配置信息
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public class JedisOpsConfig extends JedisOps {

    protected JedisOpsConfig(JedisTemplate jedisTemplate) {
        super(jedisTemplate);
    }

    /**
     * <pre>
     * INFO [section]
     *
     * 以一种易于解释（parse）且易于阅读的格式，返回关于 Redis 服务器的各种信息和统计数值。
     *
     * 通过给定可选的参数 section ，可以让命令只返回某一部分的信息：
     *
     * server : 一般 Redis 服务器信息，包含以下域：
     *
     * redis_version : Redis 服务器版本
     * redis_git_sha1 : Git SHA1
     * redis_git_dirty : Git dirty flag
     * os : Redis 服务器的宿主操作系统
     * arch_bits : 架构（32 或 64 位）
     * multiplexing_api : Redis 所使用的事件处理机制
     * gcc_version : 编译 Redis 时所使用的 GCC 版本
     * process_id : 服务器进程的 PID
     * run_id : Redis 服务器的随机标识符（用于 Sentinel 和集群）
     * tcp_port : TCP/IP 监听端口
     * uptime_in_seconds : 自 Redis 服务器启动以来，经过的秒数
     * uptime_in_days : 自 Redis 服务器启动以来，经过的天数
     * lru_clock : 以分钟为单位进行自增的时钟，用于 LRU 管理
     * clients : 已连接客户端信息，包含以下域：
     *
     * connected_clients : 已连接客户端的数量（不包括通过从属服务器连接的客户端）
     * client_longest_output_list : 当前连接的客户端当中，最长的输出列表
     * client_longest_input_buf : 当前连接的客户端当中，最大输入缓存
     * blocked_clients : 正在等待阻塞命令（BLPOP、BRPOP、BRPOPLPUSH）的客户端的数量
     * memory : 内存信息，包含以下域：
     *
     * used_memory : 由 Redis 分配器分配的内存总量，以字节（byte）为单位
     * used_memory_human : 以人类可读的格式返回 Redis 分配的内存总量
     * used_memory_rss : 从操作系统的角度，返回 Redis 已分配的内存总量（俗称常驻集大小）。这个值和 top 、 ps 等命令的输出一致。
     * used_memory_peak : Redis 的内存消耗峰值（以字节为单位）
     * used_memory_peak_human : 以人类可读的格式返回 Redis 的内存消耗峰值
     * used_memory_lua : Lua 引擎所使用的内存大小（以字节为单位）
     * mem_fragmentation_ratio : used_memory_rss 和 used_memory 之间的比率
     * mem_allocator : 在编译时指定的， Redis 所使用的内存分配器。可以是 libc 、 jemalloc 或者 tcmalloc 。
     * 在理想情况下， used_memory_rss 的值应该只比 used_memory 稍微高一点儿。
     * 当 rss > used ，且两者的值相差较大时，表示存在（内部或外部的）内存碎片。
     * 内存碎片的比率可以通过 mem_fragmentation_ratio 的值看出。
     * 当 used > rss 时，表示 Redis 的部分内存被操作系统换出到交换空间了，在这种情况下，操作可能会产生明显的延迟。
     * Because Redis does not have control over how its allocations are mapped to memory pages, high used_memory_rss is
     * often the result of a spike in memory usage.
     *
     * 当 Redis 释放内存时，分配器可能会，也可能不会，将内存返还给操作系统。
     * 如果 Redis 释放了内存，却没有将内存返还给操作系统，那么 used_memory 的值可能和操作系统显示的 Redis 内存占用并不一致。
     * 查看 used_memory_peak 的值可以验证这种情况是否发生。
     * persistence : RDB 和 AOF 的相关信息
     *
     * stats : 一般统计信息
     *
     * replication : 主/从复制信息
     *
     * cpu : CPU 计算量统计信息
     *
     * commandstats : Redis 命令统计信息
     *
     * cluster : Redis 集群信息
     *
     * keyspace : 数据库相关的统计信息
     *
     * 除上面给出的这些值以外，参数还可以是下面这两个：
     *
     * all : 返回所有信息
     * default : 返回默认选择的信息
     * 当不带参数直接调用 INFO 命令时，使用 default 作为默认参数。
     *
     * 不同版本的 Redis 可能对返回的一些域进行了增加或删减。
     * 因此，一个健壮的客户端程序在对 INFO 命令的输出进行分析时，应该能够跳过不认识的域，并且妥善地处理丢失不见的域。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * 具体请参见下面的测试代码。
     *
     * @see <a href="http://redisdoc.com/server/info.html">http://redisdoc.com/server/info.html</a>
     * </pre>
     */
    public String info() {
        try (Jedis jedis = getJedis()) {
            return jedis.info();
        }
    }

    /**
     * <pre>
     * 以一种易于解释（parse）且易于阅读的格式，返回关于 Redis 服务器的各种信息和统计数值。
     * 通过给定可选的参数 section ，可以让命令只返回某一部分的信息：
     *
     * @see #info()
     * @see <a href="http://redisdoc.com/server/info.html">http://redisdoc.com/server/info.html</a>
     * </pre>
     */
    public String info(String section) {
        try (Jedis jedis = getJedis()) {
            return jedis.info(section);
        }
    }

    /**
     * info接口的内容以Map形式返回
     */
    public Map<String, String> infoMap() {
        String info = info();
        Map<String, String> map = new LinkedHashMap<String, String>();
        Iterable<String> lines = Splitter.on('\n')
                                         .trimResults()
                                         .split(info);
        for (String line : lines) {
            List<String> arr = Splitter.on(':')
                                       .trimResults()
                                       .limit(2)
                                       .splitToList(line);
            try {
                map.put(arr.get(0), arr.get(1));
            } catch (Exception ignore) {
            }
        }
        return map;
    }

    /**
     * <pre>
     * 返回当前redis服务器时间，精确到毫秒
     *
     * @see <a href="http://redisdoc.com/server/time.html">http://redisdoc.com/server/time.html</a>
     * </pre>
     */
    public Datetime time() {
        try (Jedis jedis = getJedis()) {
            // redis返回的是两个字符串
            // 第一个字符串是当前时间(以 UNIX 时间戳格式表示)
            // 第二个字符串是当前这一秒钟已经逝去的微秒数，由于精度问题只保留到毫秒
            List<String> list = jedis.time();

            long ts = Long.parseLong(list.get(0)) * 1000L + Long.parseLong(list.get(1)) / 1000L;
            return new Datetime(ts);
        }
    }

    /**
     * <pre>
     * ECHO message
     *
     * 打印一个特定的信息 message ，测试时使用。
     *
     * 可用版本：
     * >= 1.0.0
     * 时间复杂度：
     * O(1)
     * 返回值：
     * message 自身。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/connection/echo.html">http://redisdoc.com/connection/echo.html</a>
     */
    public String echo(String string) {
        try (Jedis jedis = getJedis()) {
            return jedis.echo(string);
        }
    }

    /**
     * <pre>
     * CONFIG GET parameter
     *
     * CONFIG GET 命令用于取得运行中的 Redis 服务器的配置参数(configuration parameters)，在 Redis 2.4 版本中， 有部分参数没有办法用 CONFIG GET 访问，但是在最新的
     * Redis 2.6 版本中，所有配置参数都已经可以用 CONFIG GET 访问了。
     *
     * CONFIG GET 接受单个参数 parameter 作为搜索关键字，查找所有匹配的配置参数，其中参数和值以“键-值对”(key-value pairs)的方式排列。
     *
     * 比如执行 CONFIG GET s* 命令，服务器就会返回所有以 s 开头的配置参数及参数的值：
     *
     * redis> CONFIG GET s*
     * 1) "save"                       # 参数名：save
     * 2) "900 1 300 10 60 10000"      # save 参数的值
     * 3) "slave-serve-stale-data"     # 参数名： slave-serve-stale-data
     * 4) "yes"                        # slave-serve-stale-data 参数的值
     * 5) "set-max-intset-entries"     # ...
     * 6) "512"
     * 7) "slowlog-log-slower-than"
     * 8) "1000"
     * 9) "slowlog-max-len"
     * 10) "1000"
     * 如果你只是寻找特定的某个参数的话，你当然也可以直接指定参数的名字：
     *
     * redis> CONFIG GET slowlog-max-len
     * 1) "slowlog-max-len"
     * 2) "1000"
     * 使用命令 CONFIG GET * ，可以列出 CONFIG GET 命令支持的所有参数：
     *
     * redis> CONFIG GET *
     * 1) "dir"
     * 2) "/var/lib/redis"
     * 3) "dbfilename"
     * 4) "dump.rdb"
     * 5) "requirepass"
     * 6) (nil)
     * 7) "masterauth"
     * 8) (nil)
     * 9) "maxmemory"
     * 10) "0"
     * 11) "maxmemory-policy"
     * 12) "volatile-lru"
     * 13) "maxmemory-samples"
     * 14) "3"
     * 15) "timeout"
     * 16) "0"
     * 17) "appendonly"
     * 18) "no"
     * # ...
     * 49) "loglevel"
     * 50) "verbose"
     * 所有被 CONFIG SET 所支持的配置参数都可以在配置文件 redis.conf 中找到，不过 CONFIG GET 和 CONFIG SET 使用的格式和 redis.conf 文件所使用的格式有以下两点不同：
     *
     * 10kb 、 2gb 这些在配置文件中所使用的储存单位缩写，不可以用在 CONFIG 命令中， CONFIG SET 的值只能通过数字值显式地设定。
     *
     * 像 CONFIG SET xxx 1k 这样的命令是错误的，正确的格式是 CONFIG SET xxx 1000 。
     * save 选项在 redis.conf 中是用多行文字储存的，但在 CONFIG GET 命令中，它只打印一行文字。
     *
     * 以下是 save 选项在 redis.conf 文件中的表示：
     *
     * save 900 1
     * save 300 10
     * save 60 10000
     *
     * 但是 CONFIG GET 命令的输出只有一行：
     *
     * redis> CONFIG GET save
     * 1) "save"
     * 2) "900 1 300 10 60 10000"
     *
     * 上面 save 参数的三个值表示：在 900 秒内最少有 1 个 key 被改动，或者 300 秒内最少有 10 个 key 被改动，又或者 60 秒内最少有 1000 个 key
     * 被改动，以上三个条件随便满足一个，就触发一次保存操作。
     * 可用版本：
     * >= 2.0.0
     * 时间复杂度：
     * 不明确
     * 返回值：
     * 给定配置参数的值。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/server/config_get.html">http://redisdoc.com/server/config_get.html</a>
     */
    public List<String> configGet(String pattern) {
        try (Jedis jedis = getJedis()) {
            return jedis.configGet(pattern);
        }
    }
}
