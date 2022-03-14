package org.etnaframework.plugin.cache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import org.etnaframework.plugin.cache.CacheKeyMaker;
import org.etnaframework.plugin.cache.DefaultCacheKeyMaker;
import org.etnaframework.plugin.cache.LocalCacheAspect;
import org.etnaframework.core.util.SystemInfo.RunEnv;

/**
 * 标记在方法上，表示方法的返回值将使用JVM本地缓存，适用于读较多的场景（访问/stat/cache可查看统计数据）
 *
 * 内部有时间对齐机制，多机部署时同一个key确保同时过期，减少了缓存内容不一致的情况
 *
 * 为了增加执行效率，针对没参数的方法，如果运行环境是{@link RunEnv#dev}则执行【懒加载策略】，可以加快本地调试的启动速度
 * 而在服务器上部署时，运行环境不是{@link RunEnv#dev}则执行【定时加载策略】，即以{@link LocalCache#expire()}为周期执行更新操作
 * 此时访问缓存不会等待最新的结果，而是直接返回最近一次定时任务执行的结果，这样可减少懒加载方式缓存过期时重新加载的停顿时间
 *
 * @author BlackCat
 * @since 2018-03-05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface LocalCache {

    /**
     * 缓存key的生成规则，默认是把所有的参数值拼起来，如果需要定制哪些参数参与/不参与就需要自行编写{@link CacheKeyMaker}
     */
    Class<? extends CacheKeyMaker> keyMaker() default DefaultCacheKeyMaker.class;

    /**
     * 最多缓存多久，必须>0，支持的最小单位是1s，由于存在多机部署的情况，清除本机缓存并不能影响到其他的实例，故本地缓存不提供主动清理机制
     * 由于有【时间对齐机制】，实际缓存的时间会小于此值
     *
     * 例如最多缓存1min，加注解的方法名加传参计算得出的过期时间是【每分钟的第2s】
     * 如果当前时间是20:30:50，则过期时间是20:31:02，实际缓存有效时间12s
     * 如果当前时间是20:31:00，则过期时间是20:31:02，实际缓存有效时间2s
     * 如果当前时间是20:30:03，则过期时间是20:31:02，实际缓存有效时间60s
     * 以此类推，多机部署时，只要确保机器时间同步，则每个实例上这个key=A的过期时间都是一样的，这样能尽量避免访问到不同的实例缓存内容不同的情况
     *
     * 注意：仍不可能完全避免实例之间的不一致情况，例如有AB两个实例，过期周期为1min，key=X的过期时间点为每分钟的0秒
     * 21:30:15时访问到A，缓存了结果，预定到21:31:00过期，但此时更改了数据，又访问到B缓存了最新的结果，则到21:31:00之前，A和B的内容不一致
     */
    int expire();

    /**
     * 过期时间的单位
     */
    TimeUnit timeUnit();

    /**
     * 最多缓存key个数
     */
    int maxSize() default LocalCacheAspect.DEFAULT_MAX_CACHE_SIZE;

    /**
     * 如果方法的返回值是null，是否要缓存下来，默认缓存null，请根据需要自行调整
     * 如果是无参数的方法，在部署到生产环境时会采用定时全量加载的模式，如配置值为false而方法实际的返回值为null就会报错
     */
    boolean cacheNull() default true;

    /**
     * 该缓存方法的描述，会在/stat/cache显示出来
     */
    String descr() default "";
}
