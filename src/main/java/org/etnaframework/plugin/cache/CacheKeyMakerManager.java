package org.etnaframework.plugin.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.etnaframework.core.spring.SpringContext;
import org.springframework.stereotype.Service;

/**
 * 缓存key的生成规则统一管理类
 *
 * @author BlackCat
 * @since 2018-03-05
 */
@Service
public class CacheKeyMakerManager {

    private static final Map<Class<? extends CacheKeyMaker>, CacheKeyMaker> map = new ConcurrentHashMap<>();

    public CacheKeyMaker get(Class<? extends CacheKeyMaker> clazz) {
        CacheKeyMaker val = map.get(clazz);
        if (null == val) {
            synchronized (map) { // 如果没查到，就生成，进行双次判断，防止重复生成
                if (null == (val = map.get(clazz))) {
                    // 没找到时，先从Spring里找，找不到就直接初始化实例
                    val = SpringContext.getBean(clazz);
                    if (null == val) {
                        try {
                            val = clazz.newInstance();
                        } catch (Throwable ex) {
                            throw new IllegalArgumentException("无法初始化缓存key生成类" + clazz.getName());
                        }
                    }
                    map.put(clazz, val);
                }
            }
        }
        return val;
    }
}
