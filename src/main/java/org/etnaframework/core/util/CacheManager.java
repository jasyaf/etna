package org.etnaframework.core.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import org.etnaframework.core.util.CacheWrapper.CacheWrapperLoader;
import org.etnaframework.core.util.CacheWrapper.Statics;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * CacheWrapper 管理类
 * Created by Daniel on 2015/12/2.
 */
public final class CacheManager {

    /** 注册监控列表 */
    private final static ConcurrentMap<String, CacheWrapper> CACHE_POOLS = Maps.newConcurrentMap();

    private CacheManager() {
    }

    /**
     * 创建带指定过期时间的loaderCache
     *
     * @param cacheName 缓存注册名称
     * @param loader 匿名内部类，未命中的时候触发
     * @param listener 移除监听器
     * @param maximumSize 容量大小设置
     * @param expireTimeMS 默认过期时间，以毫秒为单位
     */
    public static <K, V> CacheWrapper<K, V> build(String cacheName, CacheWrapperLoader<K, V> loader, RemovalListener<K, V> listener, long maximumSize, long expireTimeMS) {
        checkCacheExists(cacheName);
        CacheWrapper<K, V> cacheWrapper = new CacheWrapper<>(cacheName, loader, listener, maximumSize, expireTimeMS);
        addCACHE_POOLS(cacheName, cacheWrapper);
        return cacheWrapper;
    }

    /**
     * 把缓存添加到缓存监控池中
     */
    public static void addCACHE_POOLS(String name, CacheWrapper cacheWrapper) {
        CACHE_POOLS.put(name, cacheWrapper);
    }

    /**
     * 检测缓存是否已存在
     */
    private static void checkCacheExists(String name) {
        synchronized (CacheManager.class) {
            if (StringTools.isEmpty(name)) {
                throw new IllegalArgumentException("缓存注册失败，原因是缓存名称为空或者是null");
            }
            if (CACHE_POOLS.get(name) != null) {
                throw new IllegalArgumentException("缓存注册失败，" + name + "已存在");
            }
        }
    }

    /**
     * 获取所有的统计
     */
    public static List<Statics> getAllStats() {
        List<Statics> result = Lists.newLinkedList();
        CACHE_POOLS.values().stream().forEach(c -> result.add(c.getStatics()));
        return result;
    }

    /**
     * 获取缓存打印信息
     */
    public static String printCacheInfo() {
        List<Statics> result = getAllStats();
        int nameMaxLen = 10; // 找出最长的名称，用于显示时对齐数据
        for (Statics s : result) {
            if (s.name.length() > nameMaxLen) {
                nameMaxLen = s.name.length();
            }
        }
        String fmt = "%-" + nameMaxLen + "s %-10s %-10s %10s %10s %-14s %-14s %-14s %-14s %-14s %-14s\n";
        StringBuilder tmp = new StringBuilder();
        tmp.append(String.format(fmt, "name", "maxSize", "size", "hitRate", "missRate", "hit", "miss", "access", "load", "loadSucc", "evictionCount"));
        for (Statics s : result) {
            String line = String.format(fmt, s.name, s.maxSize, s.size, multiplyDouble(s.hitRate, 100, 2), multiplyDouble(s.missRate, 100, 2), s.hitCount, s.missCount, s.requestCount, s.loadCount,
                s.loadSuccessCount, s.evictionCount);
            tmp.append(line);
        }
        return tmp.toString();
    }

    private static String multiplyDouble(double first, double second, int decimals) {
        BigDecimal b1 = new BigDecimal(first);
        BigDecimal b2 = new BigDecimal(second);
        return b1.multiply(b2).setScale(decimals, BigDecimal.ROUND_HALF_UP).toString() + "%";
    }
}
