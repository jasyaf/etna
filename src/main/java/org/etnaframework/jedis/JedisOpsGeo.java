package org.etnaframework.jedis;

import java.util.List;
import java.util.Map;
import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.GeoRadiusResponse;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.geo.GeoRadiusParam;

/**
 * redis地理位置相关操作命令集合
 *
 * Created by yuanhaoliang on 2017-08-21.
 */
public class JedisOpsGeo extends JedisOps {

    protected JedisOpsGeo(JedisTemplate jedisTemplate) {
        super(jedisTemplate);
    }

    /**
     * <pre>
     *     GEOADD key longitude latitude member [longitude latitude member ...]
     *
     * 将给定的空间元素（纬度、精度、名字）添加到指定的键里面。 这些数据会以有序集合的形式被储存在键里面， 从而使得像 GEORADIUS 和 GEORADIUSBYMEMBER 这样的命令可以在之后通过位置查询取得这些元素。
     *
     * GEOADD 命令以标准的 x,y 格式接受参数， 所以用户必须先输入经度， 然后再输入纬度。 GEOADD 能够记录的坐标是有限的： 非常接近两极的区域是无法被索引的。 精确的坐标限制由 EPSG:900913 /
     * EPSG:3785 / OSGEO:41001 等坐标系统定义， 具体如下：
     *
     * 有效的经度介于 -180 度至 180 度之间。
     * 有效的纬度介于 -85.05112878 度至 85.05112878 度之间。
     * 当用户尝试输入一个超出范围的经度或者纬度时， GEOADD 命令将返回一个错误。
     *
     * 可用版本：
     * >= 3.2.0
     * 时间复杂度：
     * 每添加一个元素的复杂度为 O(log(N)) ， 其中 N 为键里面包含的位置元素数量。
     * 返回值：
     * 新添加到键里面的空间元素数量， 不包括那些已经存在但是被更新的元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/geo/geoadd.html">http://redisdoc.com/geo/geoadd.html</a>
     */
    public Long geoadd(String key, double longitude, double latitude, String member) {
        try (Jedis jedis = getJedis()) {
            return jedis.geoadd(key, longitude, latitude, member);
        }
    }

    /**
     * <pre>
     *     GEOADD key longitude latitude member [longitude latitude member ...]
     *
     * 将给定的空间元素（纬度、精度、名字）添加到指定的键里面。 这些数据会以有序集合的形式被储存在键里面， 从而使得像 GEORADIUS 和 GEORADIUSBYMEMBER 这样的命令可以在之后通过位置查询取得这些元素。
     *
     * GEOADD 命令以标准的 x,y 格式接受参数， 所以用户必须先输入经度， 然后再输入纬度。 GEOADD 能够记录的坐标是有限的： 非常接近两极的区域是无法被索引的。 精确的坐标限制由 EPSG:900913 /
     * EPSG:3785 / OSGEO:41001 等坐标系统定义， 具体如下：
     *
     * 有效的经度介于 -180 度至 180 度之间。
     * 有效的纬度介于 -85.05112878 度至 85.05112878 度之间。
     * 当用户尝试输入一个超出范围的经度或者纬度时， GEOADD 命令将返回一个错误。
     *
     * 可用版本：
     * >= 3.2.0
     * 时间复杂度：
     * 每添加一个元素的复杂度为 O(log(N)) ， 其中 N 为键里面包含的位置元素数量。
     * 返回值：
     * 新添加到键里面的空间元素数量， 不包括那些已经存在但是被更新的元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/geo/geoadd.html">http://redisdoc.com/geo/geoadd.html</a>
     */
    public Long geoadd(String key, Map<String, GeoCoordinate> memberCoordinateMap) {
        try (Jedis jedis = getJedis()) {
            return jedis.geoadd(key, memberCoordinateMap);
        }
    }

    /**
     * <pre>
     *     GEODIST key member1 member2 [unit]
     *
     * 返回两个给定位置之间的距离。
     *
     * 如果两个位置之间的其中一个不存在， 那么命令返回空值。
     *
     * 指定单位的参数 unit 必须是以下单位的其中一个：
     *
     * m 表示单位为米。
     * km 表示单位为千米。
     * mi 表示单位为英里。
     * ft 表示单位为英尺。
     * 如果用户没有显式地指定单位参数， 那么 GEODIST 默认使用米作为单位。
     *
     * GEODIST 命令在计算距离时会假设地球为完美的球形， 在极限情况下， 这一假设最大会造成 0.5% 的误差。
     *
     * 可用版本：
     * >= 3.2.0
     * 复杂度：
     * O(log(N))
     * 返回值：
     * 计算出的距离会以双精度浮点数的形式被返回。 如果给定的位置元素不存在， 那么命令返回空值。
     *
     * </pre>
     *
     * @see <a href="http://redisdoc.com/geo/geodist.html">http://redisdoc.com/geo/geodist.html</a>
     */
    public Double geodist(String key, String member1, String member2) {
        try (Jedis jedis = getJedis()) {
            return jedis.geodist(key, member1, member2);
        }
    }

    /**
     * <pre>
     *     GEODIST key member1 member2 [unit]
     *
     * 返回两个给定位置之间的距离。
     *
     * 如果两个位置之间的其中一个不存在， 那么命令返回空值。
     *
     * 指定单位的参数 unit 必须是以下单位的其中一个：
     *
     * m 表示单位为米。
     * km 表示单位为千米。
     * mi 表示单位为英里。
     * ft 表示单位为英尺。
     * 如果用户没有显式地指定单位参数， 那么 GEODIST 默认使用米作为单位。
     *
     * GEODIST 命令在计算距离时会假设地球为完美的球形， 在极限情况下， 这一假设最大会造成 0.5% 的误差。
     *
     * 可用版本：
     * >= 3.2.0
     * 复杂度：
     * O(log(N))
     * 返回值：
     * 计算出的距离会以双精度浮点数的形式被返回。 如果给定的位置元素不存在， 那么命令返回空值。
     *
     * </pre>
     *
     * @see <a href="http://redisdoc.com/geo/geodist.html">http://redisdoc.com/geo/geodist.html</a>
     */
    public Double geodist(String key, String member1, String member2, GeoUnit unit) {
        try (Jedis jedis = getJedis()) {
            return jedis.geodist(key, member1, member2, unit);
        }
    }

    /**
     * <pre>
     *     GEOHASH key member [member ...]
     *
     * 返回一个或多个位置元素的 Geohash 表示。
     *
     * 可用版本：
     * >= 3.2.0
     * 时间复杂度：
     * 寻找每个位置元素的复杂度为 O(log(N)) ， 其中 N 为给定键包含的位置元素数量。
     * 返回值：
     * 一个数组， 数组的每个项都是一个 geohash 。 命令返回的 geohash 的位置与用户给定的位置元素的位置一一对应。
     *
     * </pre>
     *
     * @see <a href="http://redisdoc.com/geo/geohash.html">http://redisdoc.com/geo/geohash.html</a>
     */
    public List<String> geohash(String key, String... members) {
        try (Jedis jedis = getJedis()) {
            return jedis.geohash(key, members);
        }
    }

    /**
     * <pre>
     *     GEOPOS key member [member ...]
     *
     * 从键里面返回所有给定位置元素的位置（经度和纬度）。
     *
     * 因为 GEOPOS 命令接受可变数量的位置元素作为输入， 所以即使用户只给定了一个位置元素， 命令也会返回数组回复。
     *
     * 可用版本：
     * >= 3.2.0
     * 时间复杂度：
     * 获取每个位置元素的复杂度为 O(log(N)) ， 其中 N 为键里面包含的位置元素数量。
     * 返回值：
     * GEOPOS 命令返回一个数组， 数组中的每个项都由两个元素组成： 第一个元素为给定位置元素的经度， 而第二个元素则为给定位置元素的纬度。
     * 当给定的位置元素不存在时， 对应的数组项为空值。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/geo/geopos.html">http://redisdoc.com/geo/geopos.html</a>
     */
    public List<GeoCoordinate> geopos(String key, String... members) {
        try (Jedis jedis = getJedis()) {
            return jedis.geopos(key, members);
        }
    }

    /**
     * <pre>
     *     GEORADIUS key longitude latitude radius m|km|ft|mi [WITHCOORD] [WITHDIST] [WITHHASH] [ASC|DESC] [COUNT
     * count]
     *
     * 以给定的经纬度为中心， 返回键包含的位置元素当中， 与中心的距离不超过给定最大距离的所有位置元素。
     *
     * 范围可以使用以下其中一个单位：
     *
     * m 表示单位为米。
     * km 表示单位为千米。
     * mi 表示单位为英里。
     * ft 表示单位为英尺。
     * 在给定以下可选项时， 命令会返回额外的信息：
     *
     * WITHDIST ： 在返回位置元素的同时， 将位置元素与中心之间的距离也一并返回。 距离的单位和用户给定的范围单位保持一致。
     * WITHCOORD ： 将位置元素的经度和维度也一并返回。
     * WITHHASH ： 以 52 位有符号整数的形式， 返回位置元素经过原始 geohash 编码的有序集合分值。 这个选项主要用于底层应用或者调试， 实际中的作用并不大。
     * 命令默认返回未排序的位置元素。 通过以下两个参数， 用户可以指定被返回位置元素的排序方式：
     *
     * ASC ： 根据中心的位置， 按照从近到远的方式返回位置元素。
     * DESC ： 根据中心的位置， 按照从远到近的方式返回位置元素。
     * 在默认情况下， GEORADIUS 命令会返回所有匹配的位置元素。 虽然用户可以使用 COUNT <count> 选项去获取前 N 个匹配元素， 但是因为命令在内部可能会需要对所有被匹配的元素进行处理，
     * 所以在对一个非常大的区域进行搜索时， 即使只使用 COUNT 选项去获取少量元素， 命令的执行速度也可能会非常慢。 但是从另一方面来说， 使用 COUNT
     * 选项去减少需要返回的元素数量， 对于减少带宽来说仍然是非常有用的。
     *
     * 可用版本：
     * >= 3.2.0
     * 时间复杂度：
     * O(log(N)+M)， 其中 N 为指定范围之内的元素数量， 而 M 则是被返回的元素数量。
     * 返回值：
     * GEORADIUS 命令返回一个数组， 具体来说：
     *
     * 在没有给定任何 WITH 选项的情况下， 命令只会返回一个像 ["New York","Milan","Paris"] 这样的线性（linear）列表。
     * 在指定了 WITHCOORD 、 WITHDIST 、 WITHHASH 等选项的情况下， 命令返回一个二层嵌套数组， 内层的每个子数组就表示一个元素。
     * 在返回嵌套数组时， 子数组的第一个元素总是位置元素的名字。 至于额外的信息， 则会作为子数组的后续元素， 按照以下顺序被返回：
     *
     * 以浮点数格式返回的中心与位置元素之间的距离， 单位与用户指定范围时的单位一致。
     * geohash 整数。
     * 由两个元素组成的坐标，分别为经度和纬度。
     * 举个例子， GEORADIUS Sicily 15 37 200 km withcoord withdist 这样的命令返回的每个子数组都是类似以下格式的：
     *
     * ["Palermo","190.4424",["13.361389338970184","38.115556395496299"]]
     *
     * </pre>
     *
     * @see <a href="http://redisdoc.com/geo/georadius.html">http://redisdoc.com/geo/georadius.html</a>
     */
    public List<GeoRadiusResponse> georadius(String key, double longitude, double latitude, double radius,
        GeoUnit unit) {
        try (Jedis jedis = getJedis()) {
            return jedis.georadius(key, longitude, latitude, radius, unit);
        }
    }

    /**
     * <pre>
     *     GEORADIUS key longitude latitude radius m|km|ft|mi [WITHCOORD] [WITHDIST] [WITHHASH] [ASC|DESC] [COUNT
     * count]
     *
     * 以给定的经纬度为中心， 返回键包含的位置元素当中， 与中心的距离不超过给定最大距离的所有位置元素。
     *
     * 范围可以使用以下其中一个单位：
     *
     * m 表示单位为米。
     * km 表示单位为千米。
     * mi 表示单位为英里。
     * ft 表示单位为英尺。
     * 在给定以下可选项时， 命令会返回额外的信息：
     *
     * WITHDIST ： 在返回位置元素的同时， 将位置元素与中心之间的距离也一并返回。 距离的单位和用户给定的范围单位保持一致。
     * WITHCOORD ： 将位置元素的经度和维度也一并返回。
     * WITHHASH ： 以 52 位有符号整数的形式， 返回位置元素经过原始 geohash 编码的有序集合分值。 这个选项主要用于底层应用或者调试， 实际中的作用并不大。
     * 命令默认返回未排序的位置元素。 通过以下两个参数， 用户可以指定被返回位置元素的排序方式：
     *
     * ASC ： 根据中心的位置， 按照从近到远的方式返回位置元素。
     * DESC ： 根据中心的位置， 按照从远到近的方式返回位置元素。
     * 在默认情况下， GEORADIUS 命令会返回所有匹配的位置元素。 虽然用户可以使用 COUNT <count> 选项去获取前 N 个匹配元素， 但是因为命令在内部可能会需要对所有被匹配的元素进行处理，
     * 所以在对一个非常大的区域进行搜索时， 即使只使用 COUNT 选项去获取少量元素， 命令的执行速度也可能会非常慢。 但是从另一方面来说， 使用 COUNT
     * 选项去减少需要返回的元素数量， 对于减少带宽来说仍然是非常有用的。
     *
     * 可用版本：
     * >= 3.2.0
     * 时间复杂度：
     * O(log(N)+M)， 其中 N 为指定范围之内的元素数量， 而 M 则是被返回的元素数量。
     * 返回值：
     * GEORADIUS 命令返回一个数组， 具体来说：
     *
     * 在没有给定任何 WITH 选项的情况下， 命令只会返回一个像 ["New York","Milan","Paris"] 这样的线性（linear）列表。
     * 在指定了 WITHCOORD 、 WITHDIST 、 WITHHASH 等选项的情况下， 命令返回一个二层嵌套数组， 内层的每个子数组就表示一个元素。
     * 在返回嵌套数组时， 子数组的第一个元素总是位置元素的名字。 至于额外的信息， 则会作为子数组的后续元素， 按照以下顺序被返回：
     *
     * 以浮点数格式返回的中心与位置元素之间的距离， 单位与用户指定范围时的单位一致。
     * geohash 整数。
     * 由两个元素组成的坐标，分别为经度和纬度。
     * 举个例子， GEORADIUS Sicily 15 37 200 km withcoord withdist 这样的命令返回的每个子数组都是类似以下格式的：
     *
     * ["Palermo","190.4424",["13.361389338970184","38.115556395496299"]]
     *
     * </pre>
     *
     * @see <a href="http://redisdoc.com/geo/georadius.html">http://redisdoc.com/geo/georadius.html</a>
     */
    public List<GeoRadiusResponse> georadius(String key, double longitude, double latitude, double radius, GeoUnit unit,
        GeoRadiusParam param) {
        try (Jedis jedis = getJedis()) {
            return jedis.georadius(key, longitude, latitude, radius, unit, param);
        }
    }

    /**
     * <pre>
     *     GEORADIUSBYMEMBER key member radius m|km|ft|mi [WITHCOORD] [WITHDIST] [WITHHASH] [ASC|DESC] [COUNT count]
     *
     * 这个命令和 GEORADIUS 命令一样， 都可以找出位于指定范围内的元素， 但是 GEORADIUSBYMEMBER 的中心点是由给定的位置元素决定的， 而不是像 GEORADIUS 那样，
     * 使用输入的经度和纬度来决定中心点。
     *
     * 关于 GEORADIUSBYMEMBER 命令的更多信息， 请参考 GEORADIUS 命令的文档。
     *
     * 可用版本：
     * >= 3.2.0
     * 时间复杂度：
     * O(log(N)+M)， 其中 N 为指定范围之内的元素数量， 而 M 则是被返回的元素数量。
     * 返回值：
     * 一个数组， 数组中的每个项表示一个范围之内的位置元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/geo/georadiusbymember.html">http://redisdoc.com/geo/georadiusbymember.html</a>
     */
    public List<GeoRadiusResponse> georadiusByMember(String key, String member, double radius, GeoUnit unit) {
        try (Jedis jedis = getJedis()) {
            return jedis.georadiusByMember(key, member, radius, unit);
        }
    }

    /**
     * <pre>
     *     GEORADIUSBYMEMBER key member radius m|km|ft|mi [WITHCOORD] [WITHDIST] [WITHHASH] [ASC|DESC] [COUNT count]
     *
     * 这个命令和 GEORADIUS 命令一样， 都可以找出位于指定范围内的元素， 但是 GEORADIUSBYMEMBER 的中心点是由给定的位置元素决定的， 而不是像 GEORADIUS 那样，
     * 使用输入的经度和纬度来决定中心点。
     *
     * 关于 GEORADIUSBYMEMBER 命令的更多信息， 请参考 GEORADIUS 命令的文档。
     *
     * 可用版本：
     * >= 3.2.0
     * 时间复杂度：
     * O(log(N)+M)， 其中 N 为指定范围之内的元素数量， 而 M 则是被返回的元素数量。
     * 返回值：
     * 一个数组， 数组中的每个项表示一个范围之内的位置元素。
     * </pre>
     *
     * @see <a href="http://redisdoc.com/geo/georadiusbymember.html">http://redisdoc.com/geo/georadiusbymember.html</a>
     */
    public List<GeoRadiusResponse> georadiusByMember(String key, String member, double radius, GeoUnit unit,
        GeoRadiusParam param) {
        try (Jedis jedis = getJedis()) {
            return jedis.georadiusByMember(key, member, radius, unit, param);
        }
    }
}
