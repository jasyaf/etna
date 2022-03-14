package org.etnaframework.plugin.cache;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.IgnoredPackages;
import org.etnaframework.core.spring.SpringContext;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.HumanReadableUtils;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.util.SystemInfo.RunEnv;
import org.etnaframework.jedis.BaseJedisLock;
import org.etnaframework.plugin.cache.annotation.LocalCache;
import org.etnaframework.plugin.cron.CronTaskMeta;
import org.etnaframework.plugin.cron.CronTaskProcessor;
import org.slf4j.Logger;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;

/**
 * {@link LocalCache}注解的处理业务，注意为适应多机部署时的数据统一，采取了相同key过期时间对齐的机制
 *
 * 为了增加执行效率，针对没参数的方法，如果运行环境是{@link RunEnv#dev}则执行【懒加载策略】，可以加快本地调试的启动速度
 * 而在服务器上部署时，运行环境不是{@link RunEnv#dev}则执行【定时加载策略】，即以{@link LocalCache#expire()}为周期执行更新操作
 * 此时访问缓存不会等待最新的结果，而是直接返回最近一次定时任务执行的结果，这样可减少懒加载方式缓存过期时重新加载的停顿时间
 *
 * @author BlackCat
 * @author Anur
 * @since 2018-03-05
 */
@Aspect
@Component
@Order(-1) // 在有事务存在时，需要优先执行不然会被事务吞掉
public final class LocalCacheAspect {

    /** 单个方法上默认缓存元素个数限制 */
    public static final int DEFAULT_MAX_CACHE_SIZE = 1024;

    private static final Interner<String> internerPool = Interners.newWeakInterner();

    private static final Logger log = Log.getLogger();

    private final static Map<Method, CacheContainer> data = Collections.synchronizedMap(new LinkedHashMap<>());

    /** 用于检测递归调用，这在定时加载型缓存初始化时会造成死锁 */
    private final static Set<Method> processing = new ConcurrentHashSet<>();

    @Autowired
    private CacheKeyMakerManager cacheKeyMakerManager;

    @Autowired
    private CronTaskProcessor cronTaskProcessor;

    public Map<Method, CacheContainer> getData() {
        return data;
    }

    /**
     * 列举所有的Spring托管bean，将所有标注了{@link LocalCache}的方法列举出来，初始化缓存注解
     */
    @OnContextInited
    protected void init() throws Throwable {

        for (Object bean : SpringContext.getBeansOfTypeAsList(Object.class)) {
            if (IgnoredPackages.filter(bean)) {
                continue;
            }
            if (bean instanceof Advised) { // Spring生成的类包含很多非业务方法，直接找回本来的类减少扫描次数
                bean = ((Advised) bean).getTargetSource()
                                       .getTarget();
                if (null == bean) {
                    continue;
                }
            }
            for (Method method : ReflectionTools.getAllMethodsInSourceCodeOrder(bean.getClass())) {
                LocalCache anno = method.getAnnotation(LocalCache.class);
                if (null != anno) {
                    this._getOrInitLocalCache(bean, method, anno);
                }
            }
        }
    }

    private CacheContainer _getOrInitLocalCache(Object bean, Method method, LocalCache anno) throws Throwable {
        // 在启动时本身就有初始化动作，为什么这里还要处理懒加载的情况？
        // 因为如果启动过程中，业务代码本身就有用到缓存的话，有可能会在init初始化完成之前就调用了
        // 此时cache还没初始化的话就会抛空指针启动失败，故这里还需要再判断并初始化一下
        CacheContainer cacheContainer = data.get(method);
        if (null == cacheContainer) {
            synchronized (data) { // 如果没查到，就生成，进行双次判断，防止重复生成
                if (null == (cacheContainer = data.get(method))) {
                    String location = method.getDeclaringClass()
                                            .getSimpleName() + "." + method.getName();

                    // 检测递归调用
                    // 例如有方法A.a，执行时调用了B.b，但B.b内部又反过来调用了A.a
                    // 造成调用A.a时又调用了自身，这样A.a在【初始化处理的过程中】又会进来初始化自己
                    // 反复初始化结果就是死锁，故此处需要做检测抛错
                    if (processing.contains(method)) {
                        throw new IllegalStateException(
                            StringTools.concatln(new Object[] {
                                location + "上的@" + LocalCache.class.getSimpleName() + "方法有递归调用问题",
                                "本次调用路径为 " + StringTools.printTrace(),
                                "即" + location + "的代码通过以上路径调用到了自己，造成缓存初始化死锁，请检查代码去除递归调用"
                            }));
                    }
                    processing.add(method);

                    // 方法返回值的void的没有缓存意义，故禁止
                    if (Void.class.equals(method.getReturnType())) {
                        throw new IllegalArgumentException(location + "上的@" + LocalCache.class.getSimpleName() + "方法返回值不能为void");
                    }
                    // private方法在生成继承类时不会被覆盖，会导致无缓存效果，故禁止
                    if (Modifier.isPrivate(method.getModifiers())) {
                        throw new IllegalArgumentException(location + "上的@" + LocalCache.class.getSimpleName() + "方法不能为private");
                    }
                    // static方法可以直接调用而绕过spring托管，会导致无缓存效果，故禁止
                    if (Modifier.isStatic(method.getModifiers())) {
                        throw new IllegalArgumentException(location + "上的@" + LocalCache.class.getSimpleName() + "方法不能为static");
                    }
                    long expireMs = anno.timeUnit()
                                        .toMillis(anno.expire());
                    if (expireMs <= 0) {
                        throw new IllegalArgumentException(location + "上的@" + LocalCache.class.getSimpleName() + "注解expire值必须>0");
                    }
                    // 定时任务组件的最小执行间隔是1s，所以需要限制最小expire
                    if (expireMs < Datetime.MILLIS_PER_SECOND) {
                        throw new IllegalArgumentException(location + "上的@" + LocalCache.class.getSimpleName() + "注解expire时间最小是1s");
                    }

                    // 针对【无参数的方法】，使用不同的缓存策略
                    // 在服务器部署环境使用【定时加载策略】，请求时直接返回缓存的结果，定时执行刷新任务（无阻塞）
                    // 在本机开发调试环境使用【懒加载策略】，请求时判断缓存是否过期，如果过期就刷新，然后返回刷新后的结果（有阻塞）
                    if (method.getParameterCount() == 0 && SystemInfo.RUN_ENV != RunEnv.dev) {
                        cacheContainer = new TimerLoadCacheContainer(bean, method);
                    } else {
                        cacheContainer = new LazyCacheContainer(bean, method);
                    }
                    data.put(method, cacheContainer);
                }
            }
        }
        return cacheContainer;
    }

    @Around("@annotation(anno)")
    public Object advice(ProceedingJoinPoint jp, LocalCache anno) throws Throwable {

        // 提取对应的缓存策略实现，获取返回值
        CacheContainer cacheContainer = _getOrInitLocalCache(
            jp.getTarget(),
            ((MethodSignature) jp.getSignature()).getMethod(),
            anno
        );

        return cacheContainer.get(jp.getArgs());
    }

    static class CachedElement {

        Object val;

        long expire;

        CachedElement(Object val, long expire) {
            this.val = val;
            this.expire = expire;
        }

        @Override
        public String toString() {
            return "[" + DatetimeUtils.format(expire) + "]";
        }
    }

    /**
     * cache容器基类，提供公共的基础功能，由具体的策略来实现值的完整获取能力
     */
    public abstract class CacheContainer {

        protected Object bean;

        protected Method method;

        protected String location;

        protected LocalCache anno;

        /** 缓存的有效周期，单位毫秒 */
        protected long periodMs;

        CacheContainer(Object bean, Method method) {
            this.bean = bean;
            this.method = method;
            this.method.setAccessible(true);
            if (bean instanceof Advised) { // 如果是经过aspect代理的对象，则需要取到被代理的（真实的）对象，否则实际执行时又会走AOP导致死循环
                try {
                    this.bean = ((Advised) bean).getTargetSource()// 代理对象需要获取其真实对象
                                                .getTarget();
                } catch (Exception ignore) {
                }
            }
            this.location = method.getDeclaringClass()
                                  .getSimpleName() + "." + method.getName();
            this.anno = method.getAnnotation(LocalCache.class);
            this.periodMs = anno.timeUnit()
                                .toMillis(anno.expire());
        }

        /**
         * 根据方法参数值计算缓存key
         */
        String calcKey(Object[] methodArgs) {
            return cacheKeyMakerManager.get(anno.keyMaker())
                                       .generate(methodArgs);
        }

        /**
         * 执行原始方法，获得返回值
         */
        Object invoke(Object... args) throws Throwable {
            try {
                return this.method.invoke(this.bean, args); // 注意这里必须取真实对象this.bean，否则直接调用会AOP死循环
            } catch (InvocationTargetException ex) {
                // 如果是执行方法内容时抛出异常，需要【提取到原始的异常】然后再抛出，这样才能在后续的异常流程中得到处理
                if (null != ex.getCause()) {
                    throw ex.getCause();
                }
                throw ex;
            }
        }

        /**
         * 根据传入的字符串计算过期时间点
         *
         * 为了防止集中一个固定的时间点过期对后端服务压力太大，这里是根据key来计算不同的过期时间点，寻找【距当前时间最近的未来过期时间点】
         * 例如有效周期为1min，则key=A计算得出的过期时间点是每分钟的第2s，key=B计算得出是每分钟第7s过期，如此把key错开
         * 对齐时间，是以毫秒0为开始时间点，计算当前时间下一次执行的时间点，每个key算出的时间点是固定的，即相同的key同时过期，多机部署时能保持一致
         */
        long calcExpire(String str) {
            int hash = Math.abs((StringTools.md5AsHex(str)).hashCode());
            long periodMs = anno.timeUnit()
                                .toMillis(anno.expire());
            long now = System.currentTimeMillis();
            double offsetPercent = (hash % 997) / 997f; // 偏移整点的百分比，范围0-1之间，为了尽量分散用质数来除
            long lastPeriod = now - (now % periodMs); // 上一个整点周期时间点
            long expire = lastPeriod + (long) (periodMs * (1 + offsetPercent)); // 下一个过期时间 = 上一个整点时间 + 周期 * (1 + 偏移百分比)
            if (expire - now > periodMs) {
                expire -= periodMs;
            }
            return expire;
        }

        /**
         * 根据提供的方法参数，调用方法/读取缓存 获取对应的方法返回值
         */
        abstract Object get(Object[] methodArgs) throws Throwable;

        public String getLocation() {
            return location;
        }

        public LocalCache getAnno() {
            return anno;
        }
    }

    /**
     * 懒加载的Cache容器，内部用guava实现
     */
    public class LazyCacheContainer extends CacheContainer {

        protected Cache<String, CachedElement> cache;

        public LazyCacheContainer(Object bean, Method method) {
            super(bean, method);
            this.cache = CacheBuilder.newBuilder()
                                     .maximumSize(anno.maxSize())
                                     .recordStats()
                                     .expireAfterWrite(periodMs, TimeUnit.MILLISECONDS)
                                     .build();
        }

        @Override
        public Object get(Object[] methodArgs) throws Throwable {
            long start = System.currentTimeMillis();

            String key = calcKey(methodArgs);

            String status = "ReadFromCache";
            long expire = 0;

            try {
                String lock = method + key;
                // 由于采用了同一个key对齐过期时间的机制，故实际的缓存有效期一般是会小于配置的公共expire的
                // 故这里不能直接使用guava的过期时间，需要针对每个key来定制过期时间
                CachedElement e = cache.getIfPresent(key);
                if (null == e || System.currentTimeMillis() > e.expire) {
                    synchronized (internerPool.intern(lock)) { // 防止缓存过期时加载方法被重复执行
                        e = cache.getIfPresent(key);
                        if (null == e || System.currentTimeMillis() > e.expire) {
                            status = "EncounterError"; // 防止下面调用原方法出错，预备写到日志

                            // 调用原方法获得返回值
                            Object result = invoke(methodArgs);

                            // 特殊处理：如果设定不保存null，返回值就不加入到缓存中去
                            if (null == result && !anno.cacheNull()) {
                                status = "NullIgnoredByCache";
                                return null;
                            }

                            // 根据lock计算过期时间点，确保多机部署时同一个key能在同一个时间点过期
                            e = new CachedElement(result, calcExpire(lock));
                            cache.put(key, e);
                            status = "WriteToCache";
                        }
                    }
                }
                expire = e.expire;
                return e.val;
            } finally {
                log.debug("{}/{}ms/Expire@{}/{}/{}", new Object[] {
                    status,
                    System.currentTimeMillis() - start,
                    expire > 0 ? DatetimeUtils.format(expire, Datetime.DF_HH_mm_ss_S) : "N/A",
                    location,
                    key
                });
            }
        }

        public Cache<String, CachedElement> getCache() {
            return cache;
        }
    }

    /**
     * 定时加载的Cache容器，内部用定时任务定时刷缓存，只适用于【无参数的方法】
     */
    public class TimerLoadCacheContainer extends CacheContainer {

        /** 最近一次重加载缓存时间 */
        private Datetime updateTime;

        /** 缓存的过期时间 */
        private Datetime expireTime;

        private Object valInCache;

        private AtomicLong hitCount = new AtomicLong();

        public TimerLoadCacheContainer(Object bean, Method method) throws Throwable {
            super(bean, method);

            // 初始化时再检查一下确保是无参数的
            if (method.getParameterCount() != 0) {
                throw new IllegalArgumentException(location + "上的@" + LocalCache.class.getSimpleName() + "方法必须无参数才能使用定时加载机制");
            }

            // 初始化时，先调用一次方法并将结果缓存下来
            Datetime start = DatetimeUtils.now();
            System.err.print("----->预加载方法缓存 " + location + " ");
            valInCache = invoke();

            // 如果注解配置不允许返回null则需要抛异常报出来
            if (null == valInCache && !anno.cacheNull()) {
                throw new IllegalStateException(location + "上的@" + LocalCache.class.getSimpleName() + "方法配置了不允许缓存null，如需缓存null值请设置cacheNull=true");
            }

            // 生成定时任务的cron，为了尽量避免任务集中执行造成系统扛不住，根据location计算出一个固定的时间偏移量，实现错峰执行
            int secOffset = Math.abs(location.hashCode()) % 60;
            int minOffset = Math.abs(location.hashCode()) / 100 % 60;
            String cron;
            switch (anno.timeUnit()) {
            case MINUTES:
                cron = secOffset + " */" + periodMs / Datetime.MILLIS_PER_MINUTE + " * * * *";
                break;
            case HOURS:
                cron = secOffset + " " + minOffset + " */" + periodMs / Datetime.MILLIS_PER_HOUR + " * * *";
                break;
            default:
                cron = "*/" + periodMs / Datetime.MILLIS_PER_SECOND + " * * * * *";
                break;
            }
            // 提交定时任务，并在执行完毕后将结果保存到valInCache
            CronTaskMeta meta = cronTaskProcessor.addCronTask(bean, method, cron, "@" + LocalCache.class.getSimpleName() + "定时加载 " + anno.descr(), BaseJedisLock.class, true,

                // 此处的赋值操作在CronTaskMeta.run方法执行定时任务后执行
                result -> {
                    if (null == result && !anno.cacheNull()) { // 如果注解配置不允许返回null则需要抛异常报出来
                        throw new IllegalStateException(location + "上的@" + LocalCache.class.getSimpleName() + "方法配置了不允许缓存null，如需缓存null值请设置cacheNull=true");
                    }
                    this.valInCache = result;
                    this.updateTime = DatetimeUtils.now();
                }
            );

            // 提交完成后，更新一下时间
            updateTime = DatetimeUtils.now();
            long costMs = System.currentTimeMillis() - start.getTime();
            expireTime = meta.getNextStartTime();
            System.err.println("[" + HumanReadableUtils.timeSpan(costMs) + "]");
            meta.setExecuteInfo(start, costMs);
        }

        public Datetime getUpdateTime() {
            return updateTime;
        }

        public long getHitCount() {
            return hitCount.longValue();
        }

        @Override
        Object get(Object[] methodArgs) throws Throwable {
            try {
                hitCount.incrementAndGet();
                return valInCache;
            } finally {
                log.debug("{}/1ms/Reload@{}/{}", new Object[] {
                    "ReadFromCache",
                    DatetimeUtils.format(expireTime, Datetime.DF_HH_mm_ss_S),
                    location
                });
            }
        }
    }
}
