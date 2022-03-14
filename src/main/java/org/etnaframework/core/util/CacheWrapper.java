package org.etnaframework.core.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.Maps;

/**
 * 带指定过期时间的loaderCache
 * (实际过期时间是指定时间后的10秒内的随机时间戳)
 *
 * @author Daniel
 */
public class CacheWrapper<K, V> {

    /** 封装的guava cache */
    LoadingCache<K, V> cache;

    /** 缓存名 */
    private String name;

    /** 缓存数据量的大小 */
    private long maximumSize;

    /** 数据的过期时间戳 key-> expireTimestamp */
    private Map<K, Long> keyExpireTimestampMap = Maps.newConcurrentMap();

    /** 用于把过期时间戳随机化 */
    private Random random = new Random();

    /** 强制过期时间,单位毫秒 */
    private long expireTimeMS;

    /**
     * @param loader 匿名内部类，未命中的时候触发
     * @param listener 移除监听器
     * @param maximumSize 容量大小设置
     * @param expireTimeMS 默认过期时间，以毫秒为单位
     */
    CacheWrapper(String cacheName, CacheWrapperLoader<K, V> loader, RemovalListener<K, V> listener, long maximumSize, long expireTimeMS) {
        cache = CacheBuilder.newBuilder().recordStats() // 要记录stat
            .maximumSize(maximumSize <= 0 ? 50 : maximumSize) // 容量大小设置，最大容量默认为50
            .removalListener(listener) // 不需要异步执行了,因为本来执行就是异步执行,而且没有自动过期,用户线程读取的时候不会有额外影响
            .build(new CacheLoader<K, V>() {

                @Override
                public V load(K key) throws Exception {
                    keyExpireTimestampMap.put(key, getDecodeExpireTime(getExpireTimeMS()));
                    return loader.load(key);
                }
            });

        this.name = cacheName;
        long initialDelay = 5000L;
        long delay = 5000L;
        setExpireTimeMS(expireTimeMS <= 0 ? 5000L : expireTimeMS);
        this.maximumSize = maximumSize;

        ThreadUtils.getCron().scheduleWithFixedDelay(() -> { // 检查缓存过期的后台进程
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<K, Long>> it = keyExpireTimestampMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<K, Long> e = it.next();
                K key = e.getKey();
                Long val = e.getValue();
                if (val <= now) {
                    // expire
                    cache.invalidate(key); // 强制使某个key过期,如果listen不是在这个线程里执行的话,那listen需要异步执行.
                    it.remove();
                }
            }
        }, initialDelay, delay, TimeUnit.MILLISECONDS);
    }

    public Statics getStatics() {

        CacheStats stats = cache.stats();
        Statics statics = new Statics();
        statics.name = name;
        statics.maxSize = maximumSize;
        statics.size = cache.size();
        statics.hitRate = stats.hitRate();
        statics.hitCount = stats.hitCount();
        statics.missCount = stats.missCount();
        statics.requestCount = stats.requestCount();
        statics.loadCount = stats.loadCount();
        statics.loadSuccessCount = stats.loadSuccessCount();
        statics.missRate = stats.missRate();
        statics.loadExceptionCount = stats.loadExceptionCount();
        statics.loadExceptionRate = stats.loadExceptionRate();
        statics.averageLoadPenalty = stats.averageLoadPenalty();
        statics.totalLoadTime = stats.totalLoadTime();
        statics.evictionCount = stats.evictionCount();
        statics.hitRate = stats.hitRate();
        return statics;
    }

    public void invaldateAll() {
        keyExpireTimestampMap.clear();
        cache.invalidateAll();
    }

    /**
     * 获取缓存值
     */
    public V get(K key) throws ExecutionException {
        return cache.get(key);
    }

    /**
     * 获取缓存值
     */
    public V getIfPresent(K key) throws ExecutionException {
        return cache.getIfPresent(key);
    }

    public void put(K key, V val, long expireMS) {
        cache.put(key, val);
        keyExpireTimestampMap.put(key, getDecodeExpireTime(expireMS));
    }

    public void put(K key, V val) {
        cache.put(key, val);
        keyExpireTimestampMap.put(key, getDecodeExpireTime(expireTimeMS));
    }

    /**
     * 将指定的KEY失效
     */
    public void invalidate(K key) {
        cache.invalidate(key);
        keyExpireTimestampMap.remove(key);
    }

    /**
     * 获取基于指定过期时间后的随机过期时间戳
     */
    private long getDecodeExpireTime(long expireTime) {
        return System.currentTimeMillis() + expireTime + random.nextInt(10000);// demo.
    }

    public long getExpireTimeMS() {
        return expireTimeMS;
    }

    public void setExpireTimeMS(long expireTimeMS) {
        this.expireTimeMS = expireTimeMS;
    }

    /**
     * 获取缓存对象Map,以便外部调用缓存中的所有值
     */
    public ConcurrentMap<K, V> asMap() {
        return cache.asMap();
    }

    public static class Statics {

        /** 缓存名称，也是缓存唯一ID */
        public String name;

        /** 最大的容量 */
        public long maxSize;

        /** 实际容量 */
        public long size;

        /** 命中率 */
        public double hitRate;

        /** 命中次数 */
        public long hitCount;

        /** 未命中的次数 */
        public long missCount;

        /** 缓存请求未命中的比率，未命中次数除以请求次数 */
        public double missRate;

        /** 缓存调用load方法加载新值的次数 */
        public long requestCount;

        /** 缓存加载新值总的次数 */
        public long loadCount;

        /** 缓存加载新值的成功次数 */
        public long loadSuccessCount;

        /** 缓存加载新值出现异常的次数 */
        public long loadExceptionCount;

        /** 缓存加载新值出现异常的比率 */
        public double loadExceptionRate;

        /** 加载新值的耗费的平均时间，加载的次数除以加载的总时间 */
        public double averageLoadPenalty;

        /** 缓存加载新值所耗费的总时间 */
        public long totalLoadTime;

        /** 缓存中条目被移除的次数 */
        public long evictionCount;
    }

    /**
     * 数据加载器
     */
    public static abstract class CacheWrapperLoader<K, V> {

        public abstract V load(K key) throws Exception;
    }
}
